// ============================================================
// 📁 _handlers.ts — VLESSHUB BOT HANDLERS (webhook mode)
// ============================================================
// Faithful TS port of vlesshub/telegram-bot/bot.py. The Python bot was a
// long-poll process; this runs inside a Supabase edge function in
// WEBHOOK mode, so every Telegram update is a fresh invocation and the
// in-memory selection state is persisted to `vlesshub_bot_state`.
//
// Handles: decorative reply keyboard, multi-select lists (servers /
// VPN files / proxies), scraper-version menus, slash commands and the
// GitHub Actions scrape dispatch.
// ============================================================

import type { SupabaseClient } from 'jsr:@supabase/supabase-js@2';
import * as tg from './_telegram.ts';
import {
  countRows,
  listServers,
  listFiles,
  listProxies,
  getServersByIds,
  getFilesByIds,
  getProxiesByIds,
  bulkDeleteByIds,
  deleteAllServers,
  getChannels,
  addChannel,
  deleteChannel,
  addProxy,
  deleteProxies,
  insertServer,
  checkDuplicate,
  saveVpnFile,
  getContactByUserId,
  createContactRequest,
  setContactStatus,
  setContactMessage,
  setContactAdminReply,
  listContacts,
  countContacts,
  deleteContact,
  getConfig,
  setConfig,
  PAGE_SIZE,
  CONTACT_PAGE_SIZE,
  type ContactRow,
} from './_db.ts';
import { parseFile, extractChannel } from './_parser.ts';
import { getState, saveState, emptyState, type VlesshubChatState } from './_state.ts';

export interface BotContext {
  token: string;
  supabase: SupabaseClient;
  adminIds: Set<number>;
  ghToken: string;
  ghRepo: string;
  ghWorkflow: string;
  downloadUrl: string;
  contactEmail: string;
}

// ─── Button labels (mirror bot.py) ────────────────────────────
const BTN_SERVERS = '🖥 Links';
const BTN_VPN_FILES = '📄 VPN Files';
const BTN_PROXIES = '🌐 Proxies';
const BTN_SCRAPER = '🧪 Scraper';
const BTN_VERSION = '📰 Version';
const BTN_HELP = '❓ Help';
const BTN_MESSAGES = '✉️ Messages';
const BTN_MENU = '🏠 Menu';

/** Cap on user messages and admin replies exchanged via the contact flow. */
const MAX_CONTACT_WORDS = 50;

// ─── Menu texts (mirror bot.py) ───────────────────────────────
const MENU_TEXT =
  'VlessHub — official config publishing channel: @Vless_hub_bot.\n\n' +
  'New VPN configs (VLESS • VMess • Trojan • SS • Hysteria2 • WireGuard • SOCKS) ' +
  'are published here and flow straight into the app.\n\n' +
  '🖥 Links — list & delete servers\n' +
  '📄 VPN Files — browse & download raw config files (.npvt, .sip, .npv, .json, etc.)\n' +
  '🌐 Proxies — MTProto proxy pool\n' +
  '🧪 Scraper — run the config scraper / manage proxies & channels\n' +
  '📰 Version — version management\n' +
  '✉️ Messages — contact requests & user messages\n' +
  '❓ Help — how it works & what each option does';

const HELP_TEXT =
  '*VlessHub bot — menu guide*\n\n' +
  '🖥 *Links*\n' +
  'Tap servers to select them (checkmarks appear). Then tap *Copy* or *Delete* to act on the selection.\n\n' +
  '📄 *VPN Files*\n' +
  'Browse and download raw config files. Select multiple files, then *Download* or *Delete*.\n\n' +
  '🧪 *Scraper*\n' +
  'Manage proxies & channels, run the scraper.\n\n' +
  '📰 *Version*\n' +
  '`/version` show config • `/setmin` • `/setlatest` • `/setbuild` • `/forceupdate`\n\n' +
  '✉️ *Messages*\n' +
  'Approve contact requests and reply to user messages.\n\n' +
  '*Commands*: /start • /stats • /scrape • /myid';

// ─── Reply keyboard (persistent) ──────────────────────────────
function mainKeyboard() {
  return {
    keyboard: [
      [BTN_SERVERS, BTN_VPN_FILES],
      [BTN_PROXIES, BTN_SCRAPER],
      [BTN_VERSION, BTN_HELP],
      [BTN_MESSAGES],
    ],
    resize_keyboard: true,
    input_field_placeholder: 'Choose an option',
  };
}

function inlineMenuButton() {
  return { inline_keyboard: [[{ text: BTN_MENU, callback_data: 'menu' }]] };
}

function urlKeyboard(rows: { text: string; url?: string; callback_data?: string }[][]) {
  return { inline_keyboard: rows };
}

/** Display name for a contact row — username, first name, or the raw id. */
function contactName(username: string | null, firstName: string | null, userId: number): string {
  if (username) return `@${username}`;
  if (firstName) return firstName;
  return String(userId);
}

/** Admin "✉️ Messages" list — users + their latest message, one button row each. */
function buildContactList(contacts: ContactRow[], page: number, total: number) {
  const rows: any[][] = [];
  const lines: string[] = [`✉️ *Messages* (${total})`, ''];
  for (const c of contacts) {
    const name = contactName(c.username, c.first_name, c.user_id);
    const statusLine = c.status === 'approved' ? '✅ approved' : '⏳ awaiting confirm';
    lines.push(`*${name}* — ${statusLine}`);
    if (c.last_message) lines.push(`   💬 ${c.last_message}`);
    rows.push([
      {
        text: c.status === 'approved' ? '↩️ Reply' : '✅ Approve',
        callback_data: `msg:${c.status === 'approved' ? 'reply' : 'approve'}:${c.user_id}:${page}`,
      },
      { text: '🗑 Remove', callback_data: `msg:del:${c.user_id}:${page}` },
    ]);
  }
  const nav: any[] = [];
  if (page > 0) nav.push({ text: '◀ Prev', callback_data: `msg:page:${page - 1}` });
  if ((page + 1) * CONTACT_PAGE_SIZE < total) nav.push({ text: 'Next ▶', callback_data: `msg:page:${page + 1}` });
  if (nav.length) rows.push(nav);
  rows.push([{ text: BTN_MENU, callback_data: 'menu' }]);
  const pages = Math.max(1, Math.ceil(total / CONTACT_PAGE_SIZE));
  return {
    text: lines.join('\n') + `\n\nPage ${page + 1}/${pages}`,
    reply_markup: { inline_keyboard: rows },
  };
}

/** Ping every admin chat (private chats — chat id == user id). */
async function notifyAdmins(ctx: BotContext, text: string): Promise<void> {
  for (const id of ctx.adminIds) {
    try {
      await tg.sendMessage(ctx.token, id, text, { parse_mode: 'Markdown' });
    } catch {
      // admin chat unreachable — ignore
    }
  }
}

// ─── Multi-select list builders ───────────────────────────────
function buildServerList(servers: any[], selected: Set<number>, page: number, total: number) {
  const rows: any[][] = [];
  for (const s of servers) {
    const mark = selected.has(s.id) ? '☑' : '☐';
    const label = `${mark} ${s.flag ?? ''} ${s.name ?? '?'}`;
    rows.push([{ text: label, callback_data: `srv:toggle:${s.id}:${page}` }]);
  }
  const selCount = selected.size;
  const nav: any[] = [];
  if (page > 0) nav.push({ text: '◀ Prev', callback_data: `srv:page:${page - 1}` });
  if ((page + 1) * PAGE_SIZE < total) nav.push({ text: 'Next ▶', callback_data: `srv:page:${page + 1}` });
  if (nav.length) rows.push(nav);
  if (selCount > 0) {
    rows.push([
      { text: `📋 Copy (${selCount})`, callback_data: 'srv:copy' },
      { text: `🗑 Delete (${selCount})`, callback_data: 'srv:delete' },
    ]);
    rows.push([
      { text: '☑ Select all', callback_data: `srv:selectall:${page}` },
      { text: '☐ Deselect all', callback_data: `srv:deselectall:${page}` },
    ]);
  } else {
    rows.push([
      { text: '☑ Select all', callback_data: `srv:selectall:${page}` },
      { text: BTN_MENU, callback_data: 'menu' },
    ]);
  }
  const pageTotal = Math.min(PAGE_SIZE, total - page * PAGE_SIZE);
  const pages = Math.ceil(total / PAGE_SIZE);
  let text = `🖥 Links (${total})\nPage ${page + 1}/${pages} • ${pageTotal} items`;
  if (selCount > 0) text += `\n✅ ${selCount} selected`;
  return { text, reply_markup: { inline_keyboard: rows } };
}

function buildFileList(files: any[], selected: Set<number>, page: number, total: number) {
  const rows: any[][] = [];
  for (const f of files) {
    const mark = selected.has(f.id) ? '☑' : '☐';
    const sizeKb = Math.round((f.size_bytes || 0) / 1024);
    const enc = f.is_encrypted ? '🔒' : '';
    rows.push([{ text: `${mark} ${enc}${f.filename} (${sizeKb}KB)`, callback_data: `file:toggle:${f.id}:${page}` }]);
  }
  const selCount = selected.size;
  const nav: any[] = [];
  if (page > 0) nav.push({ text: '◀ Prev', callback_data: `file:page:${page - 1}` });
  if ((page + 1) * PAGE_SIZE < total) nav.push({ text: 'Next ▶', callback_data: `file:page:${page + 1}` });
  if (nav.length) rows.push(nav);
  if (selCount > 0) {
    rows.push([
      { text: `⬇️ Download (${selCount})`, callback_data: 'file:download' },
      { text: `🗑 Delete (${selCount})`, callback_data: 'file:delete' },
    ]);
    rows.push([
      { text: '☑ Select all', callback_data: `file:selectall:${page}` },
      { text: '☐ Deselect all', callback_data: `file:deselectall:${page}` },
    ]);
  } else {
    rows.push([
      { text: '☑ Select all', callback_data: `file:selectall:${page}` },
      { text: BTN_MENU, callback_data: 'menu' },
    ]);
  }
  const pageTotal = Math.min(PAGE_SIZE, total - page * PAGE_SIZE);
  const pages = Math.ceil(total / PAGE_SIZE);
  let text = `📄 VPN Files (${total})\nPage ${page + 1}/${pages} • ${pageTotal} items`;
  if (selCount > 0) text += `\n✅ ${selCount} selected`;
  return { text, reply_markup: { inline_keyboard: rows } };
}

function buildProxyList(proxies: any[], selected: Set<number>) {
  const rows: any[][] = [];
  for (const p of proxies) {
    const mark = selected.has(p.id) ? '☑' : '☐';
    const status = p.last_ok && p.is_active ? '✅' : '❌';
    rows.push([{ text: `${mark} ${status} ${p.host}:${p.port}`, callback_data: `proxy:toggle:${p.id}` }]);
  }
  const selCount = selected.size;
  if (selCount > 0) {
    rows.push([
      { text: `📋 Copy (${selCount})`, callback_data: 'proxy:copy' },
      { text: `🗑 Delete (${selCount})`, callback_data: 'proxy:delete' },
    ]);
    rows.push([
      { text: '☑ Select all', callback_data: 'proxy:selectall' },
      { text: '☐ Deselect all', callback_data: 'proxy:deselectall' },
    ]);
  } else {
    rows.push([
      { text: '☑ Select all', callback_data: 'proxy:selectall' },
      { text: BTN_MENU, callback_data: 'menu' },
    ]);
  }
  let text = `🌐 Proxies (${proxies.length})`;
  if (selCount > 0) text += `\n✅ ${selCount} selected`;
  return { text, reply_markup: { inline_keyboard: rows } };
}

// ─── Scraper / Version menus ──────────────────────────────────
async function sendScraperMenu(ctx: BotContext, chatId: number) {
  const channels = await getChannels(ctx.supabase);
  const chList = channels.length
    ? channels.map((c) => `  • @${c}`).join('\n')
    : '  (none)';
  const text = `*🧪 Scraper control*\n\nThe scraper pulls new VPN configs from the channels below.\n\n*Channels (${channels.length})*\n${chList}`;
  const kb = urlKeyboard([
    [
      { text: '➕ Add proxy', callback_data: 'scraper:addproxy' },
      { text: '🗑 Remove proxy', callback_data: 'scraper:delproxy' },
    ],
    [{ text: '📋 Proxy pool', callback_data: 'scraper:listproxy' }],
    [
      { text: '➕ Add channel', callback_data: 'scraper:addchannel' },
      { text: '🗑 Remove channel', callback_data: 'scraper:delchannel' },
    ],
    [{ text: '📋 Channels', callback_data: 'scraper:listchannel' }],
    [
      { text: '▶️ Run scrape', callback_data: 'scraper:scrape' },
      { text: '⏱ Auto scrape', callback_data: 'scraper:schedule' },
    ],
    [{ text: BTN_MENU, callback_data: 'menu' }],
  ]);
  await tg.sendMessage(ctx.token, chatId, text, { parse_mode: 'Markdown', reply_markup: kb });
}

async function sendVersionMenu(ctx: BotContext, chatId: number) {
  const text =
    '*📰 Version management*\n\n' +
    '`/version` show config\n' +
    '`/setmin X.Y.Z` set minimum\n' +
    '`/setlatest X.Y.Z` set latest\n' +
    '`/setbuild N` set build number\n' +
    '`/forceupdate on|off` toggle force';
  const kb = urlKeyboard([
    [{ text: '📰 Show config', callback_data: 'version:show' }],
    [
      { text: '⬇️ Set min', callback_data: 'version:setmin' },
      { text: '⬆️ Set latest', callback_data: 'version:setlatest' },
    ],
    [
      { text: '🔢 Set build', callback_data: 'version:setbuild' },
      { text: '🔄 Force update', callback_data: 'version:forceupdate' },
    ],
    [{ text: BTN_MENU, callback_data: 'menu' }],
  ]);
  await tg.sendMessage(ctx.token, chatId, text, { parse_mode: 'Markdown', reply_markup: kb });
}

// ─── Auto-scrape scheduler (glass ⏱ button) ─────────────────
// Schedule lives in scraper_config:
//   scrape_schedule_enabled  = "true" | "false"
//   scrape_schedule_hours    = "6" | "12" | "24"
// An hourly GitHub Actions cron tick reads the same keys and runs the
// scraper when the interval has elapsed.
const SCHEDULE_ENABLED_KEY = 'scrape_schedule_enabled';
const SCHEDULE_HOURS_KEY = 'scrape_schedule_hours';
const SCHEDULE_LAST_KEY = 'scrape_schedule_last';
const ALLOWED_INTERVALS = [6, 12, 24];

async function getSchedule(supabase: any): Promise<{ enabled: boolean; hours: number; last: string | null }> {
  const [enabledRaw, hoursRaw, last] = await Promise.all([
    getConfig(supabase, SCHEDULE_ENABLED_KEY),
    getConfig(supabase, SCHEDULE_HOURS_KEY),
    getConfig(supabase, SCHEDULE_LAST_KEY),
  ]);
  const hours = Number(hoursRaw);
  return {
    enabled: enabledRaw === 'true',
    hours: ALLOWED_INTERVALS.includes(hours) ? hours : 12,
    last,
  };
}

function scheduleText(s: { enabled: boolean; hours: number; last: string | null }): string {
  const state = s.enabled ? '🟢 *Running*' : '🔴 *Stopped*';
  let lastLine = 'Never fired yet';
  if (s.last) {
    const t = new Date(s.last);
    if (!isNaN(t.getTime())) {
      const mins = Math.round((Date.now() - t.getTime()) / 60000);
      lastLine = mins < 1 ? 'Just now' : mins < 60 ? `${mins} min ago` : `${Math.round(mins / 60)} h ago`;
    }
  }
  const next = s.enabled
    ? `Next auto-run: within ${s.hours}h of the last one (checked hourly)`
    : 'Start it to enable automatic runs.';
  return `*⏱ Auto-scrape scheduler*\n\nStatus: ${state}\nInterval: every *${s.hours} hours*\nLast auto-run: ${lastLine}\n\n${next}\n\nGitHub limits: runs cost ~2 min each; even 6h is ~240 min/month — well inside the free tier. Do not go below 6h.`;
}

function scheduleKeyboard(s: { enabled: boolean; hours: number }): any {
  const intervalBtn = (h: number) => ({
    text: (s.hours === h ? '● ' : '') + `${h}h`,
    callback_data: `scraper:schedset:${h}`,
  });
  return urlKeyboard([
    [
      intervalBtn(6),
      intervalBtn(12),
      intervalBtn(24),
    ],
    [s.enabled
      ? { text: '⏸ Stop auto-scrape', callback_data: 'scraper:schedstop' }
      : { text: '▶️ Start auto-scrape', callback_data: 'scraper:schedstart' }],
    [{ text: BTN_MENU, callback_data: 'menu' }],
  ]);
}

// ─── GitHub Actions scrape dispatch (mirror bot.py) ───────────
async function dispatchScrape(ctx: BotContext, chatId: number): Promise<{ ok: boolean; error?: string; status?: number }> {
  if (!ctx.ghToken || !ctx.ghRepo) return { ok: false, error: 'GH_PAT or GH_REPO not configured' };
  const url = `https://api.github.com/repos/${ctx.ghRepo}/actions/workflows/${ctx.ghWorkflow}/dispatches`;
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${ctx.ghToken}`,
        Accept: 'application/vnd.github+json',
        'Content-Type': 'application/json',
      },
      // The scraper lives in the dedicated `ahmad43ir/VlessHub` repo (branch
      // `main`); GH_REF can override this without a function-code change.
      // Passing the chat_id lets the workflow's report step send the run
      // summary (channels / messages / links found) back to this chat.
      body: JSON.stringify({
        ref: Deno.env.get('GH_REF') ?? 'main',
        inputs: { chat_id: String(chatId) },
      }),
      signal: AbortSignal.timeout(30_000),
    });
    if (res.status === 204) return { ok: true };
    let body = '';
    try { body = await res.text(); } catch { /* ignore */ }
    console.error(`[dispatchScrape] GitHub API returned ${res.status}: ${body.slice(0, 500)}`);
    return { ok: false, status: res.status, error: body.slice(0, 200) };
  } catch (e) {
    console.error(`[dispatchScrape] fetch failed: ${(e as Error).message}`);
    return { ok: false, error: (e as Error).message };
  }
}

// ─── Pending-input ingestion (proxy / channel) ────────────────
function parseProxyLink(text: string): { host: string; port: number; secret: string | null } | null {
  // 1. Query-string format: tg://proxy?server=..&port=..&secret=..
  //    or https://t.me/proxy?server=..&port=..&secret=..
  const m = text.match(/server=([^&]+)/);
  const p = text.match(/port=(\d+)/);
  if (m && p) {
    return {
      host: decodeURIComponent(m[1]),
      port: Number(p[1]),
      secret: (() => {
        const s = text.match(/secret=([^&\s]+)/);
        return s ? decodeURIComponent(s[1]) : null;
      })(),
    };
  }

  // 2. mtproto://secret@host:port
  const mt = text.match(/mtproto:\/\/([^\s@/]+)@([^\s/:]+):(\d+)/i);
  if (mt) {
    return { host: mt[2], port: Number(mt[3]), secret: mt[1] };
  }

  // 3. Bare host:port:secret (e.g. 1.2.3.4:8080:dd...)
  const bare = text.match(/\b((?:\d{1,3}\.){3}\d{1,3}|[a-zA-Z0-9][a-zA-Z0-9._-]*):(\d{2,5}):([A-Za-z0-9+/=]+)\b/);
  if (bare) {
    return { host: bare[1], port: Number(bare[2]), secret: bare[3] };
  }

  return null;
}

/** Extract ALL MTProto proxy links from a text block. */
function extractProxyLinks(text: string): { host: string; port: number; secret: string | null }[] {
  if (!text) return [];
  const seen = new Set<string>();
  const result: { host: string; port: number; secret: string | null }[] = [];

  // Split on whitespace/newlines to handle multiple links
  const chunks = text.split(/[\s,;]+/);
  for (const chunk of chunks) {
    const proxy = parseProxyLink(chunk);
    if (proxy) {
      const key = `${proxy.host.toLowerCase()}:${proxy.port}`;
      if (!seen.has(key)) {
        seen.add(key);
        result.push(proxy);
      }
    }
  }
  return result;
}

async function handlePendingInput(ctx: BotContext, state: VlesshubChatState, chatId: number, text: string): Promise<boolean> {
  const pending = state.pending;
  if (!pending) return false;

  if (pending === 'addproxy') {
    const proxy = parseProxyLink(text);
    if (!proxy) {
      await tg.sendMessage(ctx.token, chatId, '❌ Could not parse that. Send a proxy link like:\n`https://t.me/proxy?server=..&port=..&secret=..`', {
        parse_mode: 'Markdown',
        reply_markup: mainKeyboard(),
      });
      return true;
    }
    const ok = await addProxy(ctx.supabase, proxy.host, proxy.port, proxy.secret);
    state.pending = null;
    await saveState(ctx.supabase, state);
    await tg.sendMessage(ctx.token, chatId, ok ? `✅ Proxy added: ${proxy.host}:${proxy.port}` : '❌ Failed to add proxy.', {
      reply_markup: mainKeyboard(),
    });
    return true;
  }

  if (pending === 'delproxy') {
    const deleted = await deleteProxies(ctx.supabase, text);
    state.pending = null;
    await saveState(ctx.supabase, state);
    const msg = deleted === null
      ? '❌ Could not delete proxy.'
      : deleted === -1
        ? '🗑 Deleted all proxies.'
        : `🗑 Deleted ${deleted} proxy/proxies.`;
    await tg.sendMessage(ctx.token, chatId, msg, { reply_markup: mainKeyboard() });
    return true;
  }

  if (pending === 'addchannel') {
    const ok = await addChannel(ctx.supabase, text);
    state.pending = null;
    await saveState(ctx.supabase, state);
    await tg.sendMessage(ctx.token, chatId, ok ? `✅ Channel added: @${text.trim().replace(/^@/, '')}` : '❌ Could not add channel.', {
      reply_markup: mainKeyboard(),
    });
    return true;
  }

  if (pending === 'delchannel') {
    const ok = await deleteChannel(ctx.supabase, text);
    state.pending = null;
    await saveState(ctx.supabase, state);
    await tg.sendMessage(ctx.token, chatId, ok ? `🗑 Channel removed: @${text.trim().replace(/^@/, '')}` : '❌ Could not remove channel.', {
      reply_markup: mainKeyboard(),
    });
    return true;
  }

  if (pending?.startsWith('msg_reply:')) {
    const userId = Number(pending.split(':')[1]);
    state.pending = null;
    await saveState(ctx.supabase, state);
    if (!Number.isInteger(userId) || userId <= 0) {
      await tg.sendMessage(ctx.token, chatId, '❌ Could not find that user.', { reply_markup: mainKeyboard() });
      return true;
    }
    if (text.trim().split(/\s+/).length > MAX_CONTACT_WORDS) {
      await tg.sendMessage(ctx.token, chatId, `✂️ Keep the reply under ${MAX_CONTACT_WORDS} words.`, { reply_markup: mainKeyboard() });
      return true;
    }
    const ok = await setContactAdminReply(ctx.supabase, userId, text);
    if (ok) {
      await tg.sendMessage(ctx.token, userId, `📨 *Admin reply:* ${text}`, { parse_mode: 'Markdown' });
      await tg.sendMessage(ctx.token, chatId, '✅ Reply sent to the user.', { reply_markup: mainKeyboard() });
    } else {
      await tg.sendMessage(ctx.token, chatId, '❌ Could not send the reply.', { reply_markup: mainKeyboard() });
    }
    return true;
  }

  return false;
}

// ─── Message handler (reply keyboard buttons + pending input) ─
async function handleMessage(ctx: BotContext, msg: any): Promise<void> {
  const chatId = msg.chat?.id;
  const uid = msg.from?.id;
  const text = (msg.text ?? '') as string;
  if (chatId == null || uid == null) return;

  const isAdmin = ctx.adminIds.size === 0 || ctx.adminIds.has(uid);
  if (!isAdmin) {
    // Contact flow: approved users can message the admin directly; everyone
    // else just gets the download prompt with a Contact Us request button.
    if (text && !text.startsWith('/')) {
      const contact = await getContactByUserId(ctx.supabase, uid);
      if (contact && contact.status === 'approved') {
        if (text.trim().split(/\s+/).length > MAX_CONTACT_WORDS) {
          await tg.sendMessage(ctx.token, chatId, `✂️ Keep it under ${MAX_CONTACT_WORDS} words.`, {
            reply_markup: urlKeyboard([[{ text: '✉️ Contact Us', callback_data: 'contact:start' }]]),
          });
          return;
        }
        await setContactMessage(ctx.supabase, uid, text);
        const who = contactName(contact.username, contact.first_name, uid);
        await notifyAdmins(ctx, `📩 *New message from ${who}:*\n${text}`);
        await tg.sendMessage(ctx.token, chatId, '✅ Message sent to the admin — we\'ll reply here.', {
          reply_markup: urlKeyboard([[{ text: '✉️ Contact Us', callback_data: 'contact:start' }]]),
        });
        return;
      }
      if (contact && contact.status === 'requested') {
        await tg.sendMessage(ctx.token, chatId, '⏳ Your contact request is waiting for admin confirmation — you\'ll be notified here.', {
          reply_markup: urlKeyboard([[{ text: '✉️ Contact Us', callback_data: 'contact:start' }]]),
        });
        return;
      }
    }
    await tg.sendMessage(ctx.token, chatId, '📥 Download the app to browse free VLESS configs:', {
      reply_markup: urlKeyboard([
        [{ text: '📥 Download App', url: ctx.downloadUrl }],
        [{ text: '✉️ Contact Us', callback_data: 'contact:start' }],
      ]),
    });
    return;
  }

  const state = await getState(ctx.supabase, chatId);

  if (await handlePendingInput(ctx, state, chatId, text)) return;

  if (text === BTN_SERVERS) {
    state.selected.servers = [];
    state.pending = null;
    await saveState(ctx.supabase, state);
    const total = await countRows(ctx.supabase, 'servers');
    const servers = await listServers(ctx.supabase, 0);
    if (!servers.length) {
      await tg.sendMessage(ctx.token, chatId, 'No servers in the database.', { reply_markup: mainKeyboard() });
      return;
    }
    const view = buildServerList(servers, new Set(state.selected.servers), 0, total);
    await tg.sendMessage(ctx.token, chatId, view.text, { reply_markup: view.reply_markup });
  } else if (text === BTN_VPN_FILES) {
    state.selected.files = [];
    state.pending = null;
    await saveState(ctx.supabase, state);
    const total = await countRows(ctx.supabase, 'vpn_files');
    const files = await listFiles(ctx.supabase, 0);
    if (!files.length) {
      await tg.sendMessage(ctx.token, chatId, 'No files found.', { reply_markup: mainKeyboard() });
      return;
    }
    const view = buildFileList(files, new Set(state.selected.files), 0, total);
    await tg.sendMessage(ctx.token, chatId, view.text, { reply_markup: view.reply_markup });
  } else if (text === BTN_PROXIES) {
    state.selected.proxies = [];
    state.pending = null;
    await saveState(ctx.supabase, state);
    const proxies = await listProxies(ctx.supabase);
    if (!proxies.length) {
      await tg.sendMessage(ctx.token, chatId, 'No proxies in the pool.', { reply_markup: mainKeyboard() });
      return;
    }
    const view = buildProxyList(proxies, new Set(state.selected.proxies));
    await tg.sendMessage(ctx.token, chatId, view.text, { reply_markup: view.reply_markup });
  } else if (text === BTN_SCRAPER) {
    state.pending = null;
    await saveState(ctx.supabase, state);
    await sendScraperMenu(ctx, chatId);
  } else if (text === BTN_VERSION) {
    state.pending = null;
    await saveState(ctx.supabase, state);
    await sendVersionMenu(ctx, chatId);
  } else if (text === BTN_HELP) {
    await tg.sendMessage(ctx.token, chatId, HELP_TEXT, { parse_mode: 'Markdown', reply_markup: mainKeyboard() });
  } else if (text === BTN_MESSAGES) {
    state.pending = null;
    await saveState(ctx.supabase, state);
    const total = await countContacts(ctx.supabase);
    const contacts = await listContacts(ctx.supabase, 0);
    if (!contacts.length) {
      await tg.sendMessage(ctx.token, chatId, '✉️ No contact requests yet.', { reply_markup: mainKeyboard() });
      return;
    }
    const view = buildContactList(contacts, 0, total);
    await tg.sendMessage(ctx.token, chatId, view.text, { parse_mode: 'Markdown', reply_markup: view.reply_markup });
  } else {
    // ── Config insertion: pasted text or document ──
    await handleConfigInsertion(ctx, msg, chatId, uid, text);
  }
}

// ─── Config insertion (pasted text / file upload) ───────────
const FILE_UPLOAD_EXTENSIONS = ['.npv', '.npvt', '.npt', '.json', '.sip', '.conf', '.config', '.ovpn', '.txt'];

function bytesToBase64(bytes: Uint8Array): string {
  let binary = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

function channelFromForward(message: any): string | null {
  const chat = message.forward_origin?.chat ?? message.forward_from_chat ?? null;
  if (!chat || chat.type !== 'channel') return null;
  const username = chat.username;
  if (typeof username === 'string' && username) return `@${username}`;
  return null;
}

async function handleConfigInsertion(
  ctx: BotContext,
  msg: any,
  chatId: number,
  uid: number,
  text: string,
): Promise<void> {
  let content: string;
  let source: string;
  let vpnFileNote: string | null = null;

  if (msg.document) {
    const filePath = await tg.getFile(ctx.token, msg.document.file_id);
    if (!filePath) {
      await tg.sendMessage(ctx.token, chatId, 'Could not read the file.');
      return;
    }
    const fileName = msg.document.file_name ?? 'document';
    try {
      content = await tg.downloadFileText(ctx.token, filePath);
      source = fileName;
    } catch {
      await tg.sendMessage(ctx.token, chatId, 'Could not read the file.');
      return;
    }
    // Save raw file to vpn_files too
    if (FILE_UPLOAD_EXTENSIONS.some((ext) => fileName.toLowerCase().endsWith(ext))) {
      try {
        const bytes = await tg.downloadFileBytes(ctx.token, filePath);
        const saved = await saveVpnFile(ctx.supabase, {
          filename: fileName,
          mime_type: msg.document.mime_type ?? null,
          size_bytes: bytes.length,
          contentBase64: bytesToBase64(bytes),
          uploaded_by: uid,
        });
        vpnFileNote = saved.saved
          ? `📁 Raw file saved to VPN Files (${(bytes.length / 1024).toFixed(1)} KB).`
          : saved.duplicate
            ? '📁 File already exists in VPN Files — skipped.'
            : '⚠️ Could not save the raw file to VPN Files.';
      } catch {
        vpnFileNote = '⚠️ Could not save the raw file to VPN Files.';
      }
    }
  } else if (text) {
    content = text;
    source = 'text';
  } else {
    return;
  }

  if (!content || !content.trim()) {
    await tg.sendMessage(ctx.token, chatId, 'No readable content. Send a file or paste text.');
    return;
  }

  // ── Auto-detect MTProto proxy links ──
  // If the message contains tg://proxy, t.me/proxy, mtproto://, or
  // host:port:secret patterns, store them in scraper_proxies instead
  // of trying to parse as VPN configs.
  const proxies = extractProxyLinks(content);
  if (proxies.length > 0 && parseFile(content).length === 0) {
    let added = 0;
    let duplicates = 0;
    for (const p of proxies) {
      const ok = await addProxy(ctx.supabase, p.host, p.port, p.secret, 'bot');
      if (ok) added++; else duplicates++;
    }
    const summary = proxies.length === 1
      ? `\u2705 Proxy added: ${proxies[0].host}:${proxies[0].port}`
      : `\u2705 ${added} proxy(ies) added to scraper pool` + (duplicates > 0 ? ` (${duplicates} already existed)` : '');
    await tg.sendMessage(ctx.token, chatId, summary, { reply_markup: mainKeyboard() });
    return;
  }

  const parsed = parseFile(content);
  if (parsed.length === 0) {
    await tg.sendMessage(
      ctx.token, chatId,
      'No VPN configs found. Supported: VLESS/VMess/Trojan/SS/WireGuard URIs, NPV JSON, SIP.' +
        (vpnFileNote ? `\n\n${vpnFileNote}` : ''),
      { reply_markup: mainKeyboard() },
    );
    return;
  }

  const batchChannel = channelFromForward(msg) ?? extractChannel(content);
  const channelLabel = batchChannel ? ` from ${batchChannel}` : '';
  await tg.sendMessage(ctx.token, chatId, `Parsed ${parsed.length} config(s)${channelLabel}. Uploading...`);

  let imported = 0;
  let duplicates = 0;
  let invalid = 0;
  for (const entry of parsed) {
    if (await checkDuplicate(ctx.supabase, entry.config)) {
      duplicates++;
      continue;
    }
    const channel = extractChannel(entry.config) ?? batchChannel ?? 'Untitled';
    const name = `${channel} ${imported + 1}`;
    if (await insertServer(ctx.supabase, entry, name)) imported++;
    else invalid++;
    await new Promise((r) => setTimeout(r, 150));
  }

  const total = await countRows(ctx.supabase, 'servers');
  const summary = [
    `*${parsed.length} config(s) received*`,
    `✅ Imported: ${imported}`,
    `↔ Duplicates: ${duplicates}`,
    `⚠ Failed: ${invalid}`,
    total > 0 ? `📊 Total servers: ${total}` : '',
  ].filter(Boolean).join('\n');

  await tg.sendMessage(
    ctx.token, chatId,
    vpnFileNote ? `${summary}\n\n${vpnFileNote}` : summary,
    { parse_mode: 'Markdown', reply_markup: mainKeyboard() },
  );
}

// ─── Callback handler ─────────────────────────────────────────
async function handleCallback(ctx: BotContext, query: any): Promise<void> {
  const data = query.data as string;
  const uid = query.from?.id;
  const chatId = query.message?.chat?.id ?? 0;
  const messageId = query.message?.message_id;
  if (!chatId) return;

  // contact:start is the one callback anyone (non-admin users) may use.
  if (ctx.adminIds.size > 0 && !ctx.adminIds.has(uid) && data !== 'contact:start') {
    await tg.answerCallbackQuery(ctx.token, query.id, 'Admin only.');
    return;
  }

  const state = await getState(ctx.supabase, chatId);

  const edit = (text: string, opts: any = {}) =>
    tg.editMessageText(ctx.token, chatId, messageId, text, opts);

  if (data === 'menu') {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const s = emptyState(chatId);
    await saveState(ctx.supabase, s);
    const serversN = await countRows(ctx.supabase, 'servers');
    const filesN = await countRows(ctx.supabase, 'vpn_files');
    const channels = await getChannels(ctx.supabase);
    const text = `🌐 *VlessHub Bot*\n\n🖥 Links: ${serversN}\n📄 Files: ${filesN}\n🧪 Channels: ${channels.length}`;
    await edit(text, { parse_mode: 'Markdown', reply_markup: inlineMenuButton() });
    return;
  }

  if (data === 'noop') {
    await tg.answerCallbackQuery(ctx.token, query.id);
    return;
  }

  // ── Servers ──
  if (data.startsWith('srv:toggle:')) {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const [, , sidRaw, pageRaw] = data.split(':');
    const sid = Number(sidRaw);
    const page = Number(pageRaw);
    const sel = new Set(state.selected.servers);
    if (sel.has(sid)) sel.delete(sid);
    else sel.add(sid);
    state.selected.servers = [...sel];
    await saveState(ctx.supabase, state);
    const total = await countRows(ctx.supabase, 'servers');
    const servers = await listServers(ctx.supabase, page);
    const view = buildServerList(servers, sel, page, total);
    await edit(view.text, { reply_markup: view.reply_markup });
    return;
  }

  if (data.startsWith('srv:selectall:')) {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const page = Number(data.split(':')[2]);
    const servers = await listServers(ctx.supabase, page, 'id');
    state.selected.servers = servers.map((s) => s.id);
    await saveState(ctx.supabase, state);
    const total = await countRows(ctx.supabase, 'servers');
    const full = await listServers(ctx.supabase, page);
    const view = buildServerList(full, new Set(state.selected.servers), page, total);
    await edit(view.text, { reply_markup: view.reply_markup });
    return;
  }

  if (data.startsWith('srv:deselectall:')) {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const page = Number(data.split(':')[2]);
    state.selected.servers = [];
    await saveState(ctx.supabase, state);
    const total = await countRows(ctx.supabase, 'servers');
    const servers = await listServers(ctx.supabase, page);
    const view = buildServerList(servers, new Set(), page, total);
    await edit(view.text, { reply_markup: view.reply_markup });
    return;
  }

  if (data.startsWith('srv:page:')) {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const page = Number(data.split(':')[2]);
    state.selected.servers = [];
    await saveState(ctx.supabase, state);
    const total = await countRows(ctx.supabase, 'servers');
    const servers = await listServers(ctx.supabase, page);
    const view = buildServerList(servers, new Set(), page, total);
    await edit(view.text, { reply_markup: view.reply_markup });
    return;
  }

  if (data === 'srv:copy') {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const ids = state.selected.servers;
    if (!ids.length) return;
    const servers = await getServersByIds(ctx.supabase, ids);
    const configs = servers.map((s) => s.config).filter(Boolean);
    if (configs.length) {
      const text = configs.map((c) => `<code>${c}</code>`).join('\n\n');
      await tg.sendMessage(ctx.token, chatId, text, { parse_mode: 'HTML' });
    }
    await tg.answerCallbackQuery(ctx.token, query.id, `Copied ${configs.length} config(s)!`);
    state.selected.servers = [];
    await saveState(ctx.supabase, state);
    return;
  }

  if (data === 'srv:delete') {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const ids = state.selected.servers;
    if (!ids.length) return;
    const deleted = await bulkDeleteByIds(ctx.supabase, 'servers', ids);
    await edit(`✅ Deleted ${deleted} server(s).`, { reply_markup: inlineMenuButton() });
    state.selected.servers = [];
    await saveState(ctx.supabase, state);
    return;
  }

  if (data === 'srv:delall') {
    await tg.answerCallbackQuery(ctx.token, query.id);
    await edit('⚠️ Delete ALL servers? This cannot be undone.', {
      reply_markup: urlKeyboard([
        [{ text: '✅ Yes, delete all', callback_data: 'srv:confirm_delall' }],
        [{ text: '❌ Cancel', callback_data: 'menu' }],
      ]),
    });
    return;
  }

  if (data === 'srv:confirm_delall') {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const ok = await deleteAllServers(ctx.supabase);
    await edit(ok ? '✅ All servers deleted.' : '❌ Failed.', { reply_markup: inlineMenuButton() });
    return;
  }

  // ── Files ──
  if (data.startsWith('file:toggle:')) {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const [, , fidRaw, pageRaw] = data.split(':');
    const fid = Number(fidRaw);
    const page = Number(pageRaw);
    const sel = new Set(state.selected.files);
    if (sel.has(fid)) sel.delete(fid);
    else sel.add(fid);
    state.selected.files = [...sel];
    await saveState(ctx.supabase, state);
    const total = await countRows(ctx.supabase, 'vpn_files');
    const files = await listFiles(ctx.supabase, page);
    const view = buildFileList(files, sel, page, total);
    await edit(view.text, { reply_markup: view.reply_markup });
    return;
  }

  if (data.startsWith('file:selectall:')) {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const page = Number(data.split(':')[2]);
    const files = await listFiles(ctx.supabase, page, 'id');
    state.selected.files = files.map((f) => f.id);
    await saveState(ctx.supabase, state);
    const total = await countRows(ctx.supabase, 'vpn_files');
    const full = await listFiles(ctx.supabase, page);
    const view = buildFileList(full, new Set(state.selected.files), page, total);
    await edit(view.text, { reply_markup: view.reply_markup });
    return;
  }

  if (data.startsWith('file:deselectall:')) {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const page = Number(data.split(':')[2]);
    state.selected.files = [];
    await saveState(ctx.supabase, state);
    const total = await countRows(ctx.supabase, 'vpn_files');
    const files = await listFiles(ctx.supabase, page);
    const view = buildFileList(files, new Set(), page, total);
    await edit(view.text, { reply_markup: view.reply_markup });
    return;
  }

  if (data.startsWith('file:page:')) {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const page = Number(data.split(':')[2]);
    state.selected.files = [];
    await saveState(ctx.supabase, state);
    const total = await countRows(ctx.supabase, 'vpn_files');
    const files = await listFiles(ctx.supabase, page);
    const view = buildFileList(files, new Set(), page, total);
    await edit(view.text, { reply_markup: view.reply_markup });
    return;
  }

  if (data === 'file:download') {
    await tg.answerCallbackQuery(ctx.token, query.id, 'Preparing downloads...');
    const ids = state.selected.files;
    if (!ids.length) return;
    const files = await getFilesByIds(ctx.supabase, ids);
    let sent = 0;
    for (const f of files) {
      if (f.content) {
        try {
          const bytes = atob(f.content);
          const ok = await tg.sendDocument(ctx.token, chatId, f.filename ?? 'config.npv', bytes);
          if (ok) sent++;
        } catch {
          // skip corrupt file content
        }
      }
    }
    await tg.answerCallbackQuery(ctx.token, query.id, `Downloaded ${sent} file(s)!`);
    state.selected.files = [];
    await saveState(ctx.supabase, state);
    return;
  }

  if (data === 'file:delete') {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const ids = state.selected.files;
    if (!ids.length) return;
    const deleted = await bulkDeleteByIds(ctx.supabase, 'vpn_files', ids);
    await edit(`✅ Deleted ${deleted} file(s).`, { reply_markup: inlineMenuButton() });
    state.selected.files = [];
    await saveState(ctx.supabase, state);
    return;
  }

  // ── Proxies ──
  if (data.startsWith('proxy:toggle:')) {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const pid = Number(data.split(':')[2]);
    const sel = new Set(state.selected.proxies);
    if (sel.has(pid)) sel.delete(pid);
    else sel.add(pid);
    state.selected.proxies = [...sel];
    await saveState(ctx.supabase, state);
    const proxies = await listProxies(ctx.supabase);
    const view = buildProxyList(proxies, sel);
    await edit(view.text, { reply_markup: view.reply_markup });
    return;
  }

  if (data === 'proxy:selectall') {
    await tg.answerCallbackQuery(ctx.token, query.id);
    // One fetch instead of two — ids come from the same full row set.
    const proxies = await listProxies(ctx.supabase);
    const sel = new Set(proxies.map((p) => p.id));
    state.selected.proxies = [...sel];
    await saveState(ctx.supabase, state);
    const view = buildProxyList(proxies, sel);
    await edit(view.text, { reply_markup: view.reply_markup });
    return;
  }

  if (data === 'proxy:deselectall') {
    await tg.answerCallbackQuery(ctx.token, query.id);
    state.selected.proxies = [];
    await saveState(ctx.supabase, state);
    const proxies = await listProxies(ctx.supabase);
    const view = buildProxyList(proxies, new Set());
    await edit(view.text, { reply_markup: view.reply_markup });
    return;
  }

  if (data === 'proxy:copy') {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const ids = state.selected.proxies;
    if (!ids.length) return;
    const proxies = await getProxiesByIds(ctx.supabase, ids);
    if (proxies.length) {
      const rows: any[][] = [];
      for (const p of proxies) {
        const link = `https://t.me/proxy?server=${encodeURIComponent(p.host)}&port=${p.port}&secret=${encodeURIComponent(p.secret ?? '')}`;
        rows.push([{ text: `${p.host}:${p.port}`, url: link }]);
      }
      rows.push([{ text: BTN_MENU, callback_data: 'menu' }]);
      await tg.sendMessage(ctx.token, chatId, '🌐 Tap a proxy to apply it in Telegram:', {
        reply_markup: urlKeyboard(rows),
      });
    }
    await tg.answerCallbackQuery(ctx.token, query.id, `${proxies.length} proxy link(s) ready!`);
    state.selected.proxies = [];
    await saveState(ctx.supabase, state);
    return;
  }

  if (data === 'proxy:delete') {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const ids = state.selected.proxies;
    if (!ids.length) return;
    const deleted = await bulkDeleteByIds(ctx.supabase, 'scraper_proxies', ids);
    await edit(`✅ Deleted ${deleted} proxy/proxies.`, { reply_markup: inlineMenuButton() });
    state.selected.proxies = [];
    await saveState(ctx.supabase, state);
    return;
  }

  // ── Scraper sub-handlers ──
  if (data.startsWith('scraper:')) {
    const action = data.split(':')[1];      if (action === 'scrape') {
      await tg.answerCallbackQuery(ctx.token, query.id);
      if (!ctx.ghToken || !ctx.ghRepo) {
        await edit('❌ GitHub not configured.\nSet GH_PAT and GH_REPO secrets.', { reply_markup: inlineMenuButton() });
        return;
      }
      await edit('🚀 Triggering scrape...');
      // Remember this chat so scheduled auto-runs can report failures
      // back here (the workflow report step falls back to this key).
      setConfig(ctx.supabase, 'scrape_report_chat_id', String(chatId));
      const result = await dispatchScrape(ctx, chatId);
      if (result.ok) {
        await edit('✅ Scrape triggered! w8 2-3min', { reply_markup: inlineMenuButton() });
      } else {
        const detail = result.status ? ` (HTTP ${result.status})` : '';
        const hint = result.status === 404
          ? '\n\nWorkflow "' + ctx.ghWorkflow + '" not found in ' + ctx.ghRepo + '. Check GH_WORKFLOW secret.'
          : result.status === 403 || result.status === 401
            ? '\n\nBad token. GH_PAT needs "Actions: read & write" on ' + ctx.ghRepo + '.'
            : '';
        await edit(`❌ Scrape failed${detail}${hint}\n${result.error ?? ''}`, { reply_markup: inlineMenuButton() });
      }
    } else if (action === 'listproxy') {
      await tg.answerCallbackQuery(ctx.token, query.id);
      state.selected.proxies = [];
      await saveState(ctx.supabase, state);
      const proxies = await listProxies(ctx.supabase);
      if (!proxies.length) {
        await edit('Proxy pool is empty.', { reply_markup: inlineMenuButton() });
        return;
      }
      const view = buildProxyList(proxies, new Set());
      await edit(view.text, { reply_markup: view.reply_markup });
    } else if (action === 'listchannel') {
      await tg.answerCallbackQuery(ctx.token, query.id);
      const channels = await getChannels(ctx.supabase);
      const text = channels.length
        ? `*Channels (${channels.length})*\n\n` + channels.map((c) => `@${c}`).join('\n')
        : 'No channels configured.';
      const kb = urlKeyboard([
        [{ text: '➕ Add channel', callback_data: 'scraper:addchannel' }],
        [{ text: '🗑 Remove channel', callback_data: 'scraper:delchannel' }],
        [{ text: BTN_MENU, callback_data: 'menu' }],
      ]);
      await edit(text, { parse_mode: 'Markdown', reply_markup: kb });
    } else if (['addproxy', 'delproxy', 'addchannel', 'delchannel'].includes(action)) {
      await tg.answerCallbackQuery(ctx.token, query.id);
      const prompts: Record<string, string> = {
        addproxy: 'Send a proxy link:\n`https://t.me/proxy?server=..&port=..&secret=..`',
        delproxy: 'Send the proxy host or id to remove, or `all`.',
        addchannel: 'Send the channel username:\n`@channel_name`',
        delchannel: 'Send the channel username to remove.',
      };
      state.pending = action;
      await saveState(ctx.supabase, state);
      await edit(prompts[action], { parse_mode: 'Markdown', reply_markup: inlineMenuButton() });
    } else if (action === 'schedule') {
      await tg.answerCallbackQuery(ctx.token, query.id);
      const s = await getSchedule(ctx.supabase);
      await edit(scheduleText(s), { parse_mode: 'Markdown', reply_markup: scheduleKeyboard(s) });
    } else if (action === 'schedset' || action === 'schedstart' || action === 'schedstop') {
      await tg.answerCallbackQuery(ctx.token, query.id);
      // Scheduler actions imply this is the admin chat — remember it so
      // scheduled-run failure reports have a destination even if the
      // admin never used manual /scrape.
      setConfig(ctx.supabase, 'scrape_report_chat_id', String(chatId));
      if (action === 'schedset') {
        const hours = Number(data.split(':')[2]);
        if (!ALLOWED_INTERVALS.includes(hours)) {
          await edit('Unsupported interval.', { reply_markup: inlineMenuButton() });
          return;
        }
        await setConfig(ctx.supabase, SCHEDULE_HOURS_KEY, String(hours));
      } else {
        await setConfig(ctx.supabase, SCHEDULE_ENABLED_KEY, action === 'schedstart' ? 'true' : 'false');
      }
      const s = await getSchedule(ctx.supabase);
      const verb = action === 'schedset' ? 'Interval set' : action === 'schedstart' ? 'Auto-scrape started' : 'Auto-scrape stopped';
      await tg.answerCallbackQuery(ctx.token, query.id, verb);
      await edit(scheduleText(s), { parse_mode: 'Markdown', reply_markup: scheduleKeyboard(s) });
    }
    return;
  }

  // ── Contact requests (public → admin) ───────────────────────────
  if (data === 'contact:start') {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const who = contactName(query.from?.username ?? null, query.from?.first_name ?? null, uid);
    const result = await createContactRequest(
      ctx.supabase,
      uid,
      query.from?.username ?? null,
      query.from?.first_name ?? null,
    );
    const back = urlKeyboard([
      [{ text: '📥 Download App', url: ctx.downloadUrl }],
      [{ text: '✉️ Contact Us', callback_data: 'contact:start' }],
    ]);
    if (result === 'created') {
      for (const adminId of ctx.adminIds) {
        await tg.sendMessage(ctx.token, adminId, `📩 *New contact request* from ${who}`, {
          parse_mode: 'Markdown',
          reply_markup: {
            inline_keyboard: [
              [{ text: '✅ Approve', callback_data: `msg:approve:${uid}:0` }],
              [{ text: '🗑 Remove', callback_data: `msg:del:${uid}:0` }],
            ],
          },
        });
      }
      await tg.sendMessage(ctx.token, chatId, '✅ Contact request sent! An admin will confirm you — then you can message us (up to 50 words).', {
        reply_markup: back,
      });
    } else if (result === 'exists') {
      await tg.sendMessage(ctx.token, chatId, '📨 You already have a contact request with us — the admin will get back to you here.', {
        reply_markup: back,
      });
    } else {
      await tg.sendMessage(ctx.token, chatId, '❌ Could not send the request — please try again later.', {
        reply_markup: back,
      });
    }
    return;
  }

  if (data.startsWith('msg:page:')) {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const page = Number(data.split(':')[2]);
    const total = await countContacts(ctx.supabase);
    const contacts = await listContacts(ctx.supabase, page);
    if (!contacts.length) {
      await edit('✉️ No contact requests yet.', { reply_markup: inlineMenuButton() });
      return;
    }
    const view = buildContactList(contacts, page, total);
    await edit(view.text, { parse_mode: 'Markdown', reply_markup: view.reply_markup });
    return;
  }

  if (data.startsWith('msg:approve:')) {
    await tg.answerCallbackQuery(ctx.token, query.id, 'Approved!');
    const parts = data.split(':');
    const userId = Number(parts[2]);
    const page = Number(parts[3] ?? 0);
    await setContactStatus(ctx.supabase, userId, 'approved');
    await tg.sendMessage(ctx.token, userId, '✅ Admin approved your contact request — send us a message (up to 50 words).');
    const total = await countContacts(ctx.supabase);
    const contacts = await listContacts(ctx.supabase, page);
    const view = buildContactList(contacts, page, total);
    await edit(view.text, { parse_mode: 'Markdown', reply_markup: view.reply_markup });
    return;
  }

  if (data.startsWith('msg:reply:')) {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const parts = data.split(':');
    const userId = Number(parts[2]);
    const contact = await getContactByUserId(ctx.supabase, userId);
    const who = contactName(contact?.username ?? null, contact?.first_name ?? null, userId);
    state.pending = `msg_reply:${userId}`;
    await saveState(ctx.supabase, state);
    await edit(`Send your reply (up to ${MAX_CONTACT_WORDS} words) for *${who}*:`, {
      parse_mode: 'Markdown',
      reply_markup: inlineMenuButton(),
    });
    return;
  }

  if (data.startsWith('msg:del:')) {
    await tg.answerCallbackQuery(ctx.token, query.id, 'Removed');
    const parts = data.split(':');
    const userId = Number(parts[2]);
    const page = Number(parts[3] ?? 0);
    await deleteContact(ctx.supabase, userId);
    const total = await countContacts(ctx.supabase);
    const contacts = await listContacts(ctx.supabase, page);
    if (!contacts.length) {
      await edit('✉️ No contact requests yet.', { reply_markup: inlineMenuButton() });
      return;
    }
    const view = buildContactList(contacts, page, total);
    await edit(view.text, { parse_mode: 'Markdown', reply_markup: view.reply_markup });
    return;
  }

  // ── Version ──
  if (data.startsWith('version:')) {
    await tg.answerCallbackQuery(ctx.token, query.id);
    const action = data.split(':')[1];
    if (action === 'show') {
      await edit('*📰 Version config*\n\nUse `/version` command to see full config.', {
        parse_mode: 'Markdown',
        reply_markup: inlineMenuButton(),
      });
    }
    return;
  }

  await tg.answerCallbackQuery(ctx.token, query.id);
}

// ─── Slash commands ───────────────────────────────────────────
async function handleCommand(ctx: BotContext, msg: any): Promise<void> {
  const chatId = msg.chat?.id;
  const uid = msg.from?.id;
  const text = (msg.text ?? '') as string;
  if (chatId == null || uid == null) return;
  const isAdmin = ctx.adminIds.size === 0 || ctx.adminIds.has(uid);
  const [cmd, ...rest] = text.split(/\s+/);
  const arg = rest.join(' ').trim();

  if (cmd === '/start') {
    if (isAdmin) {
      await tg.sendMessage(ctx.token, chatId, MENU_TEXT, { reply_markup: mainKeyboard() });
    } else {
      await tg.sendMessage(ctx.token, chatId, '🌐 *VlessHub*\nFree VLESS VPN configs & MTProto proxies.', {
        parse_mode: 'Markdown',
        reply_markup: urlKeyboard([
          [{ text: '📥 Download App', url: ctx.downloadUrl }],
          [{ text: '✉️ Contact Us', callback_data: 'contact:start' }],
        ]),
      });
    }
    return;
  }

  if (cmd === '/scrape') {
    if (!isAdmin) {
      await tg.sendMessage(ctx.token, chatId, 'Admin only.');
      return;
    }
    if (!ctx.ghToken || !ctx.ghRepo) {
      await tg.sendMessage(ctx.token, chatId, '❌ GitHub not configured.\nSet GH_PAT and GH_REPO secrets.', { reply_markup: mainKeyboard() });
      return;
    }
    await tg.sendMessage(ctx.token, chatId, '🚀 Triggering scrape...');
    // Remember this chat so scheduled auto-runs can report failures
    // back here (the workflow report step falls back to this key).
    setConfig(ctx.supabase, 'scrape_report_chat_id', String(chatId));
    const result = await dispatchScrape(ctx, chatId);
    if (result.ok) {
      await tg.sendMessage(ctx.token, chatId, '✅ Scrape triggered! w8 2-3min', { reply_markup: mainKeyboard() });
    } else {
      const detail = result.status ? ` (HTTP ${result.status})` : '';
      const hint = result.status === 404
        ? '\nWorkflow "' + ctx.ghWorkflow + '" not found in ' + ctx.ghRepo + '. Check GH_WORKFLOW.'
        : result.status === 403 || result.status === 401
          ? '\nBad token. GH_PAT needs "Actions: read & write" on ' + ctx.ghRepo + '.'
          : '';
      await tg.sendMessage(ctx.token, chatId, `❌ Scrape failed${detail}${hint}\n${result.error ?? ''}`, { reply_markup: mainKeyboard() });
    }
    return;
  }

  if (cmd === '/version') {
    const serversN = await countRows(ctx.supabase, 'servers');
    const filesN = await countRows(ctx.supabase, 'vpn_files');
    const proxiesN = await countRows(ctx.supabase, 'scraper_proxies');
    const channels = await getChannels(ctx.supabase);
    const text =
      `*VlessHub Bot* v1.0.0\n\n` +
      `Links: ${serversN}\nFiles: ${filesN}\nProxies: ${proxiesN}\nChannels: ${channels.length}\n\n` +
      `Supabase: ✅\nGitHub: ${ctx.ghToken && ctx.ghRepo ? '✅' : '❌'}`;
    await tg.sendMessage(ctx.token, chatId, text, { parse_mode: 'Markdown', reply_markup: mainKeyboard() });
    return;
  }

  if (cmd === '/myid') {
    await tg.sendMessage(ctx.token, chatId, `Your Telegram user ID: \`${uid}\``, { parse_mode: 'Markdown' });
    return;
  }

  if (cmd === '/stats') {
    const total = await countRows(ctx.supabase, 'servers');
    await tg.sendMessage(ctx.token, chatId, `Active servers in DB: ${total}`, { reply_markup: mainKeyboard() });
    return;
  }
}

// ─── Entry: route an incoming Telegram update ─────────────────
export async function routeUpdate(ctx: BotContext, update: any): Promise<void> {
  if (update.message) {
    const text = (update.message.text ?? '') as string;
    if (update.message.text?.startsWith('/')) {
      await handleCommand(ctx, update.message);
    } else {
      await handleMessage(ctx, update.message);
    }
    return;
  }
  if (update.callback_query) {
    await handleCallback(ctx, update.callback_query);
    return;
  }
}
