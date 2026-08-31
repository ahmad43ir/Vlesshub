# 🤖 VlessHub Telegram Bot

Manages VLESS channels and dispatches scrape commands for the VlessHub ecosystem.

## Commands

| Command | Description |
|---------|-------------|
| `/start` | Welcome message |
| `/help` | List all commands |
| `/addchannel <name>` | Add a VLESS channel to monitor |
| `/removechannel <name>` | Remove a channel |
| `/listchannels` | List all monitored channels |
| `/scrape` | Trigger a GitHub Actions scrape run |
| `/status` | Show current bot & scraper status |

## Quick Start

```bash
cd telegram-bot
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt

cp .env.example .env
# Edit .env with your credentials

python bot.py
```

## How It Works

```
User sends /addchannel vpn_channel
       ↓
Bot writes to Supabase scraper_config.vless_channels
       ↓
User sends /scrape
       ↓
Bot triggers GitHub Actions workflow_dispatch
       ↓
vless-scraper runs, reads channels from scraper_config
       ↓
Scraped VLESS links → Cloudflare Worker → Supabase → App
```

## Configuration

| Variable | Required | Description |
|----------|----------|-------------|
| `BOT_TOKEN` | ✅ | Telegram bot token from @BotFather |
| `SUPABASE_URL` | ✅ | Supabase project URL |
| `SUPABASE_KEY` | ✅ | Supabase `service_role` key |
| `GITHUB_TOKEN` | ❌ | GitHub PAT for triggering workflows |
| `GITHUB_REPO` | ❌ | `owner/repo` format |
| `GITHUB_WORKFLOW` | ❌ | Workflow filename (default: `vless-scraper.yml`) |
| `AUTHORIZED_USERS` | ❌ | Comma-separated user IDs (empty = anyone) |

## Deployment

### Railway (recommended)
```bash
railway login
railway init
railway variables set BOT_TOKEN=... SUPABASE_URL=... SUPABASE_KEY=...
railway up
```

### Fly.io
```bash
fly launch
fly secrets set BOT_TOKEN=... SUPABASE_URL=... SUPABASE_KEY=...
fly deploy
```

### GitHub Actions (self-hosted runner)
Run as a long-polling process on a VPS or self-hosted runner.
