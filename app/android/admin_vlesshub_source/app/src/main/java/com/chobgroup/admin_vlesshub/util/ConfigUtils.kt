package com.chobgroup.admin_vlesshub.util

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Client-compatibility config utilities for the admin app.
 * Mirrors VlessHub's ConfigActions helpers.
 */
object ConfigUtils {

    /**
     * Patch a VLESS Reality config: auto-add `flow=xtls-rprx-vision` when
     * missing. Required by v2rayNG, NekoBox, Hiddify for Reality connections.
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
        if (params.containsKey("flow")) return trimmed

        val encodedFlow = URLEncoder.encode("xtls-rprx-vision", "UTF-8")
        return trimmed.substring(0, qIdx + 1) + fragment + "&flow=$encodedFlow"
    }

    /** True when a config uses a transport unsupported by most clients (e.g. xhttp). */
    fun hasUnsupportedTransport(raw: String): Boolean {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("vless://", ignoreCase = true) &&
            !trimmed.startsWith("vmess://", ignoreCase = true)) return false
        val qIdx = trimmed.indexOf('?')
        if (qIdx < 0) return false
        val params = parseQueryParams(trimmed.substring(qIdx + 1))
        val transport = params["type"]?.lowercase() ?: return false
        return transport !in setOf("tcp", "grpc", "ws", "http", "httpupgrade", "")
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (query.isBlank()) return map
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
