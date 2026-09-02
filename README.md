# VlessHub

Everything VlessHub in one repo — scraper, apps, PWA, backend bot.

| Folder | What it is |
|---|---|
| `main.py`, `proxy_pool.py`, `cleanup_chats.py`, `create_session.py` | Telegram scraper (GitHub Actions: `.github/workflows/scrape.yml`) |
| `app/android/vlesshub-app/` | Android user app source (archived — not built; **PWA is the only distribution**) |
| `app/android/admin_vlesshub_source/` | Android admin companion app |
| `app/pwa/vlesshub/` | **PWA — the only distribution** (Cloudflare Pages: vlesshub-2i2.pages.dev) |
| `app/proxy-worker/vlesshub-proxy/` | Cloudflare Worker reverse proxy for the PWA |
| `bot/vlesshub-bot/` | Supabase Edge Function Telegram bot |
| `migrations/` | Supabase SQL migrations |
| `docs/` | App READMEs |

## Distribution

**VlessHub is PWA-only.** Users open/install https://vlesshub-2i2.pages.dev — no APK.

- Mirror for blocked regions: https://vlesshub-proxy.mobileahmad43-a18.workers.dev
- Legacy link https://chobgroup.pages.dev/vlesshub/ 301-redirects here.
- All GitHub releases + release tags were removed (Sept 2026); the Android APK is no
  longer built or distributed. The `app/android/` source is kept for reference only.
