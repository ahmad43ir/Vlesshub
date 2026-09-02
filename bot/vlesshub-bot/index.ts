// ============================================================
// 📁 index.ts — VLESSHUB BOT ENTRY POINT (WEBHOOK MODE)
// ============================================================
// Supabase Edge Function replacement for vlesshub/telegram-bot/bot.py.
//
// Edge functions can't run Telegram's long-poll loop, so this bot runs
// in WEBHOOK mode: Telegram POSTs updates here and we answer via the
// Bot API. Register the webhook once with POST /setwebhook
// (X-Admin-Key), after which Telegram delivers updates to:
//   https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/vlesshub-bot
//
// Endpoints:
//   POST /               — Telegram update (validated by secret_token)
//   POST /setwebhook     — (X-Admin-Key) register the Telegram webhook
//   POST /deletewebhook  — (X-Admin-Key) remove the webhook
//   POST /getwebhookinfo — (X-Admin-Key) show webhook status
//   GET  / or /health    — health check
//
// Environment (set via `supabase secrets set`):
//   VLESSHUB_BOT_TOKEN — vlesshub Telegram bot token from @BotFather
//   ADMIN_IDS          — comma-separated Telegram user IDs (single admin)
//   ADMIN_KEY          — shared secret for the admin endpoints
//   GH_PAT             — GitHub PAT ("Actions: read & write") for /scrape
//   GH_REPO            — "owner/repo" to dispatch the scraper on (default ahmad43ir/VlessHub)
//   GH_REF             — branch/ref for workflow dispatch (default main)
//   GH_WORKFLOW        — workflow file name (default scrape.yml)
//   DOWNLOAD_URL       — app download link for public users
//   CONTACT_EMAIL / CHANNEL_URL — public-user footer links
//   (CONTACT_EMAIL is a URL, default https://t.me/Vless_hub_bot — no email)
//   (SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY are injected automatically)
//
// Deploy: supabase functions deploy vlesshub-bot --project-ref bprkazfxqmanrybiexnh --no-verify-jwt
// ============================================================

import { createClient } from 'jsr:@supabase/supabase-js@2';
import { corsPreflight, jsonResponse, log, requireEnv } from './_utils.ts';
import * as tg from './_telegram.ts';
import { getWebhookSecret, saveWebhookSecret } from './_state.ts';
import { routeUpdate, type BotContext } from './_handlers.ts';

// ─── Environment at module load (fail fast on required vars) ──
const BOT_TOKEN = requireEnv('VLESSHUB_BOT_TOKEN');
const SUPABASE_URL = requireEnv('SUPABASE_URL');
const SUPABASE_KEY = requireEnv('SUPABASE_SERVICE_ROLE_KEY');
const ADMIN_KEY = Deno.env.get('ADMIN_KEY') ?? '';
const ADMIN_IDS = new Set(
  (Deno.env.get('ADMIN_IDS') ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter((s) => /^\d+$/.test(s))
    .map(Number),
);
const GH_PAT = Deno.env.get('GH_PAT') ?? '';
const GH_REPO = Deno.env.get('GH_REPO') ?? 'ahmad43ir/VlessHub';
const GH_REF = Deno.env.get('GH_REF') ?? 'main';
const GH_WORKFLOW = Deno.env.get('GH_WORKFLOW') ?? 'scrape.yml';
const DOWNLOAD_URL = Deno.env.get('DOWNLOAD_URL') ?? 'https://vlesshub-2i2.pages.dev';
const CONTACT_EMAIL = Deno.env.get('CONTACT_EMAIL') ?? 'https://t.me/Vless_hub_bot';

if (ADMIN_IDS.size === 0) {
  log('warn', 'entry', 'ADMIN_IDS is empty — the bot starts but denies everyone (type /myid to discover your ID).');
}
if (!ADMIN_KEY) {
  log('warn', 'entry', 'ADMIN_KEY is empty — admin endpoints (setwebhook etc.) will reject all requests.');
}
if (!GH_PAT) {
  log('warn', 'entry', 'GH_PAT is empty — /scrape and the "Run scrape" button will fail.');
}

const supabase = createClient(SUPABASE_URL, SUPABASE_KEY);

const ctx: BotContext = {
  token: BOT_TOKEN,
  supabase,
  adminIds: ADMIN_IDS,
  ghToken: GH_PAT,
  ghRepo: GH_REPO,
  ghWorkflow: GH_WORKFLOW,
  downloadUrl: DOWNLOAD_URL,
  contactEmail: CONTACT_EMAIL,
};

// ─── Helpers ─────────────────────────────────────────────────

function normalizedRoute(pathname: string): string {
  const marker = '/vlesshub-bot';
  const idx = pathname.lastIndexOf(marker);
  const path = idx >= 0 ? pathname.slice(idx + marker.length) : pathname;
  return path === '' ? '/' : path;
}

function requireAdminKey(req: Request): boolean {
  const provided = req.headers.get('X-Admin-Key') ?? '';
  return ADMIN_KEY !== '' && provided === ADMIN_KEY;
}

async function handleAdmin(req: Request, route: string): Promise<Response> {
  const webhookUrl = `${SUPABASE_URL}/functions/v1/vlesshub-bot`;
  try {
    if (route === '/setwebhook') {
      let secret = await getWebhookSecret(supabase);
      if (!secret) {
        secret = crypto.randomUUID().replace(/-/g, '');
        await saveWebhookSecret(supabase, secret);
      }
      const ok = await tg.setWebhook(BOT_TOKEN, webhookUrl, secret);
      if (!ok) {
        return jsonResponse(
          { error: 'setWebhook failed — check VLESSHUB_BOT_TOKEN and that the function URL is HTTPS' },
          502,
        );
      }
      return jsonResponse({ ok: true, webhookUrl, secret });
    }

    if (route === '/deletewebhook') {
      const ok = await tg.deleteWebhook(BOT_TOKEN);
      if (!ok) {
        return jsonResponse({ error: 'deleteWebhook failed' }, 502);
      }
      return jsonResponse({ ok: true });
    }

    // /getwebhookinfo
    const info = await tg.getWebhookInfo(BOT_TOKEN);
    if (!info) {
      return jsonResponse({ error: 'getWebhookInfo failed' }, 502);
    }
    return jsonResponse({ ok: true, webhook: info });
  } catch (e) {
    log('error', 'admin', `Admin endpoint ${route} failed: ${(e as Error).message}`);
    return jsonResponse({ error: 'Internal server error' }, 500);
  }
}

// ─── HTTP handler ────────────────────────────────────────────

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return corsPreflight();

  const url = new URL(req.url);
  const route = normalizedRoute(url.pathname);

  // ── Health ───────────────────────────────────────────────
  if (req.method === 'GET' && (route === '/' || route === '/health')) {
    return jsonResponse({
      status: 'ok',
      service: 'vlesshub-bot',
      webhook: `${SUPABASE_URL}/functions/v1/vlesshub-bot`,
    });
  }

  // ── Admin endpoints ──────────────────────────────────────
  if (
    req.method === 'POST' &&
    (route === '/setwebhook' || route === '/deletewebhook' || route === '/getwebhookinfo')
  ) {
    if (!requireAdminKey(req)) {
      return jsonResponse({ error: 'Unauthorized — valid X-Admin-Key required' }, 401);
    }
    return await handleAdmin(req, route);
  }

  // ── Telegram updates (only POST to /) ────────────────────
  if (req.method !== 'POST' || route !== '/') {
    return jsonResponse({ error: 'Not found' }, 404);
  }

  // Validate the secret_token Telegram signed the webhook with.
  const secret = await getWebhookSecret(supabase);
  const provided = req.headers.get('X-Telegram-Bot-Api-Secret-Token');
  if (!secret || provided !== secret) {
    log('warn', 'entry', 'Rejected update — invalid or missing webhook secret token');
    return jsonResponse({ error: 'Unauthorized' }, 401);
  }

  let update: any;
  try {
    update = await req.json();
  } catch {
    return jsonResponse({ error: 'Bad request' }, 400);
  }

  try {
    await routeUpdate(ctx, update);
  } catch (e) {
    log('error', 'entry', `Unhandled update error: ${(e as Error).message}`);
  }

  return jsonResponse({ ok: true });
});
