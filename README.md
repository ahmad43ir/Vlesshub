# VlessHub

Free VPN config hub — VLESS links, MTProto proxies, and VPN config files scraped from public Telegram channels.

## Download

⬇️ **[Latest Release](https://github.com/ahmad43ir/VlessHub/releases/latest)**

Install the APK on Android 6.0+ (API 23+).

## Apps

| App | Description |
|-----|-------------|
| [`app/`](app/) | **VlessHub** — user-facing app with Links, MTProto, Files tabs |
| [`admin-app/`](admin-app/) | **Admin VlessHub** — admin app with OTP login + server remove |

## Architecture

```
VlessHub/
├── app/                  # Main VlessHub Android app (Kotlin + Compose)
├── admin-app/            # Admin app (OTP login, remove servers/proxies/files)
├── api/                  # Cloudflare Worker backend (vlesshub-api)
├── scraper/              # Telegram scraper (Python + Telethon)
├── telegram-bot/         # Telegram bot for channel management
└── .github/workflows/    # CI/CD — auto-build APK, release workflow
```

## Backend

- **Cloudflare Worker** (`api/`) — data API for servers, proxies, files
- **Supabase** — database (servers, vpn_files, scraper_proxies)
- **Resend** — email delivery for admin OTP login

## Build

```bash
cd app
./gradlew :app:assembleDebug
```

## Release

```bash
git tag v0.2.0
git push origin v0.2.0
```

This triggers the GitHub Actions release workflow → APK published to [Releases](https://github.com/ahmad43ir/VlessHub/releases).

## License

See [LICENSE](LICENSE).
