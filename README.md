# VlessHub

Free VPN config hub — VLESS links, MTProto proxies, and VPN config files scraped from public Telegram channels.

## Download

⬇️ **[Latest Release](https://github.com/ahmad43ir/VlessHub/releases/latest)**

Install the APK on Android 6.0+ (API 23+).

## ⚠️ Source Code Policy (v2.10 and above)

> **Starting with version 2.11, VlessHub will be developed and distributed as closed source.**
>
> This decision was made for security reasons — to protect user privacy and prevent misuse of the codebase. **v2.10 is the last open-source release.** Older open-source releases remain available in the repository; newer versions will be published under this policy.

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

## Features

- **Links** — VLESS/V2Ray config links with TCP ping, copy, and export
- **MTProto** — Free Telegram MTProto proxy batches with copy, share, and open
- **Files** — VPN config files with download and open
- **Admin** — OTP-protected admin app for managing servers, proxies, and files

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
git tag v2.10.0
git push origin v2.10.0
```

This triggers the GitHub Actions release workflow → APK published to [Releases](https://github.com/ahmad43ir/VlessHub/releases).

## Tech Stack

- 🤖 **Android** — Kotlin + Jetpack Compose
- ⚡ **Backend** — Cloudflare Workers (JavaScript)
- 🗄️ **Database** — Supabase (PostgreSQL)
- 📡 **Scraper** — Python + Telethon (Telegram API)
- 📧 **Email** — Resend (OTP delivery)

## License

v2.10 and earlier: MIT License. Versions 2.11+ are proprietary and closed source.

## Support

- 📧 Email: mobileahmad43@gmail.com
- 📢 Telegram: [@VlessHub](https://t.me/VlessHub)
