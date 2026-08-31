"""
VlessHub Telegram Bot — Multi-Select Edition
================================================================
All lists (Servers, Files, Proxies) support selecting multiple items,
then bulk Copy or Delete on the selection.
"""

import asyncio
import base64
import logging
import os
import sys
from datetime import datetime, timezone
from io import BytesIO

import httpx
from telegram import (
    InlineKeyboardButton,
    InlineKeyboardMarkup,
    ReplyKeyboardMarkup,
    Update,
)
from telegram.ext import (
    Application,
    CallbackQueryHandler,
    CommandHandler,
    ContextTypes,
    MessageHandler,
    filters,
)

# ─── .env Loading ──────────────────────────────────────────────
def _load_dotenv(path: str = ".env") -> None:
    try:
        with open(path, encoding="utf-8") as f:
            for raw in f:
                line = raw.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                key, _, val = line.partition("=")
                key = key.strip()
                if key and key not in os.environ:
                    os.environ[key] = val.strip()
    except FileNotFoundError:
        pass

_load_dotenv()

# ─── Logging ───────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
log = logging.getLogger("vlesshub-bot")

# ─── Config ────────────────────────────────────────────────────
BOT_TOKEN: str = os.environ.get("BOT_TOKEN", "[REDACTED-TELEGRAM-BOT-TOKEN]")
SUPABASE_URL: str = os.environ.get("SUPABASE_URL", "https://bprkazfxqmanrybiexnh.supabase.co")
SUPABASE_KEY: str = os.environ.get("SUPABASE_KEY", "")
GITHUB_TOKEN: str = os.environ.get("GITHUB_TOKEN", "")
GITHUB_REPO: str = os.environ.get("GITHUB_REPO", "")
GITHUB_WORKFLOW: str = os.environ.get("GITHUB_WORKFLOW", "vless-scraper.yml")
DOWNLOAD_URL = os.environ.get("DOWNLOAD_URL", "https://chobgroup.pages.dev")
CONTACT_EMAIL = os.environ.get("CONTACT_EMAIL", "privacy@rootnet.app")
CHANNEL_URL = os.environ.get("CHANNEL_URL", "https://t.me/vless_hub")
BOT_VERSION = "1.0.0"

ADMIN_USERS_RAW: str = os.environ.get("ADMIN_USERS", "")
ADMIN_USERS: set[int] = {
    int(uid.strip()) for uid in ADMIN_USERS_RAW.split(",") if uid.strip().isdigit()
}

CONFIG_TABLE = "scraper_config"
PAGE_SIZE = 5

# ─── Button labels ─────────────────────────────────────────────
BTN_SERVERS = "\U0001f5a5 Links"
BTN_VPN_FILES = "\U0001f4c4 VPN Files"
BTN_PROXIES = "\U0001f310 Proxies"
BTN_SCRAPER = "\U0001f9ea Scraper"
BTN_VERSION = "\U0001f4f0 Version"
BTN_HELP = "\u2753 Help"
BTN_MENU = "\U0001f3e0 Menu"

# ─── Selection state (in-memory per chat) ──────────────────────
# Structure: { chat_id: { "servers": {id1, id2, ...}, "files": {id1, id2, ...}, "proxies": {id1, id2, ...} } }
_selections: dict[int, dict[str, set[int]]] = {}

def _sel(chat_id: int) -> dict[str, set[int]]:
    if chat_id not in _selections:
        _selections[chat_id] = {"servers": set(), "files": set(), "proxies": set()}
    return _selections[chat_id]

def _toggle(chat_id: int, kind: str, item_id: int) -> bool:
    """Toggle selection. Returns True if now selected."""
    s = _sel(chat_id)
    if item_id in s[kind]:
        s[kind].discard(item_id)
        return False
    else:
        s[kind].add(item_id)
        return True

def _clear(chat_id: int, kind: str | None = None) -> None:
    s = _sel(chat_id)
    if kind:
        s[kind] = set()
    else:
        s["servers"] = set()
        s["files"] = set()
        s["proxies"] = set()

# ─── Auth ──────────────────────────────────────────────────────

def is_admin(uid: int) -> bool:
    return not ADMIN_USERS or uid in ADMIN_USERS

# ─── Supabase helpers ──────────────────────────────────────────

def _headers(prefer: str = "return=minimal") -> dict[str, str]:
    return {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}", "Prefer": prefer}

async def sb_get(table: str, params: dict | None = None) -> list[dict]:
    async with httpx.AsyncClient(timeout=15) as c:
        r = await c.get(f"{SUPABASE_URL.rstrip('/')}/rest/v1/{table}", headers=_headers(), params=params or {})
        r.raise_for_status()
        return r.json() or []

async def sb_count(table: str) -> int:
    async with httpx.AsyncClient(timeout=15) as c:
        r = await c.get(
            f"{SUPABASE_URL.rstrip('/')}/rest/v1/{table}",
            headers={**_headers(), "Prefer": "count=exact"},
            params={"select": "id", "limit": "0"},
        )
        cr = r.headers.get("content-range", "*/0")
        return int(cr.split("/")[-1]) if cr.split("/")[-1].isdigit() else 0

async def sb_delete(table: str, filters: dict) -> bool:
    async with httpx.AsyncClient(timeout=15) as c:
        r = await c.delete(f"{SUPABASE_URL.rstrip('/')}/rest/v1/{table}", headers=_headers(), params=filters)
        return r.status_code in (200, 204)

async def sb_bulk_delete(table: str, ids: list[int]) -> int:
    """Delete multiple rows by id. Returns count deleted."""
    deleted = 0
    for item_id in ids:
        ok = await sb_delete(table, {"id": f"eq.{item_id}"})
        if ok:
            deleted += 1
    return deleted

async def get_config(key: str) -> str | None:
    data = await sb_get(CONFIG_TABLE, {"key": f"eq.{key}", "select": "value"})
    return data[0]["value"] if data else None

async def get_channels() -> list[str]:
    value = await get_config("vless_channels")
    if not value:
        return []
    return [c.strip().lstrip("@") for c in value.split(",") if c.strip()]

# ─── Keyboard builders ─────────────────────────────────────────

def main_keyboard() -> ReplyKeyboardMarkup:
    return ReplyKeyboardMarkup(
        [
            [BTN_SERVERS, BTN_VPN_FILES],
            [BTN_PROXIES, BTN_SCRAPER],
            [BTN_VERSION, BTN_HELP],
        ],
        resize_keyboard=True,
        input_field_placeholder="Choose an option",
    )

def inline_menu_button() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup([[InlineKeyboardButton(BTN_MENU, callback_data="menu")]])

# ─── Multi-select list builders ────────────────────────────────

def _build_server_list(servers: list[dict], selected: set[int], page: int, total: int) -> tuple[str, InlineKeyboardMarkup]:
    """Build server list with checkboxes."""
    rows = []
    for s in servers:
        mark = "\u2611" if s["id"] in selected else "\u2610"
        label = f"{mark} {s.get('flag','')} {s.get('name','?')}"
        rows.append([InlineKeyboardButton(label, callback_data=f"srv:toggle:{s['id']}:{page}")])

    sel_count = len(selected)
    nav = []
    if page > 0:
        nav.append(InlineKeyboardButton("\u25c0 Prev", callback_data=f"srv:page:{page-1}"))
    if (page + 1) * PAGE_SIZE < total:
        nav.append(InlineKeyboardButton("Next \u25b6", callback_data=f"srv:page:{page+1}"))
    if nav:
        rows.append(nav)

    # Action buttons
    actions = []
    if sel_count > 0:
        actions.append(InlineKeyboardButton(f"\U0001f4cb Copy ({sel_count})", callback_data="srv:copy"))
        actions.append(InlineKeyboardButton(f"\U0001f5d1 Delete ({sel_count})", callback_data="srv:delete"))
    if sel_count > 0:
        rows.append([
            InlineKeyboardButton(f"\U0001f4cb Copy ({sel_count})", callback_data="srv:copy"),
            InlineKeyboardButton(f"\U0001f5d1 Delete ({sel_count})", callback_data="srv:delete"),
        ])
        rows.append([
            InlineKeyboardButton(f"\u2611 Select all", callback_data=f"srv:selectall:{page}"),
            InlineKeyboardButton(f"\u2610 Deselect all", callback_data=f"srv:deselectall:{page}"),
        ])
    else:
        rows.append([
            InlineKeyboardButton(f"\u2611 Select all", callback_data=f"srv:selectall:{page}"),
            InlineKeyboardButton(BTN_MENU, callback_data="menu"),
        ])

    page_total = min(PAGE_SIZE, total - page * PAGE_SIZE)
    text = f"\U0001f5a5 Links ({total})\nPage {page+1}/{(total+PAGE_SIZE-1)//PAGE_SIZE} \u2022 {page_total} items"
    if sel_count > 0:
        text += f"\n\u2705 {sel_count} selected"
    return text, InlineKeyboardMarkup(rows)

def _build_file_list(files: list[dict], selected: set[int], page: int, total: int) -> tuple[str, InlineKeyboardMarkup]:
    """Build file list with checkboxes."""
    rows = []
    for f in files:
        mark = "\u2611" if f["id"] in selected else "\u2610"
        size_kb = round((f.get("size_bytes") or 0) / 1024)
        enc = "\U0001f512" if f.get("is_encrypted") else ""
        label = f"{mark} {enc}{f['filename']} ({size_kb}KB)"
        rows.append([InlineKeyboardButton(label, callback_data=f"file:toggle:{f['id']}:{page}")])

    sel_count = len(selected)
    nav = []
    if page > 0:
        nav.append(InlineKeyboardButton("\u25c0 Prev", callback_data=f"file:page:{page-1}"))
    if (page + 1) * PAGE_SIZE < total:
        nav.append(InlineKeyboardButton("Next \u25b6", callback_data=f"file:page:{page+1}"))
    if nav:
        rows.append(nav)

    actions = []
    if sel_count > 0:
        actions.append(InlineKeyboardButton(f"\u2b07\ufe0f Download ({sel_count})", callback_data="file:download"))
        actions.append(InlineKeyboardButton(f"\U0001f5d1 Delete ({sel_count})", callback_data="file:delete"))
    if sel_count > 0:
        rows.append([
            InlineKeyboardButton(f"\u2b07\ufe0f Download ({sel_count})", callback_data="file:download"),
            InlineKeyboardButton(f"\U0001f5d1 Delete ({sel_count})", callback_data="file:delete"),
        ])
        rows.append([
            InlineKeyboardButton(f"\u2611 Select all", callback_data=f"file:selectall:{page}"),
            InlineKeyboardButton(f"\u2610 Deselect all", callback_data=f"file:deselectall:{page}"),
        ])
    else:
        rows.append([
            InlineKeyboardButton(f"\u2611 Select all", callback_data=f"file:selectall:{page}"),
            InlineKeyboardButton(BTN_MENU, callback_data="menu"),
        ])

    page_total = min(PAGE_SIZE, total - page * PAGE_SIZE)
    text = f"\U0001f4c4 VPN Files ({total})\nPage {page+1}/{(total+PAGE_SIZE-1)//PAGE_SIZE} \u2022 {page_total} items"
    if sel_count > 0:
        text += f"\n\u2705 {sel_count} selected"
    return text, InlineKeyboardMarkup(rows)

def _build_proxy_list(proxies: list[dict], selected: set[int]) -> tuple[str, InlineKeyboardMarkup]:
    """Build proxy list with checkboxes."""
    rows = []
    for p in proxies:
        mark = "\u2611" if p["id"] in selected else "\u2610"
        status = "\u2705" if p.get("last_ok") and p.get("is_active") else "\u274c"
        label = f"{mark} {status} {p['host']}:{p['port']}"
        rows.append([InlineKeyboardButton(label, callback_data=f"proxy:toggle:{p['id']}")])

    sel_count = len(selected)
    actions = []
    if sel_count > 0:
        actions.append(InlineKeyboardButton(f"\U0001f4cb Copy ({sel_count})", callback_data="proxy:copy"))
        actions.append(InlineKeyboardButton(f"\U0001f5d1 Delete ({sel_count})", callback_data="proxy:delete"))
    if sel_count > 0:
        rows.append([
            InlineKeyboardButton(f"\U0001f4cb Copy ({sel_count})", callback_data="proxy:copy"),
            InlineKeyboardButton(f"\U0001f5d1 Delete ({sel_count})", callback_data="proxy:delete"),
        ])
        rows.append([
            InlineKeyboardButton(f"\u2611 Select all", callback_data="proxy:selectall"),
            InlineKeyboardButton(f"\u2610 Deselect all", callback_data="proxy:deselectall"),
        ])
    else:
        rows.append([
            InlineKeyboardButton(f"\u2611 Select all", callback_data="proxy:selectall"),
            InlineKeyboardButton(BTN_MENU, callback_data="menu"),
        ])

    text = f"\U0001f310 Proxies ({len(proxies)})"
    if sel_count > 0:
        text += f"\n\u2705 {sel_count} selected"
    return text, InlineKeyboardMarkup(rows)

# ─── Menu texts ────────────────────────────────────────────────

MENU_TEXT = (
    "VlessHub \u2014 official config publishing channel: @Vless_hub_bot.\n\n"
    "New VPN configs (VLESS \u2022 VMess \u2022 Trojan \u2022 SS \u2022 Hysteria2 \u2022 WireGuard \u2022 SOCKS) "
    "are published here and flow straight into the app.\n\n"
    "\U0001f5a5 Links \u2014 list & delete servers\n"
    "\U0001f4c4 VPN Files \u2014 browse & download raw config files (.npvt, .sip, .npv, .json, etc.)\n"
    "\U0001f310 Proxies \u2014 MTProto proxy pool\n"
    "\U0001f9ea Scraper \u2014 run the config scraper / manage proxies & channels\n"
    "\U0001f4f0 Version \u2014 version management\n"
    "\u2753 Help \u2014 how it works & what each option does"
)

HELP_TEXT = (
    "*VlessHub bot \u2014 menu guide*\n\n"
    "\U0001f5a5 *Links*\n"
    "Tap servers to select them (checkmarks appear). Then tap *Copy* or *Delete* to act on the selection.\n\n"
    "\U0001f4c4 *VPN Files*\n"
    "Browse and download raw config files. Select multiple files, then *Download* or *Delete*.\n\n"
    "\U0001f9ea *Scraper*\n"
    "Manage proxies & channels, run the scraper.\n\n"
    "\U0001f4f0 *Version*\n"
    "`/version` show config \u2022 `/setmin` \u2022 `/setlatest` \u2022 `/setbuild` \u2022 `/forceupdate`\n\n"
    "*Commands*: /start \u2022 /stats \u2022 /scrape \u2022 /myid"
)

# ─── /start ────────────────────────────────────────────────────

async def cmd_start(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.message or not update.effective_user:
        return
    uid = update.effective_user.id
    if is_admin(uid):
        await update.message.reply_text(MENU_TEXT, reply_markup=main_keyboard())
    else:
        kb = InlineKeyboardMarkup([
            [InlineKeyboardButton("\U0001f4e5 Download App", url=DOWNLOAD_URL)],
            [InlineKeyboardButton("\U0001f4e3 Our Channel", url=CHANNEL_URL)],
            [InlineKeyboardButton("\u2709\ufe0f Contact Us", url=f"mailto:{CONTACT_EMAIL}")],
        ])
        await update.message.reply_text(
            "\U0001f310 *VlessHub*\nFree VLESS VPN configs & MTProto proxies.",
            reply_markup=kb, parse_mode="Markdown",
        )

# ─── Persistent keyboard handler ───────────────────────────────

async def handle_main_keyboard(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.message or not update.effective_user:
        return
    uid = update.effective_user.id
    text = update.message.text or ""

    if not is_admin(uid):
        await update.message.reply_text(
            "\U0001f4e5 Download the app to browse free VLESS configs:",
            reply_markup=InlineKeyboardMarkup([
                [InlineKeyboardButton("\U0001f4e5 Download App", url=DOWNLOAD_URL)],
                [InlineKeyboardButton("\u2709\ufe0f Contact Us", url=f"mailto:{CONTACT_EMAIL}")],
            ]),
        )
        return

    if text == BTN_SERVERS:
        chat_id = update.effective_chat.id
        _clear(chat_id, "servers")
        total = await sb_count("servers")
        servers = await sb_get("servers", {"select": "id,name,flag", "order": "id.desc", "limit": str(PAGE_SIZE)})
        if not servers:
            await update.message.reply_text("No servers in the database.", reply_markup=main_keyboard())
            return
        text_msg, kb = _build_server_list(servers, _sel(chat_id)["servers"], 0, total)
        await update.message.reply_text(text_msg, reply_markup=kb)

    elif text == BTN_VPN_FILES:
        chat_id = update.effective_chat.id
        _clear(chat_id, "files")
        total = await sb_count("vpn_files")
        files = await sb_get("vpn_files", {
            "select": "id,filename,size_bytes,source_channel,is_encrypted",
            "order": "id.desc", "limit": str(PAGE_SIZE),
        })
        if not files:
            await update.message.reply_text("No files found.", reply_markup=main_keyboard())
            return
        text_msg, kb = _build_file_list(files, _sel(chat_id)["files"], 0, total)
        await update.message.reply_text(text_msg, reply_markup=kb)

    elif text == BTN_PROXIES:
        chat_id = update.effective_chat.id
        _clear(chat_id, "proxies")
        proxies = await sb_get("scraper_proxies", {"select": "id,host,port,is_active,last_ok", "order": "id.desc"})
        if not proxies:
            await update.message.reply_text("No proxies in the pool.", reply_markup=main_keyboard())
            return
        text_msg, kb = _build_proxy_list(proxies, _sel(chat_id)["proxies"])
        await update.message.reply_text(text_msg, reply_markup=kb)

    elif text == BTN_SCRAPER:
        await send_scraper_menu(update, context)

    elif text == BTN_VERSION:
        await send_version_menu(update, context)

    elif text == BTN_HELP:
        await update.message.reply_text(HELP_TEXT, reply_markup=main_keyboard(), parse_mode="Markdown")

# ─── Scraper / Version menus (unchanged) ───────────────────────

async def send_scraper_menu(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    channels = await get_channels()
    ch_list = "\n".join([f"  \u2022 @{c}" for c in channels]) if channels else "  (none)"
    text = (
        "*\U0001f9ea Scraper control*\n\n"
        "The scraper pulls new VPN configs from the channels below.\n\n"
        f"*Channels ({len(channels)})*\n{ch_list}"
    )
    kb = InlineKeyboardMarkup([
        [
            InlineKeyboardButton("\u2795 Add proxy", callback_data="scraper:addproxy"),
            InlineKeyboardButton("\U0001f5d1 Remove proxy", callback_data="scraper:delproxy"),
        ],
        [InlineKeyboardButton("\U0001f4cb Proxy pool", callback_data="scraper:listproxy")],
        [
            InlineKeyboardButton("\u2795 Add channel", callback_data="scraper:addchannel"),
            InlineKeyboardButton("\U0001f5d1 Remove channel", callback_data="scraper:delchannel"),
        ],
        [InlineKeyboardButton("\U0001f4cb Channels", callback_data="scraper:listchannel")],
        [InlineKeyboardButton("\u25b6\ufe0f Run scrape", callback_data="scraper:scrape")],
        [InlineKeyboardButton(BTN_MENU, callback_data="menu")],
    ])
    await update.message.reply_text(text, reply_markup=kb, parse_mode="Markdown")

async def send_version_menu(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    text = (
        "*\U0001f4f0 Version management*\n\n"
        "`/version` show config\n"
        "`/setmin X.Y.Z` set minimum\n"
        "`/setlatest X.Y.Z` set latest\n"
        "`/setbuild N` set build number\n"
        "`/forceupdate on|off` toggle force"
    )
    kb = InlineKeyboardMarkup([
        [InlineKeyboardButton("\U0001f4f0 Show config", callback_data="version:show")],
        [
            InlineKeyboardButton("\u2b07\ufe0f Set min", callback_data="version:setmin"),
            InlineKeyboardButton("\u2b06\ufe0f Set latest", callback_data="version:setlatest"),
        ],
        [
            InlineKeyboardButton("\U0001f522 Set build", callback_data="version:setbuild"),
            InlineKeyboardButton("\U0001f504 Force update", callback_data="version:forceupdate"),
        ],
        [InlineKeyboardButton(BTN_MENU, callback_data="menu")],
    ])
    await update.message.reply_text(text, reply_markup=kb, parse_mode="Markdown")

# ─── Callback handler ──────────────────────────────────────────

async def callback_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    data = query.data
    uid = query.from_user.id
    chat_id = query.message.chat_id if query.message else 0

    if not is_admin(uid):
        await query.answer("Admin only.", show_alert=True)
        return

    # ── Menu ──
    if data == "menu":
        await query.answer()
        _clear(chat_id)
        servers_n = await sb_count("servers")
        files_n = await sb_count("vpn_files")
        channels = await get_channels()
        text = (
            f"\U0001f310 *VlessHub Bot*\n\n"
            f"\U0001f5a5 Links: {servers_n}\n"
            f"\U0001f4c4 Files: {files_n}\n"
            f"\U0001f9ea Channels: {len(channels)}"
        )
        await query.edit_message_text(text, reply_markup=inline_menu_button(), parse_mode="Markdown")
        return

    if data == "noop":
        await query.answer()
        return

    # ── Servers multi-select ──
    if data.startswith("srv:toggle:"):
        await query.answer()
        parts = data.split(":")
        sid = int(parts[2])
        page = int(parts[3])
        _toggle(chat_id, "servers", sid)
        # Re-fetch current page and rebuild
        total = await sb_count("servers")
        offset = page * PAGE_SIZE
        servers = await sb_get("servers", {"select": "id,name,flag", "order": "id.desc", "limit": str(PAGE_SIZE), "offset": str(offset)})
        text_msg, kb = _build_server_list(servers, _sel(chat_id)["servers"], page, total)
        await query.edit_message_text(text_msg, reply_markup=kb)
        return

    if data.startswith("srv:selectall:"):
        await query.answer()
        page = int(data.split(":")[2])
        offset = page * PAGE_SIZE
        servers = await sb_get("servers", {"select": "id", "order": "id.desc", "limit": str(PAGE_SIZE), "offset": str(offset)})
        _sel(chat_id)["servers"] = {s["id"] for s in servers}
        total = await sb_count("servers")
        servers_full = await sb_get("servers", {"select": "id,name,flag", "order": "id.desc", "limit": str(PAGE_SIZE), "offset": str(offset)})
        text_msg, kb = _build_server_list(servers_full, _sel(chat_id)["servers"], page, total)
        await query.edit_message_text(text_msg, reply_markup=kb)
        return

    if data.startswith("srv:deselectall:"):
        await query.answer()
        page = int(data.split(":")[2])
        _clear(chat_id, "servers")
        total = await sb_count("servers")
        offset = page * PAGE_SIZE
        servers = await sb_get("servers", {"select": "id,name,flag", "order": "id.desc", "limit": str(PAGE_SIZE), "offset": str(offset)})
        text_msg, kb = _build_server_list(servers, _sel(chat_id)["servers"], page, total)
        await query.edit_message_text(text_msg, reply_markup=kb)
        return

    if data.startswith("srv:page:"):
        await query.answer()
        page = int(data.split(":")[2])
        _clear(chat_id, "servers")
        total = await sb_count("servers")
        offset = page * PAGE_SIZE
        servers = await sb_get("servers", {"select": "id,name,flag", "order": "id.desc", "limit": str(PAGE_SIZE), "offset": str(offset)})
        text_msg, kb = _build_server_list(servers, _sel(chat_id)["servers"], page, total)
        await query.edit_message_text(text_msg, reply_markup=kb)
        return

    if data == "srv:copy":
        await query.answer()
        sel = _sel(chat_id)["servers"]
        if not sel:
            return
        servers = await sb_get("servers", {"id": f"in.({','.join(map(str, sel))})", "select": "id,name,config"})
        configs = [s["config"] for s in servers if s.get("config")]
        if configs:
            # Send as one message with all configs
            text = "\n\n".join([f"<code>{c}</code>" for c in configs])
            await query.message.reply_text(text, parse_mode="HTML")
        await query.answer(f"Copied {len(configs)} config(s)!", show_alert=False)
        _clear(chat_id, "servers")
        return

    if data == "srv:delete":
        await query.answer()
        sel = _sel(chat_id)["servers"]
        if not sel:
            return
        ids = list(sel)
        deleted = await sb_bulk_delete("servers", ids)
        await query.edit_message_text(
            f"\u2705 Deleted {deleted} server(s).",
            reply_markup=inline_menu_button(),
        )
        _clear(chat_id, "servers")
        return

    if data == "srv:delall":
        await query.answer()
        await query.edit_message_text(
            "\u26a0\ufe0f Delete ALL servers? This cannot be undone.",
            reply_markup=InlineKeyboardMarkup([
                [InlineKeyboardButton("\u2705 Yes, delete all", callback_data="srv:confirm_delall")],
                [InlineKeyboardButton("\u274c Cancel", callback_data="menu")],
            ]),
        )
        return

    if data == "srv:confirm_delall":
        await query.answer()
        ok = await sb_delete("servers", {"id": "gte.0"})
        await query.edit_message_text(
            "\u2705 All servers deleted." if ok else "\u274c Failed.",
            reply_markup=inline_menu_button(),
        )
        return

    # ── VPN Files multi-select ──
    if data.startswith("file:toggle:"):
        await query.answer()
        parts = data.split(":")
        fid = int(parts[2])
        page = int(parts[3])
        _toggle(chat_id, "files", fid)
        total = await sb_count("vpn_files")
        offset = page * PAGE_SIZE
        files = await sb_get("vpn_files", {
            "select": "id,filename,size_bytes,source_channel,is_encrypted",
            "order": "id.desc", "limit": str(PAGE_SIZE), "offset": str(offset),
        })
        text_msg, kb = _build_file_list(files, _sel(chat_id)["files"], page, total)
        await query.edit_message_text(text_msg, reply_markup=kb)
        return

    if data.startswith("file:selectall:"):
        await query.answer()
        page = int(data.split(":")[2])
        offset = page * PAGE_SIZE
        files = await sb_get("vpn_files", {"select": "id", "order": "id.desc", "limit": str(PAGE_SIZE), "offset": str(offset)})
        _sel(chat_id)["files"] = {f["id"] for f in files}
        total = await sb_count("vpn_files")
        files_full = await sb_get("vpn_files", {"select": "id,filename,size_bytes,source_channel,is_encrypted", "order": "id.desc", "limit": str(PAGE_SIZE), "offset": str(offset)})
        text_msg, kb = _build_file_list(files_full, _sel(chat_id)["files"], page, total)
        await query.edit_message_text(text_msg, reply_markup=kb)
        return

    if data.startswith("file:deselectall:"):
        await query.answer()
        page = int(data.split(":")[2])
        _clear(chat_id, "files")
        total = await sb_count("vpn_files")
        offset = page * PAGE_SIZE
        files = await sb_get("vpn_files", {"select": "id,filename,size_bytes,source_channel,is_encrypted", "order": "id.desc", "limit": str(PAGE_SIZE), "offset": str(offset)})
        text_msg, kb = _build_file_list(files, _sel(chat_id)["files"], page, total)
        await query.edit_message_text(text_msg, reply_markup=kb)
        return

    if data.startswith("file:page:"):
        await query.answer()
        page = int(data.split(":")[2])
        _clear(chat_id, "files")
        total = await sb_count("vpn_files")
        offset = page * PAGE_SIZE
        files = await sb_get("vpn_files", {
            "select": "id,filename,size_bytes,source_channel,is_encrypted",
            "order": "id.desc", "limit": str(PAGE_SIZE), "offset": str(offset),
        })
        text_msg, kb = _build_file_list(files, _sel(chat_id)["files"], page, total)
        await query.edit_message_text(text_msg, reply_markup=kb)
        return

    if data == "file:download":
        await query.answer("Preparing downloads...")
        sel = _sel(chat_id)["files"]
        if not sel:
            return
        files = await sb_get("vpn_files", {"id": f"in.({','.join(map(str, sel))})", "select": "id,filename,content"})
        sent = 0
        for f in files:
            if f.get("content"):
                try:
                    raw = base64.b64decode(f["content"])
                    bio = BytesIO(raw)
                    bio.name = f.get("filename", "config.npv")
                    await query.message.reply_document(document=bio, filename=f["filename"])
                    sent += 1
                except Exception:
                    pass
        await query.answer(f"Downloaded {sent} file(s)!", show_alert=False)
        _clear(chat_id, "files")
        return

    if data == "file:delete":
        await query.answer()
        sel = _sel(chat_id)["files"]
        if not sel:
            return
        ids = list(sel)
        deleted = await sb_bulk_delete("vpn_files", ids)
        await query.edit_message_text(
            f"\u2705 Deleted {deleted} file(s).",
            reply_markup=inline_menu_button(),
        )
        _clear(chat_id, "files")
        return

    # ── Proxies multi-select ──
    if data.startswith("proxy:toggle:"):
        await query.answer()
        pid = int(data.split(":")[2])
        _toggle(chat_id, "proxies", pid)
        proxies = await sb_get("scraper_proxies", {"select": "id,host,port,is_active,last_ok", "order": "id.desc"})
        text_msg, kb = _build_proxy_list(proxies, _sel(chat_id)["proxies"])
        await query.edit_message_text(text_msg, reply_markup=kb)
        return

    if data == "proxy:selectall":
        await query.answer()
        proxies = await sb_get("scraper_proxies", {"select": "id", "order": "id.desc"})
        _sel(chat_id)["proxies"] = {p["id"] for p in proxies}
        proxies_full = await sb_get("scraper_proxies", {"select": "id,host,port,is_active,last_ok", "order": "id.desc"})
        text_msg, kb = _build_proxy_list(proxies_full, _sel(chat_id)["proxies"])
        await query.edit_message_text(text_msg, reply_markup=kb)
        return

    if data == "proxy:deselectall":
        await query.answer()
        _clear(chat_id, "proxies")
        proxies = await sb_get("scraper_proxies", {"select": "id,host,port,is_active,last_ok", "order": "id.desc"})
        text_msg, kb = _build_proxy_list(proxies, _sel(chat_id)["proxies"])
        await query.edit_message_text(text_msg, reply_markup=kb)
        return

    if data == "proxy:copy":
        await query.answer()
        sel = _sel(chat_id)["proxies"]
        if not sel:
            return
        proxies = await sb_get("scraper_proxies", {"id": f"in.({','.join(map(str, sel))})", "select": "host,port,secret"})
        if proxies:
            # Send each proxy as an inline button that opens directly in Telegram
            rows = []
            for p in proxies:
                link = f"https://t.me/proxy?server={p['host']}&port={p['port']}&secret={p.get('secret', '')}"
                rows.append([InlineKeyboardButton(f"{p['host']}:{p['port']}", url=link)])
            rows.append([InlineKeyboardButton(BTN_MENU, callback_data="menu")])
            await query.message.reply_text(
                f"\U0001f310 Tap a proxy to apply it in Telegram:",
                reply_markup=InlineKeyboardMarkup(rows),
            )
        await query.answer(f"{len(proxies)} proxy link(s) ready!", show_alert=False)
        _clear(chat_id, "proxies")
        return

    if data == "proxy:delete":
        await query.answer()
        sel = _sel(chat_id)["proxies"]
        if not sel:
            return
        ids = list(sel)
        deleted = await sb_bulk_delete("scraper_proxies", ids)
        await query.edit_message_text(
            f"\u2705 Deleted {deleted} proxy/proxies.",
            reply_markup=inline_menu_button(),
        )
        _clear(chat_id, "proxies")
        return

    # ── Scraper sub-handlers ──
    if data.startswith("scraper:"):
        action = data.split(":")[1]
        if action == "scrape":
            await query.answer()
            if not GITHUB_TOKEN or not GITHUB_REPO:
                await query.edit_message_text("\u274c GitHub not configured.", reply_markup=inline_menu_button())
                return
            await query.edit_message_text("\U0001f680 Triggering scrape...")
            url = f"https://api.github.com/repos/{GITHUB_REPO}/actions/workflows/{GITHUB_WORKFLOW}/dispatches"
            headers = {"Authorization": f"Bearer {GITHUB_TOKEN}", "Accept": "application/vnd.github+json"}
            async with httpx.AsyncClient(timeout=30) as c:
                r = await c.post(url, json={"ref": "master"}, headers=headers)
            if r.status_code == 204:
                await query.edit_message_text("\u2705 Scrape triggered!", reply_markup=inline_menu_button())
            else:
                await query.edit_message_text(f"\u274c Failed: HTTP {r.status_code}", reply_markup=inline_menu_button())
        elif action == "listproxy":
            await query.answer()
            proxies = await sb_get("scraper_proxies", {"select": "id,host,port,source,is_active,last_ok", "order": "id.desc"})
            _clear(chat_id, "proxies")
            if not proxies:
                await query.edit_message_text("Proxy pool is empty.", reply_markup=inline_menu_button())
                return
            text_msg, kb = _build_proxy_list(proxies, _sel(chat_id)["proxies"])
            await query.edit_message_text(text_msg, reply_markup=kb)
        elif action == "listchannel":
            await query.answer()
            channels = await get_channels()
            text = f"*Channels ({len(channels)})*\n\n" + "\n".join([f"@{c}" for c in channels]) if channels else "No channels configured."
            kb = InlineKeyboardMarkup([
                [InlineKeyboardButton("\u2795 Add channel", callback_data="scraper:addchannel")],
                [InlineKeyboardButton("\U0001f5d1 Remove channel", callback_data="scraper:delchannel")],
                [InlineKeyboardButton(BTN_MENU, callback_data="menu")],
            ])
            await query.edit_message_text(text, reply_markup=kb, parse_mode="Markdown")
        elif action in ("addproxy", "delproxy", "addchannel", "delchannel"):
            await query.answer()
            prompts = {
                "addproxy": "Send a proxy link:\n`https://t.me/proxy?server=..&port=..&secret=..`",
                "delproxy": "Send the proxy host or id to remove, or `all`.",
                "addchannel": "Send the channel username:\n`@channel_name`",
                "delchannel": "Send the channel username to remove.",
            }
            await query.edit_message_text(prompts[action], parse_mode="Markdown",
                reply_markup=inline_menu_button())
        return

    # ── Version sub-handlers ──
    if data.startswith("version:"):
        await query.answer()
        action = data.split(":")[1]
        if action == "show":
            await query.edit_message_text(
                "*\U0001f4f0 Version config*\n\nUse `/version` command to see full config.",
                reply_markup=inline_menu_button(), parse_mode="Markdown",
            )
        return

    await query.answer()

# ─── Slash commands ────────────────────────────────────────────

async def cmd_scrape(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.message or not update.effective_user:
        return
    if not is_admin(update.effective_user.id):
        await update.message.reply_text("Admin only.")
        return
    if not GITHUB_TOKEN or not GITHUB_REPO:
        await update.message.reply_text("\u274c GitHub not configured.", reply_markup=main_keyboard())
        return
    await update.message.reply_text("\U0001f680 Triggering scrape...")
    url = f"https://api.github.com/repos/{GITHUB_REPO}/actions/workflows/{GITHUB_WORKFLOW}/dispatches"
    headers = {"Authorization": f"Bearer {GITHUB_TOKEN}", "Accept": "application/vnd.github+json"}
    async with httpx.AsyncClient(timeout=30) as c:
        r = await c.post(url, json={"ref": "master"}, headers=headers)
    if r.status_code == 204:
        await update.message.reply_text("\u2705 Scrape triggered!", reply_markup=main_keyboard())
    else:
        await update.message.reply_text(f"\u274c Failed: HTTP {r.status_code}", reply_markup=main_keyboard())

async def cmd_version(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.message:
        return
    servers_n = await sb_count("servers")
    files_n = await sb_count("vpn_files")
    proxies_n = await sb_count("scraper_proxies")
    channels = await get_channels()
    text = (
        f"*VlessHub Bot* v{BOT_VERSION}\n\n"
        f"Links: {servers_n}\nFiles: {files_n}\nProxies: {proxies_n}\nChannels: {len(channels)}\n\n"
        f"Supabase: {'\u2705' if SUPABASE_URL and SUPABASE_KEY else '\u274c'}\n"
        f"GitHub: {'\u2705' if GITHUB_TOKEN and GITHUB_REPO else '\u274c'}"
    )
    await update.message.reply_text(text, reply_markup=main_keyboard(), parse_mode="Markdown")

async def cmd_myid(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.message or not update.effective_user:
        return
    await update.message.reply_text(f"Your Telegram user ID: `{update.effective_user.id}`", parse_mode="Markdown")

async def cmd_stats(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.message:
        return
    total = await sb_count("servers")
    await update.message.reply_text(f"Active servers in DB: {total}", reply_markup=main_keyboard())

# ─── Main ──────────────────────────────────────────────────────

def main() -> None:
    if not BOT_TOKEN:
        log.error("BOT_TOKEN is required")
        sys.exit(1)

    log.info("Starting VlessHub Bot v" + BOT_VERSION)

    import asyncio as _asyncio
    try:
        _asyncio.get_event_loop()
    except RuntimeError:
        _asyncio.set_event_loop(_asyncio.new_event_loop())

    app = Application.builder().token(BOT_TOKEN).build()

    app.add_handler(CommandHandler("start", cmd_start))
    app.add_handler(CommandHandler("scrape", cmd_scrape))
    app.add_handler(CommandHandler("version", cmd_version))
    app.add_handler(CommandHandler("myid", cmd_myid))
    app.add_handler(CommandHandler("stats", cmd_stats))

    app.add_handler(CallbackQueryHandler(callback_handler))
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_main_keyboard))

    log.info("Bot is running. Press Ctrl+C to stop.")
    app.run_polling(allowed_updates=Update.ALL_TYPES)

if __name__ == "__main__":
    main()
