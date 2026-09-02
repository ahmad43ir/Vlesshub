package com.chobgroup.admin_vlesshub.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri

/** Actions for handing configuration links to Android and installed clients. */
object ConfigActions {

    /** True when the value is a Telegram MTProto proxy deep link. */
    fun isTelegramProxyLink(raw: String): Boolean =
        raw.trim().startsWith("tg://proxy", ignoreCase = true) ||
            raw.trim().startsWith("https://t.me/proxy", ignoreCase = true) ||
            raw.trim().startsWith("http://t.me/proxy", ignoreCase = true)

    /** Normalize Telegram's public t.me proxy URL to its native deep-link form. */
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

    fun copyToClipboard(context: Context, label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(label, normalizeTelegramProxyLink(value)),
        )
    }

    /** Open a Telegram proxy in the installed Telegram app, with resolver fallback. */
    fun openTelegramProxy(context: Context, raw: String): Boolean {
        val uri = runCatching {
            Uri.parse(normalizeTelegramProxyLink(raw))
        }.getOrNull() ?: return false
        val explicit = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("org.telegram.messenger")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            if (explicit.resolveActivity(context.packageManager) != null) {
                context.startActivity(explicit)
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            true
        }.getOrDefault(false)
    }
}

fun Context.unwrapActivity(): android.app.Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return current as? android.app.Activity
}
