# vlesshub-bot (Supabase Edge Function)

Telegram bot for VlessHub — manages channels, triggers scraping, handles contact requests. Deployed as a Supabase Edge Function in **webhook mode** (no server, no long-poll, no downtime).

## Why Webhook Mode

Edge functions can't run Telegram's long-poll loop. Instead, Telegram POSTs updates to the function URL and the bot answers via the Bot API. State is persisted in Postgres (`vlesshub_bot_state` table).

## Commands

### Admin Commands

| Command | Description |
|---------|-------------|
| `/start` | Main menu: Links, MTProto, Files, Messages, Scraper |
| `/scrape` | Dispatch GitHub Actions workflow on ahmad43ir/Vlesshub repo |

### Admin Menu Flows

**Links / MTProto / Files:**
1. Bot shows paginated list (10 per page) with checkboxes
2. Admin taps items to select/deselect
3. "✅ Done" → shows action buttons: Copy, Export, Remove
4. Copy/Export sends configs to admin chat; Remove deletes from DB

**Messages:**
1. Shows list of contact requests (⏳ pending / ✅ approved)
2. Per user: Approve (or Reply once approved) + Remove
3. Reply: admin types message → delivered to user in-bot

### Public User Flow (Contact Us)

1. User taps "Contact Us" in /start menu
2. Bot creates `vlesshub_contact` row (status: pending)
3. Admin notified with inline buttons: [✅ Approve] [🗑 Remove]
4. Once approved, user can type up to 50-word messages
5. Admin sees messages and can reply

## Files

| File | Purpose |
|------|---------|
| `index.ts` | Entry: webhook routing, admin endpoints, health check |
| `_handlers.ts` | All message/callback/command handlers, menus, contact flow |
| `_db.ts` | Supabase data-plane (servers, files, proxies, channels, contacts) |
| `_state.ts` | Per-chat selection state + pending input + webhook secret |
| `_telegram.ts` | Minimal Telegram Bot API client (fetch-based) |
| `_utils.ts` | JSON response, CORS, logging, markdown escape |

## Data Plane

Reads/writes Supabase tables directly via service-role client:

| Table | Purpose |
|-------|---------|
| `servers` | Imported VPN configs (VLESS/VMess/Trojan/SS) |
| `vpn_files` | Raw config files for download |
| `scraper_proxies` | MTProto proxy pool (also read by scraper) |
| `scraper_config` | `vless_channels` list |
| `vlesshub_bot_state` | Per-chat selection + pending input state |
| `vlesshub_contact` | Contact request messages |

## Deploy

```bash
cd rootnet-vpn
npx supabase functions deploy vlesshub-bot --project-ref bprkazfxqmanrybiexnh
```

## Secrets

| Secret | Purpose |
|--------|---------|
| `BOT_TOKEN` | Telegram bot token for sending messages |
| `GH_PAT` | GitHub fine-grained PAT (Actions: read & write on ahmad43ir/Vlesshub) |
| `GH_REPO` | Repository name (defaults to `ahmad43ir/Vlesshub`) |
| `GH_REF` | Branch name (defaults to `main`) |

## Scraper Integration

The `/scrape` command dispatches a GitHub Actions workflow:

```typescript
POST https://api.github.com/repos/{GH_REPO}/actions/workflows/scrape.yml/dispatches
Headers: Authorization: Bearer {GH_PAT}
Body: { ref: GH_REF }
```

The scraper runs in `RUN_ONCE` mode, reports results back to admin chat via `BOT_TOKEN`.

## Safety Rules

- Never send messages from the user account (`@rootnet_vpn_manager`) — use bot only
- Never regenerate `TELEGRAM_SESSION`
- Never run scraper twice simultaneously
- Honor all `FloodWaitError` responses
- ≤30 messages/channel, ≤5-8 channels per scrape run
- ≤15 proxy tests per run, concurrency ≤4
