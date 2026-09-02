# VlessHub

Everything VlessHub in one repo — scraper, apps, PWA, backend bot.

| Folder | What it is |
|---|---|
| `main.py`, `proxy_pool.py`, `cleanup_chats.py`, `create_session.py` | Telegram scraper (GitHub Actions: `.github/workflows/scrape.yml`) |
| `app/android/vlesshub-app/` | Android user app (Kotlin + Compose) |
| `app/android/admin_vlesshub_source/` | Android admin companion app |
| `app/pwa/vlesshub/` | PWA (Cloudflare Pages) |
| `app/proxy-worker/vlesshub-proxy/` | Cloudflare Worker reverse proxy for the PWA |
| `bot/vlesshub-bot/` | Supabase Edge Function Telegram bot |
| `migrations/` | Supabase SQL migrations |
| `docs/` | App READMEs |
