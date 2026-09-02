# VlessHub

Free VLESS VPN config aggregator — scrapes Telegram channels, serves configs to users via Android app + PWA, with a Telegram bot for management and a scraper for automated collection.

## Architecture

```
Telegram Channels (VLESS configs + MTProto proxies)
       ↓ (Telethon listener)
  Scraper (ahmad43ir/Vlesshub repo, GitHub Actions cron)
       ↓ (Supabase REST)
  Supabase DB (servers, vpn_files, scraper_proxies, vlesshub_contact)
       ↓ (public REST)
  VlessHub Android App
  VlessHub PWA (mobile web app)
  Admin Android App
  vlesshub-bot (Supabase Edge Function)
```

## Components

| Component | Path | Description |
|-----------|------|-------------|
| **VlessHub App** | `vlesshub-app/` | Android app source (archived — not built; see PWA note below) |
| **VlessHub PWA** | `pages-site/vlesshub/` | Mobile web app — same features as Android, installable on iOS + Android |
| **Admin App** | `admin_vlesshub_source/` | Admin companion — manage links/files/proxies with Hide/Remove, Xray config testing |
| **Bot** | `rootnet-vpn/supabase/functions/vlesshub-bot/` | Supabase Edge Function — Telegram bot for channel management, scraping, contact messages |
| **Scraper** | `ahmad43ir/Vlesshub` (separate repo, branch `main`) | Python Telethon bot — watches channels, extracts configs, tests proxies, uploads files |
| **Shared Backend** | `rootnet-vpn/supabase/functions/rootnet-api/` | Shared Supabase Edge Function (servers, version gate, geo-api) |

## Proxy Workers

| Worker | URL | Purpose |
|--------|-----|---------|
| `vlesshub-proxy` | `vlesshub-proxy.mobileahmad43-a18.workers.dev` | Proxies the PWA from chobgroup.pages.dev (hides origin) |
| `yektanet-proxy` | `yektanet-proxy.mobileahmad43-a18.workers.dev` | Proxies Yektanet CDN scripts (bypasses Iran block) |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Android App | Kotlin, Jetpack Compose, Material 3, Adivery ads |
| PWA | Vanilla HTML/CSS/JS, Service Worker, Yektanet ads |
| Backend | Supabase Edge Functions (Deno/TypeScript), PostgreSQL |
| Scraper | Python 3, Telethon, GitHub Actions |
| Bot | TypeScript (webhook mode, no long-poll) |
| Build | Gradle 9.1, AGP, R8 minification, signed release |
| Proxy | Cloudflare Workers |

## Supabase

- **Project:** `bprkazfxqmanrybiexnh`
- **URL:** `https://bprkazfxqmanrybiexnh.supabase.co`
- **Tables:** `servers`, `vpn_files`, `scraper_proxies`, `scraper_config`, `vlesshub_bot_state`, `vlesshub_contact`, `app_config`
- **Edge Functions:** `rootnet-api`, `geo-api`, `vlesshub-bot`, `proxy-api`
- **Storage Buckets:** `downloads` (public), `project-archives` (private)

---

## Android App (vlesshub-app)

Package: `com.chobgroup.vlesshub` · Version: `0.2.2` · Min SDK: 23 · Target SDK: 36

### Tabs

| Tab | Description |
|-----|-------------|
| **MTProto** | Random batches of 10 Telegram MTProto proxies — Copy/Share/Open in Telegram |
| **Links** | VLESS/V2Ray server configs — Copy/Export, TCPing, Sort by ping, Pagination (10/page) |
| **Files** | VPN config files (.npvt, .sip, .npv) — Download with progress, Open with chooser |
| **Settings** | Contact Us, Privacy, Chob Group website |

### Ad Model (Adivery — interstitial only, no rewarded video)

| Trigger | Action |
|---------|--------|
| Every 5th distinct Copy/Export tap | Interstitial ad, then action completes |
| Every 3rd completed file download | Interstitial ad, then next download starts |
| Refresh button | Interstitial ad, then refresh runs |
| More button (pagination) | Interstitial ad, then next page loads |

60-second app-wide cooldown between interstitials. If ad not ready, action proceeds without ad (no lockout).

### Key Files

```
app/src/main/java/com/chobgroup/vlesshub/
├── ads/AdiveryAdsManager.kt          # Interstitial-only ad manager
├── config/ConfigNormalizer.kt        # Parse VLESS/VMess/Trojan/SS URIs
├── core/theme/Color.kt, Theme.kt     # Dark cyber-organic theme (neon green #4CFF88)
├── data/
│   ├── AppConstants.kt               # Supabase URLs, API endpoints, Adivery IDs
│   ├── ProxyApi.kt                   # Fetch MTProto proxies
│   ├── model/                        # VpnServer, VpnFile, ProxyItem, UnifiedConfig
│   ├── remote/                       # GeoIpResolver, PinnedHttpClient, VersionCheckApi
│   └── repository/                   # RemoteServerRepository, RemoteVpnFileRepository, ServerCacheStore
├── MainActivity.kt                   # Entry, version gate, Adivery init
├── ui/
│   ├── HomeScreen.kt                 # MTProto tab
│   ├── LibraryScreens.kt             # Links + Files tabs
│   ├── MainShellScreen.kt            # Bottom nav
│   └── icons/AppIcons.kt             # Custom icon set
└── util/                             # ConfigActions, DownloadStorage, TimeFormat
```

### Build

```bash
cd vlesshub/vlesshub-app
> **ℹ️ DISTRIBUTION NOTE:** VlessHub ships as a **PWA only** — https://vlesshub-2i2.pages.dev
> (mirror: https://vlesshub-proxy.mobileahmad43-a18.workers.dev). No APKs are built or
> published; all GitHub releases have been removed. The Android source above is kept
> for reference. Build locally if you ever need it:
>
> ```bash
> cd app/android/vlesshub-app
> ./gradlew.bat :app:assembleRelease
> ```
./gradlew.bat :app:testDebugUnitTest  # Unit tests
```

### Key Behaviors

- **Cache-first loading:** cached servers shown instantly, Supabase hit only on first run or refresh
- **TCPing:** real TLS handshake for TLS/Reality configs, plain TCP for others; 5s timeout
- **Sort by ping:** toggle from menu; reachable first (ascending ms), untested/failed after; persistent, reset on refresh
- **Pagination:** 10 links/page, 5 files/page; persisted in SharedPreferences; refresh resets
- **Hidden configs:** per-device hidden set in SharedPreferences; "Restore hidden" in menu
- **Version gate:** checks `app_config` at startup; blocks if versionName < minimum_version
- **Contact messages:** users request contact via bot; admin approves and chats in-bot

---

## PWA (pages-site/vlesshub/)

Mobile-only web app — same features as Android, installable on iOS (Safari → Add to Home Screen) and Android (Chrome → Install app).

### URLs

| URL | Purpose |
|-----|---------|
| `chobgroup.pages.dev/vlesshub/` | Origin (Cloudflare Pages) |
| `vlesshub-proxy.mobileahmad43-a18.workers.dev` | Proxy (clean URL, hides origin) |

### Features

| Feature | Details |
|---------|---------|
| **MTProto tab** | Proxy list, Copy/Share/Open in Telegram |
| **Links tab** | VLESS configs, Copy/Export, Sort by ping, Pagination (10/page) |
| **Files tab** | VPN files, Download, Format display |
| **Dark theme** | Same cyber-organic design as Android app |
| **Service worker** | Offline caching for static assets |
| **Version gate** | Blocks outdated installs via `app_config` |
| **Install prompt** | Banner on supported browsers |
| **Mobile-only** | Desktop shows "open on phone" message |

### Ad Model (Yektanet — full-screen interstitial)

| Trigger | Action |
|---------|--------|
| Every 3rd action (copy/share/download/more/refresh) | Full-screen ad overlay, auto-closes after 8s |

Ad loaded via Yektanet analytics script (`yn_pub.js`) proxied through `yektanet-proxy` worker. Placement ID: `122583`.

### Files

```
pages-site/vlesshub/
├── index.html        # Complete PWA (HTML + CSS + JS, single file)
├── manifest.json     # PWA manifest (installable)
├── sw.js             # Service worker (cache-first for static, network-first for API)
└── icons/
    ├── icon-192.png  # App icon 192x192
    └── icon-512.png  # App icon 512x512
```

### Deploy

```bash
cd pages-site
npx wrangler pages deploy . --project-name chobgroup --branch main
```

---

## Proxy Workers

### vlesshub-proxy

Proxies the PWA from `chobgroup.pages.dev/vlesshub/` through a clean Workers domain.

```bash
cd yektanet-proxy   # or vlesshub-proxy
npx wrangler deploy
```

### yektanet-proxy

Proxies Yektanet CDN scripts (`cdn.yektanet.com`) to bypass Iran blocks.

```
vlesshub-proxy.mobileahmad43-a18.workers.dev  →  chobgroup.pages.dev/vlesshub/
yektanet-proxy.mobileahmad43-a18.workers.dev  →  cdn.yektanet.com/...
```

---

## Admin App (admin_vlesshub_source)

Package: `com.chobgroup.admin_vlesshub` · Same tech stack, no ads, red accent theme.

### Tabs

| Tab | Description |
|-----|-------------|
| **Links** | VLESS configs — Co
