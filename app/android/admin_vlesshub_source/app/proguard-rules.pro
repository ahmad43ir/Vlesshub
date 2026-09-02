# Admin VlessHub — no ad SDKs, no special keep rules needed.
# OkHttp / Okio are safe with defaults.

# ── libv2ray (AndroidLibXrayLite) — JNI bindings must survive R8 ──────────
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-keep class go.Seq { *; }
-keep class go.Seq$* { *; }
