package com.chobgroup.admin_vlesshub.config

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.Base64

/**
 * Converts VLESS/Trojan/VMess URI strings into xray-core JSON config
 * suitable for running a temporary local proxy for real-delay testing.
 */
object XrayConfigGenerator {

    /**
     * Generate a complete xray-core JSON config with a SOCKS inbound
     * on the given local port, and the server as the outbound.
     *
     * @param rawUri The VLESS/Trojan/VMess URI string
     * @param localPort Local SOCKS port for the test proxy
     * @param testUrl URL to fetch through the proxy for latency measurement
     * @return xray-core JSON config string, or null if the URI can't be parsed
     */
    fun generate(rawUri: String, localPort: Int, testUrl: String = "http://cp.cloudflare.com/"): String? {
        val trimmed = rawUri.trim()
        return when {
            trimmed.startsWith("vless://", ignoreCase = true) -> generateVless(trimmed, localPort, testUrl)
            trimmed.startsWith("trojan://", ignoreCase = true) -> generateTrojan(trimmed, localPort, testUrl)
            trimmed.startsWith("vmess://", ignoreCase = true) -> generateVmess(trimmed, localPort, testUrl)
            trimmed.startsWith("ss://", ignoreCase = true) -> generateShadowsocks(trimmed, localPort, testUrl)
            else -> null
        }
    }

    // ── VLESS ──────────────────────────────────────────────────

    private fun generateVless(uri: String, localPort: Int, testUrl: String): String? {
        return try {
            val qIdx = uri.indexOf('?')
            val hashIdx = uri.indexOf('#')
            val authority = uri.substring("vless://".length, if (qIdx > 0) qIdx else hashIdx.coerceAtLeast(uri.length))
            val uuid = authority.substringBefore('@', "")
            val hostPort = authority.substringAfter('@', "")
            val host = hostPort.substringBeforeLast(':', "")
            val port = hostPort.substringAfterLast(':', "443").toIntOrNull() ?: 443

            val query = if (qIdx > 0 && hashIdx > qIdx) uri.substring(qIdx + 1, hashIdx)
            else if (qIdx > 0) uri.substring(qIdx + 1)
            else ""
            val params = parseQuery(query)

            val security = params["security"] ?: "none"
            val flow = params["flow"] ?: ""
            val sni = params["sni"] ?: host
            val fp = params["fp"] ?: "chrome"
            val pbk = params["pbk"] ?: ""
            val sid = params["sid"] ?: ""
            val alpn = params["alpn"] ?: ""
            val transport = params["type"] ?: "tcp"
            val path = params["path"] ?: ""
            val transportHost = params["host"] ?: ""

            // Build streamSettings
            val streamSettings = JSONObject()

            // Security (tls / reality / none)
            when (security.lowercase()) {
                "reality" -> {
                    streamSettings.put("security", "reality")
                    val realitySettings = JSONObject()
                    realitySettings.put("fingerprint", fp)
                    realitySettings.put("serverName", sni)
                    realitySettings.put("publicKey", pbk)
                    realitySettings.put("shortId", sid)
                    streamSettings.put("realitySettings", realitySettings)
                }
                "tls" -> {
                    streamSettings.put("security", "tls")
                    val tlsSettings = JSONObject()
                    tlsSettings.put("serverName", sni)
                    tlsSettings.put("fingerprint", fp)
                    if (alpn.isNotEmpty()) {
                        tlsSettings.put("alpn", JSONArray(alpn.split(",").map { it.trim() }))
                    }
                    streamSettings.put("tlsSettings", tlsSettings)
                }
                else -> streamSettings.put("security", "none")
            }

            // Transport
            streamSettings.put("network", transport)
            when (transport.lowercase()) {
                "ws" -> {
                    val wsSettings = JSONObject()
                    if (path.isNotEmpty()) wsSettings.put("path", path)
                    if (transportHost.isNotEmpty()) wsSettings.put("headers", JSONObject().put("Host", transportHost))
                    streamSettings.put("wsSettings", wsSettings)
                }
                "grpc" -> {
                    val grpcSettings = JSONObject()
                    val serviceName = params["serviceName"] ?: params["path"]?.removePrefix("/") ?: ""
                    if (serviceName.isNotEmpty()) grpcSettings.put("serviceName", serviceName)
                    streamSettings.put("grpcSettings", grpcSettings)
                }
                "h2", "http" -> {
                    val h2Settings = JSONObject()
                    if (path.isNotEmpty()) h2Settings.put("path", path)
                    if (transportHost.isNotEmpty()) h2Settings.put("host", JSONArray().put(transportHost))
                    streamSettings.put("httpSettings", h2Settings)
                }
            }

            // Build outbound
            val vlessSettings = JSONObject()
                .put("vnext", JSONArray().put(JSONObject()
                    .put("address", host)
                    .put("port", port)
                    .put("users", JSONArray().put(JSONObject()
                        .put("id", uuid)
                        .put("encryption", params["encryption"] ?: "none")
                        .apply { if (flow.isNotEmpty()) put("flow", flow) }
                    ))
                ))

            val outbound = JSONObject()
                .put("protocol", "vless")
                .put("settings", vlessSettings)
                .put("streamSettings", streamSettings)
                .put("tag", "proxy")

            buildConfig(localPort, outbound, testUrl)
        } catch (e: Exception) {
            null
        }
    }

    // ── Trojan ─────────────────────────────────────────────────

    private fun generateTrojan(uri: String, localPort: Int, testUrl: String): String? {
        return try {
            val qIdx = uri.indexOf('?')
            val hashIdx = uri.indexOf('#')
            val authority = uri.substring("trojan://".length, if (qIdx > 0) qIdx else hashIdx.coerceAtLeast(uri.length))
            val password = authority.substringBefore('@', "")
            val hostPort = authority.substringAfter('@', "")
            val host = hostPort.substringBeforeLast(':', "")
            val port = hostPort.substringAfterLast(':', "443").toIntOrNull() ?: 443

            val query = if (qIdx > 0 && hashIdx > qIdx) uri.substring(qIdx + 1, hashIdx)
            else if (qIdx > 0) uri.substring(qIdx + 1)
            else ""
            val params = parseQuery(query)

            val security = params["security"] ?: "tls"
            val sni = params["sni"] ?: host
            val fp = params["fp"] ?: ""
            val alpn = params["alpn"] ?: ""
            val transport = params["type"] ?: "tcp"
            val path = params["path"] ?: ""
            val transportHost = params["host"] ?: ""

            // Build streamSettings
            val streamSettings = JSONObject()
            when (security.lowercase()) {
                "tls" -> {
                    streamSettings.put("security", "tls")
                    val tlsSettings = JSONObject()
                    tlsSettings.put("serverName", sni)
                    if (fp.isNotEmpty()) tlsSettings.put("fingerprint", fp)
                    if (alpn.isNotEmpty()) {
                        tlsSettings.put("alpn", JSONArray(alpn.split(",").map { it.trim() }))
                    }
                    val insecure = params["allowInsecure"] == "1" || params["insecure"] == "1"
                    if (insecure) tlsSettings.put("allowInsecure", true)
                    streamSettings.put("tlsSettings", tlsSettings)
                }
                else -> streamSettings.put("security", "none")
            }

            // Transport
            streamSettings.put("network", transport)
            when (transport.lowercase()) {
                "ws" -> {
                    val wsSettings = JSONObject()
                    if (path.isNotEmpty()) wsSettings.put("path", path)
                    if (transportHost.isNotEmpty()) wsSettings.put("headers", JSONObject().put("Host", transportHost))
                    streamSettings.put("wsSettings", wsSettings)
                }
                "grpc" -> {
                    val grpcSettings = JSONObject()
                    val serviceName = params["serviceName"] ?: params["path"]?.removePrefix("/") ?: ""
                    if (serviceName.isNotEmpty()) grpcSettings.put("serviceName", serviceName)
                    streamSettings.put("grpcSettings", grpcSettings)
                }
                "h2", "http" -> {
                    val h2Settings = JSONObject()
                    if (path.isNotEmpty()) h2Settings.put("path", path)
                    if (transportHost.isNotEmpty()) h2Settings.put("host", JSONArray().put(transportHost))
                    streamSettings.put("httpSettings", h2Settings)
                }
            }

            // Build outbound
            val trojanSettings = JSONObject()
                .put("servers", JSONArray().put(JSONObject()
                    .put("address", host)
                    .put("port", port)
                    .put("password", password)
                    .put("email", "test@vlesshub")
                ))

            val outbound = JSONObject()
                .put("protocol", "trojan")
                .put("settings", trojanSettings)
                .put("streamSettings", streamSettings)
                .put("tag", "proxy")

            buildConfig(localPort, outbound, testUrl)
        } catch (e: Exception) {
            null
        }
    }

    // ── VMess ──────────────────────────────────────────────────

    private fun generateVmess(uri: String, localPort: Int, testUrl: String): String? {
        return try {
            var encoded = uri.substring("vmess://".length)
            if (encoded.startsWith("/")) encoded = encoded.substring(1)
            val cut = encoded.indexOfFirst { it == '?' || it == '#' }
            if (cut >= 0) encoded = encoded.substring(0, cut)
            encoded = encoded.replace('-', '+').replace('_', '/')
            when (encoded.length % 4) {
                2 -> encoded += "=="
                3 -> encoded += "="
            }
            val decoded = String(Base64.getDecoder().decode(encoded))
            val json = JSONObject(decoded)

            val host = json.optString("add").ifEmpty { json.optString("address") }
            val port = json.optInt("port", 443)
            val uuid = json.optString("id")
            val security = json.optString("security", "auto")
            val tls = json.optString("tls")
            val sni = json.optString("sni").ifEmpty { host }
            val fp = json.optString("fp").ifEmpty { "chrome" }
            val net = json.optString("net").ifEmpty { json.optString("type", "tcp") }
            val path = json.optString("path").ifEmpty { "" }
            val host2 = json.optString("host").ifEmpty { "" }
            val alpn = json.optString("alpn").ifEmpty { "" }

            val streamSettings = JSONObject()
            if (tls == "tls") {
                streamSettings.put("security", "tls")
                val tlsSettings = JSONObject()
                tlsSettings.put("serverName", sni)
                tlsSettings.put("fingerprint", fp)
                if (alpn.isNotEmpty()) tlsSettings.put("alpn", JSONArray(alpn.split(",").map { it.trim() }))
                streamSettings.put("tlsSettings", tlsSettings)
            } else {
                streamSettings.put("security", "none")
            }

            streamSettings.put("network", net)
            when (net.lowercase()) {
                "ws" -> {
                    val wsSettings = JSONObject()
                    if (path.isNotEmpty()) wsSettings.put("path", path)
                    if (host2.isNotEmpty()) wsSettings.put("headers", JSONObject().put("Host", host2))
                    streamSettings.put("wsSettings", wsSettings)
                }
                "grpc" -> {
                    val grpcSettings = JSONObject()
                    val sn2 = json.optString("serviceName", "").ifEmpty { path.removePrefix("/") }
                    if (sn2.isNotEmpty()) grpcSettings.put("serviceName", sn2)
                    streamSettings.put("grpcSettings", grpcSettings)
                }
            }

            val vmessSettings = JSONObject()
                .put("vnext", JSONArray().put(JSONObject()
                    .put("address", host)
                    .put("port", port)
                    .put("users", JSONArray().put(JSONObject()
                        .put("id", uuid)
                        .put("alterId", json.optInt("aid", 0))
                        .put("security", json.optString("scy", "auto"))
                    ))
                ))

            val outbound = JSONObject()
                .put("protocol", "vmess")
                .put("settings", vmessSettings)
                .put("streamSettings", streamSettings)
                .put("tag", "proxy")

            buildConfig(localPort, outbound, testUrl)
        } catch (e: Exception) {
            null
        }
    }

    // ── Shadowsocks ────────────────────────────────────────────

    private fun generateShadowsocks(uri: String, localPort: Int, testUrl: String): String? {
        return try {
            val raw = uri.substring("ss://".length)
            val hashIdx = raw.indexOf('#')
            val atIdx = raw.indexOf('@')
            // ss://method:password@host:port or ss://base64(method:password)@host:port
            val host: String
            val port: Int
            val method: String
            val password: String

            if (atIdx > 0) {
                val userInfo = raw.substring(0, atIdx)
                val hostPort = raw.substring(atIdx + 1, if (hashIdx > atIdx) hashIdx else raw.length)
                host = hostPort.substringBeforeLast(':')
                port = hostPort.substringAfterLast(':').toIntOrNull() ?: 443

                if (userInfo.contains(':')) {
                    method = userInfo.substringBefore(':')
                    password = userInfo.substringAfter(':')
                } else {
                    val decoded = String(Base64.getDecoder().decode(userInfo.replace('-', '+').replace('_', '/')))
                    method = decoded.substringBefore(':')
                    password = decoded.substringAfter(':')
                }
            } else {
                return null
            }

            val ssSettings = JSONObject()
                .put("servers", JSONArray().put(JSONObject()
                    .put("address", host)
                    .put("port", port)
                    .put("method", method)
                    .put("password", password)
                ))

            val outbound = JSONObject()
                .put("protocol", "shadowsocks")
                .put("settings", ssSettings)
                .put("tag", "proxy")

            val streamSettings = JSONObject().put("network", "tcp")
            outbound.put("streamSettings", streamSettings)

            buildConfig(localPort, outbound, testUrl)
        } catch (e: Exception) {
            null
        }
    }

    // ── measureOutboundDelay config (no SOCKS inbound needed) ──

    /**
     * Generate a minimal xray-core config for Libv2ray.measureOutboundDelay().
     * This only needs the outbound — measureOutboundDelay handles the rest.
     */
    fun generateForMeasure(rawUri: String): String? {
        val trimmed = rawUri.trim()
        val outbound = when {
            trimmed.startsWith("vless://", ignoreCase = true) -> buildVlessOutbound(trimmed)
            trimmed.startsWith("trojan://", ignoreCase = true) -> buildTrojanOutbound(trimmed)
            trimmed.startsWith("vmess://", ignoreCase = true) -> buildVmessOutbound(trimmed)
            trimmed.startsWith("ss://", ignoreCase = true) -> buildShadowsocksOutbound(trimmed)
            else -> null
        } ?: return null

        // measureOutboundDelay expects a config with outbounds array
        val config = JSONObject()
        config.put("log", JSONObject().put("loglevel", "warning").put("access", "none").put("error", "none"))
        config.put("outbounds", JSONArray().put(outbound))
        return config.toString()
    }

    private fun buildVlessOutbound(uri: String): JSONObject? {
        return try {
            val qIdx = uri.indexOf('?')
            val hashIdx = uri.indexOf('#')
            val authority = uri.substring("vless://".length, if (qIdx > 0) qIdx else hashIdx.coerceAtLeast(uri.length))
            val uuid = authority.substringBefore('@', "")
            val hostPort = authority.substringAfter('@', "")
            val host = hostPort.substringBeforeLast(':', "")
            val port = hostPort.substringAfterLast(':', "443").toIntOrNull() ?: 443
            val query = if (qIdx > 0 && hashIdx > qIdx) uri.substring(qIdx + 1, hashIdx) else if (qIdx > 0) uri.substring(qIdx + 1) else ""
            val params = parseQuery(query)

            val flow = params["flow"] ?: ""
            val encryption = params["encryption"] ?: "none"
            val userObj = JSONObject().put("id", uuid).put("encryption", encryption)
            if (flow.isNotEmpty()) userObj.put("flow", flow)

            val vnextObj = JSONObject().put("address", host).put("port", port).put("users", JSONArray().put(userObj))
            val settings = JSONObject().put("vnext", JSONArray().put(vnextObj))
            val streamSettings = buildStreamSettings(params, host)

            JSONObject().put("protocol", "vless").put("settings", settings).put("streamSettings", streamSettings).put("tag", "proxy")
        } catch (e: Exception) { null }
    }

    private fun buildTrojanOutbound(uri: String): JSONObject? {
        return try {
            val qIdx = uri.indexOf('?')
            val hashIdx = uri.indexOf('#')
            val authority = uri.substring("trojan://".length, if (qIdx > 0) qIdx else hashIdx.coerceAtLeast(uri.length))
            val password = authority.substringBefore('@', "")
            val hostPort = authority.substringAfter('@', "")
            val host = hostPort.substringBeforeLast(':', "")
            val port = hostPort.substringAfterLast(':', "443").toIntOrNull() ?: 443
            val query = if (qIdx > 0 && hashIdx > qIdx) uri.substring(qIdx + 1, hashIdx) else if (qIdx > 0) uri.substring(qIdx + 1) else ""
            val params = parseQuery(query)

            val serverObj = JSONObject().put("address", host).put("port", port).put("password", password)
            val settings = JSONObject().put("servers", JSONArray().put(serverObj))
            val streamSettings = buildStreamSettings(params, host)

            JSONObject().put("protocol", "trojan").put("settings", settings).put("streamSettings", streamSettings).put("tag", "proxy")
        } catch (e: Exception) { null }
    }

    private fun buildVmessOutbound(uri: String): JSONObject? {
        return try {
            var encoded = uri.substring("vmess://".length)
            if (encoded.startsWith("/")) encoded = encoded.substring(1)
            val cut = encoded.indexOfFirst { it == '?' || it == '#' }
            if (cut >= 0) encoded = encoded.substring(0, cut)
            encoded = encoded.replace('-', '+').replace('_', '/')
            when (encoded.length % 4) { 2 -> encoded += "=="; 3 -> encoded += "=" }
            val json = JSONObject(String(Base64.getDecoder().decode(encoded)))
            val host = json.optString("add").ifEmpty { json.optString("address") }
            val port = json.optInt("port", 443)
            val uuid = json.optString("id")
            val tls = json.optString("tls")
            val sni = json.optString("sni").ifEmpty { host }
            val fp = json.optString("fp").ifEmpty { "chrome" }
            val net = json.optString("net").ifEmpty { json.optString("type", "tcp") }
            val path = json.optString("path").ifEmpty { "" }
            val host2 = json.optString("host").ifEmpty { "" }

            val userObj = JSONObject().put("id", uuid).put("alterId", json.optInt("aid", 0)).put("security", json.optString("scy", "auto"))
            val vnextObj = JSONObject().put("address", host).put("port", port).put("users", JSONArray().put(userObj))
            val settings = JSONObject().put("vnext", JSONArray().put(vnextObj))

            val streamSettings = JSONObject()
            if (tls == "tls") {
                streamSettings.put("security", "tls")
                val tlsSettings = JSONObject().put("serverName", sni).put("fingerprint", fp)
                streamSettings.put("tlsSettings", tlsSettings)
            } else {
                streamSettings.put("security", "none")
            }
            streamSettings.put("network", net)
            when (net.lowercase()) {
                "ws" -> { val ws = JSONObject(); if (path.isNotEmpty()) ws.put("path", path); if (host2.isNotEmpty()) ws.put("headers", JSONObject().put("Host", host2)); streamSettings.put("wsSettings", ws) }
                "grpc" -> { val g = JSONObject(); val sn = json.optString("serviceName", ""); if (sn.isNotEmpty()) g.put("serviceName", sn); streamSettings.put("grpcSettings", g) }
            }

            JSONObject().put("protocol", "vmess").put("settings", settings).put("streamSettings", streamSettings).put("tag", "proxy")
        } catch (e: Exception) { null }
    }

    private fun buildShadowsocksOutbound(uri: String): JSONObject? {
        return try {
            val raw = uri.substring("ss://".length)
            val hashIdx = raw.indexOf('#')
            val atIdx = raw.indexOf('@')
            if (atIdx < 0) return null
            val userInfo = raw.substring(0, atIdx)
            val hostPort = raw.substring(atIdx + 1, if (hashIdx > atIdx) hashIdx else raw.length)
            val host = hostPort.substringBeforeLast(':')
            val port = hostPort.substringAfterLast(':').toIntOrNull() ?: 443
            val method: String; val password: String
            if (userInfo.contains(':')) { method = userInfo.substringBefore(':'); password = userInfo.substringAfter(':') }
            else { val d = String(Base64.getDecoder().decode(userInfo.replace('-', '+').replace('_', '/'))); method = d.substringBefore(':'); password = d.substringAfter(':') }
            val serverObj = JSONObject().put("address", host).put("port", port).put("method", method).put("password", password)
            JSONObject().put("protocol", "shadowsocks").put("settings", JSONObject().put("servers", JSONArray().put(serverObj))).put("streamSettings", JSONObject().put("network", "tcp")).put("tag", "proxy")
        } catch (e: Exception) { null }
    }

    private fun buildStreamSettings(params: Map<String, String>, host: String): JSONObject {
        val security = params["security"] ?: "none"
        val transport = params["type"] ?: "tcp"
        val path = params["path"] ?: ""
        val transportHost = params["host"] ?: ""
        val sni = params["sni"] ?: host
        val fp = params["fp"] ?: "chrome"
        val alpn = params["alpn"] ?: ""
        val pbk = params["pbk"] ?: ""
        val sid = params["sid"] ?: ""
        val mode = params["mode"] ?: ""
        val extra = params["extra"] ?: ""
        val xhrOrigins = params["origins"] ?: ""

        val streamSettings = JSONObject()
        when (security.lowercase()) {
            "reality" -> { streamSettings.put("security", "reality"); streamSettings.put("realitySettings", JSONObject().put("fingerprint", fp).put("serverName", sni).put("publicKey", pbk).put("shortId", sid)) }
            "tls" -> { streamSettings.put("security", "tls"); val tlsSettings = JSONObject().put("serverName", sni).put("fingerprint", fp); if (alpn.isNotEmpty()) tlsSettings.put("alpn", JSONArray(alpn.split(",").map { it.trim() })); val insecure = params["allowInsecure"] == "1" || params["insecure"] == "1"; if (insecure) tlsSettings.put("allowInsecure", true); streamSettings.put("tlsSettings", tlsSettings) }
            else -> streamSettings.put("security", "none")
        }

        streamSettings.put("network", transport)
        when (transport.lowercase()) {
            "ws" -> { val ws = JSONObject(); if (path.isNotEmpty()) ws.put("path", path); if (transportHost.isNotEmpty()) ws.put("headers", JSONObject().put("Host", transportHost)); streamSettings.put("wsSettings", ws) }
            "grpc" -> { val g = JSONObject(); val sn = params["serviceName"] ?: path.removePrefix("/"); if (sn.isNotEmpty()) g.put("serviceName", sn); streamSettings.put("grpcSettings", g) }
            "h2", "http" -> { val h2 = JSONObject(); if (path.isNotEmpty()) h2.put("path", path); if (transportHost.isNotEmpty()) h2.put("host", JSONArray().put(transportHost)); streamSettings.put("httpSettings", h2) }
            "xhttp" -> {
                val xh = JSONObject()
                if (path.isNotEmpty()) xh.put("path", path)
                if (transportHost.isNotEmpty()) xh.put("host", JSONArray().put(transportHost))
                if (mode.isNotEmpty()) xh.put("mode", mode)
                if (extra.isNotEmpty()) {
                    try { xh.put("extra", JSONObject(extra)) } catch (e: Exception) { xh.put("extra", JSONObject().put("mode", mode)) }
                }
                streamSettings.put("xhttpSettings", xh)
            }
        }

        return streamSettings
    }

    // ── Config builder ─────────────────────────────────────────

    private fun buildConfig(localPort: Int, outbound: JSONObject, testUrl: String): String {
        val config = JSONObject()

        // Logging
        config.put("log", JSONObject()
            .put("loglevel", "warning")
            .put("access", "none")
            .put("error", "none")
        )

        // Inbound: local SOCKS proxy
        config.put("inbounds", JSONArray().put(JSONObject()
            .put("tag", "socks-in")
            .put("port", localPort)
            .put("listen", "127.0.0.1")
            .put("protocol", "socks")
            .put("settings", JSONObject()
                .put("auth", "noauth")
                .put("udp", true)
            )
            .put("sniffing", JSONObject()
                .put("enabled", true)
                .put("destOverride", JSONArray().put("http").put("tls"))
            )
        ))

        // Outbound: the server
        config.put("outbounds", JSONArray().put(outbound))

        return config.toString(2)
    }

    // ── Helpers ────────────────────────────────────────────────

    private fun parseQuery(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (query.isBlank()) return map
        query.split("&").forEach { pair ->
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
