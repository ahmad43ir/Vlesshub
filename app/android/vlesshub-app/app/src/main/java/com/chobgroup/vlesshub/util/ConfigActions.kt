package com.chobgroup.vlesshub.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * v2.0 config-launcher actions — the app no longer runs a VPN engine. It hands
 * configs to the user: copy to the clipboard, or open them in whatever client
 * app is installed (v2rayNG, NekoBox, Hiddify, ...) via Android's default
 * scheme resolution (vless://, vmess://, trojan://, ss://, ...).
 */
object ConfigActions {

    /** True when the raw config looks like a URI a client app can handle. */
    fun isLinkLike(raw: String): Boolean = raw.trim().contains("://")

    /** True when the value is a Telegram MTProto proxy deep link. */
    fun isTelegramProxyLink(raw: String): Boolean =
        raw.trim().startsWith("tg://proxy", ignoreCase = true) ||
            raw.trim().startsWith("https://t.me/proxy", ignoreCase = true) ||
            raw.trim().startsWith("http://t.me/proxy", ignoreCase = true)

    /**
     * Telegram accepts proxy links through its tg:// deep-link handler. Some
     * Android versions/apps do not claim the tg scheme reliably, so normalize
     * the public t.me form to tg:// and target Telegram explicitly when opening.
     */
    fun normalizeTelegramProxyLink(raw: String): String {
        val value = raw.trim()
        return when {
            value.startsWith("tg://proxy", ignoreCase = true) -> value
            value.startsWith("https://t.me/proxy", ignoreCase = true) ->
                "tg://proxy" + value.substringAfter("t.me/proxy")
            value.startsWith("http://t.me/proxy", ignoreCase = true) ->
                "tg://proxy" + value.substringAfter("t.me/proxy")
            else -> value
        }
    }

    /**
     * Patch a VLESS Reality config for client compatibility:
     * - Auto-add `flow=xtls-rprx-vision` when missing (required by v2rayNG,
     *   NekoBox, Hiddify for Reality connections to work).
     * - Does NOT touch non-VLESS or non-Reality configs.
     * Returns the original string unchanged when no patch is needed.
     */
    fun patchConfigForClient(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("vless://", ignoreCase = true)) return trimmed

        val qIdx = trimmed.indexOf('?')
        if (qIdx < 0) return trimmed

        val fragment = trimmed.substring(qIdx + 1)
        val params = parseQueryParams(fragment)

        val security = params["security"]?.lowercase() ?: return trimmed
        if (security != "reality") return trimmed

        // Already has flow — nothing to patch.
        if (params.containsKey("flow")) return trimmed

        // Add flow=xtls-rprx-vision (URL-encoded)
        val encodedFlow = URLEncoder.encode("xtls-rprx-vision", "UTF-8")
        return trimmed.substring(0, qIdx + 1) + fragment + "&flow=$encodedFlow"
    }

    /**
     * Detect whether a config uses a transport that most VPN clients
     * (v2rayNG, NekoBox, Hiddify) don't support. Returns true for xhttp
     * and other non-standard transports.
     */
    fun hasUnsupportedTransport(raw: String): Boolean {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("vless://", ignoreCase = true) &&
            !trimmed.startsWith("vmess://", ignoreCase = true)) return false
        val qIdx = trimmed.indexOf('?')
        if (qIdx < 0) return false
        val params = parseQueryParams(trimmed.substring(qIdx + 1))
        val transport = params["type"]?.lowercase() ?: return false
        // Supported: tcp, grpc, ws, http, httpupgrade.  Unsupported: xhttp, quic, etc.
        return transport !in setOf("tcp", "grpc", "ws", "http", "httpupgrade", "")
    }

    /** Copies the raw config to the system clipboard (with client patches applied). */
    fun copyToClipboard(context: Context, label: String, value: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copied = if (isTelegramProxyLink(value)) normalizeTelegramProxyLink(value) else patchConfigForClient(value)
        cm.setPrimaryClip(ClipData.newPlainText(label, copied))
    }

    /**
     * Opens the config with the default handler for its scheme. Returns `false`
     * when no app can handle it (e.g. no client installed, or the config is not
     * a URI). Never throws. Applies client-compatibility patches before opening.
     */
    fun openWithDefaultApp(context: Context, raw: String): Boolean {
        val telegramProxy = isTelegramProxyLink(raw)
        val value = if (telegramProxy) normalizeTelegramProxyLink(raw) else patchConfigForClient(raw)
        if (!isLinkLike(value)) return false
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            if (telegramProxy) setPackage("org.telegram.messenger")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else if (telegramProxy) {
                // Fallback to Android's resolver for Telegram variants such as
                // Telegram X or a regional build.
                val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (fallback.resolveActivity(context.packageManager) == null) false
                else {
                    context.startActivity(fallback)
                    true
                }
            } else {
                false
            }
        }.getOrDefault(false)
    }

    /** Opens the Play Store page for a client app (falls back to the web URL). */
    fun openPlayStore(context: Context, packageName: String) {
        val intent = runCatching {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            if (market.resolveActivity(context.packageManager) != null) market else null
        }.getOrNull() ?: Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
        )
        runCatching { context.startActivity(intent) }
    }

    /** Simple query-string parser (handles URL-encoded values). */
    private fun parseQueryParams(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (query.isBlank()) return map
        // Strip fragment (#...) if present
        val clean = query.substringBefore('#')
        clean.split("&").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx >= 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                map[key] = value
            }
        }
        return map
    }
}

/**
 * Resolves the host [Activity] even when Compose wraps the context in a
 * [ContextWrapper] (common with themed contexts). Returns null when no
 * Activity is reachable.
 */
fun Context.unwrapActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? Activity
}
