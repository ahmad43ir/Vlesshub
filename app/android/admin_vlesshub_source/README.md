# Admin VlessHub

Admin companion app for managing VlessHub servers, MTProto proxies, and VPN config files. Provides CRUD operations (Hide + Remove) that the user app doesn't have.

Package: `com.chobgroup.admin_vlesshub`

## Features

- **3 tabs:** Links (VLESS/V2Ray configs), MTProto (Telegram proxies), Files (VPN config files)
- **Copy / Export** configs to clipboard or VPN client apps
- **TCPing** to test server reachability (Links tab)
- **Refresh** to pull fresh data from the backend
- **Hide** — hide items locally (per-device, SharedPreferences)
- **Remove** — delete items from the backend via API (requires admin key)
- **Open in Telegram** — open MTProto proxy links directly in Telegram (requires `<queries>` for Android 11+)
- **Xray config testing** — generate and test Xray JSON configs (includes libv2ray.aar + geo assets)
- **No ads** — no Adivery SDK, no ad gating

## Tech Stack

- Native Android (Kotlin + Jetpack Compose)
- Same endpoints as VlessHub (Supabase REST via rootnet-api)
- Red accent theme (vs VlessHub's green)
- R8 minification on release builds

## Admin Key

The Remove button only appears when an admin API key is configured in Settings. The key is sent as an `X-Admin-Key` header on DELETE requests.

Backend endpoints expected:
- `DELETE /servers/{id}` — remove a config
- `DELETE /files/{id}` — remove a VPN file
- `DELETE /proxies/{id}` — remove a proxy

## Build

```bash
cd vlesshub/admin_vlesshub_source
./gradlew.bat :app:assembleDebug      # Debug APK (~144 MB — includes Xray engine)
```

## Key Files

```
app/src/main/java/com/chobgroup/admin_vlesshub/
├── AdminApplication.kt               # App init, HiddenStore init
├── MainActivity.kt                   # Entry, version check, admin key gate
├── config/
│   ├── ConfigNormalizer.kt           # Parse VLESS URIs → normalized config
│   ├── XrayTestManager.kt            # Xray engine integration
│   └── XrayConfigGenerator.kt        # Generate Xray JSON from VLESS URIs
├── data/
│   ├── AdminKeyStore.kt              # SharedPreferences for admin API key
│   ├── HiddenStore.kt                # SharedPreferences for hidden server/file IDs
│   ├── AppConstants.kt               # Supabase URLs, API endpoints
│   ├── ProxyApi.kt                   # Fetch MTProto proxies
│   ├── model/                        # VpnServer, VpnFile, ProxyItem, UnifiedConfig
│   └── remote/
│       ├── AdminApi.kt               # DELETE endpoints with X-Admin-Key
│       ├── GeoIpResolver.kt          # IP → country
│       ├── PinnedHttpClient.kt       # Cert-pinned HTTP client
│       └── VersionCheckApi.kt        # Version gate check
├── ui/
│   ├── AdminLinksScreen.kt           # Links with Hide + Remove
│   ├── AdminFilesScreen.kt           # Files with Hide + Remove
│   ├── AdminMTProtoScreen.kt         # MTProto with Hide + Remove + Open
│   ├── AdminMainShellScreen.kt       # Bottom nav
│   ├── AdminSettingsScreen.kt        # Admin key config
│   ├── LoginScreen.kt                # Admin key entry
│   ├── components/AdminWidgets.kt    # Shared UI components
│   └── icons/AdminIcons.kt           # Custom icons (VisibilityOff for Hide)
├── util/ConfigActions.kt             # Copy/open/share actions
└── libs/libv2ray.aar                 # Xray engine (134 MB)
    src/main/assets/geoip.dat         # GeoIP database
    src/main/assets/geosite.dat       # GeoSite database
```

## Differences from User App

| Feature | User App (`vlesshub-app`) | Admin App (`admin_vlesshub_source`) |
|---------|--------------------------|-------------------------------------|
| Package | `com.chobgroup.vlesshub` | `com.chobgroup.admin_vlesshub` |
| Ads | Adivery interstitial | None |
| Hide button | No | Yes (local SharedPreferences) |
| Remove button | No | Yes (API DELETE with admin key) |
| Xray testing | No (removed) | Yes (libv2ray.aar + geo assets) |
| Theme | Green (#4CFF88) | Red accent |
| Login | No | Yes (admin key gate) |
| APK size | ~1.6 MB | ~144 MB (Xray engine) |

## APK Size

The admin APK is large (~144 MB) because it includes:
- `libv2ray.aar` (134 MB) — native Xray engine libraries for config testing
- `geoip.dat` + `geosite.dat` (29 MB) — IP geolocation databases

These are NOT needed in the user app (which only copies/exports configs, never runs them).
