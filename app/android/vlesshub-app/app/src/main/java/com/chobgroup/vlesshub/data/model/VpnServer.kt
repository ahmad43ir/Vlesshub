package com.chobgroup.vlesshub.data.model

/** Raw config input format — spec §6.1. */
enum class ConfigFormat {
    LINK,
    JSON,
    NPV,
    CONF,
    RAW,
    SIP;

    companion object {
        fun fromString(value: String?): ConfigFormat = when (value?.lowercase()) {
            "json" -> JSON
            "npv" -> NPV
            "conf" -> CONF
            "raw" -> RAW
            "sip" -> SIP
            else -> LINK
        }
    }

    /** Human-readable description for UI display. */
    val displayName: String
        get() = when (this) {
            LINK -> "Link"
            JSON -> "JSON"
            NPV -> "NPV"
            CONF -> "WireGuard Conf"
            RAW -> "Raw"
            SIP -> "SIP (SOCKS only)"
        }
}

/** Transport type detected from the config URI. */
enum class TransportType(val displayName: String) {
    TCP("TCP"),
    WS("WebSocket"),
    GRPC("gRPC"),
    HTTP("HTTP/2"),
    XHTTP("XHTTP"),
    UNKNOWN("Unknown");

    companion object {
        fun fromString(value: String?): TransportType = when (value?.lowercase()) {
            "tcp" -> TCP
            "ws" -> WS
            "grpc" -> GRPC
            "h2", "http" -> HTTP
            "xhttp" -> XHTTP
            else -> UNKNOWN
        }
    }
}

/**
 * Authoritative validation status — only "Working" means the config
 * actually works through a real Xray-core proxy connection.
 */
enum class ValidationStatus(val displayName: String) {
    NOT_TESTED("Not tested"),
    REACHABLE("Reachable"),
    UNREACHABLE("Unreachable"),
    INVALID("Invalid config"),
    NEEDS_REAL_TEST("Needs real test"),
    WORKING("Working"),
    FAILED("Failed");

    companion object {
        fun fromString(value: String?): ValidationStatus = when (value?.lowercase()) {
            "reachable" -> REACHABLE
            "unreachable" -> UNREACHABLE
            "invalid" -> INVALID
            "needs_real_test" -> NEEDS_REAL_TEST
            "working" -> WORKING
            "failed" -> FAILED
            else -> NOT_TESTED
        }
    }
}

/**
 * Per-client compatibility info. A config can be Working on Xray-core
 * but unsupported by certain clients (e.g. XHTTP works on v2rayNG but not NekoBox).
 */
data class ClientCompatibility(
    val v2rayng: Boolean = false,
    val v2rayn: Boolean = false,
    val nekoboxAndroid: Boolean = false,
    val hiddify: Boolean = false,
    val streisand: Boolean = false,
) {
    companion object {
        /**
         * Determine client compatibility based on transport type.
         * Source: VlessHub research (August 2026).
         */
        fun forTransport(transport: TransportType): ClientCompatibility = when (transport) {
            TransportType.TCP -> ClientCompatibility(
                v2rayng = true, v2rayn = true, nekoboxAndroid = true,
                hiddify = true, streisand = true,
            )
            TransportType.WS -> ClientCompatibility(
                v2rayng = true, v2rayn = true, nekoboxAndroid = true,
                hiddify = true, streisand = true,
            )
            TransportType.GRPC -> ClientCompatibility(
                v2rayng = true, v2rayn = true, nekoboxAndroid = true,
                hiddify = true, streisand = true,
            )
            TransportType.HTTP -> ClientCompatibility(
                v2rayng = true, v2rayn = true, nekoboxAndroid = true,
                hiddify = true, streisand = true,
            )
            TransportType.XHTTP -> ClientCompatibility(
                v2rayng = true, v2rayn = true, nekoboxAndroid = false,
                hiddify = false, streisand = false,
            )
            TransportType.UNKNOWN -> ClientCompatibility()
        }
    }

    /** Count of supported clients. */
    val supportedCount: Int get() = listOf(v2rayng, v2rayn, nekoboxAndroid, hiddify, streisand).count { it }

    /** Short summary for UI. */
    val summary: String
        get() {
            val supported = mutableListOf<String>()
            val unsupported = mutableListOf<String>()
            if (v2rayng) supported.add("v2rayNG") else unsupported.add("v2rayNG")
            if (v2rayn) supported.add("v2rayN") else unsupported.add("v2rayN")
            if (nekoboxAndroid) supported.add("NekoBox") else unsupported.add("NekoBox")
            if (hiddify) supported.add("Hiddify") else unsupported.add("Hiddify")
            if (streisand) supported.add("Streisand") else unsupported.add("Streisand")
            return "✓ ${supported.joinToString(", ")}\n✕ ${unsupported.joinToString(", ")}"
        }
}

/**
 * A VPN server as shown in the server list — spec §6.1.
 * Configs ALWAYS come from the backend (spec rule 5) — never hardcoded.
 */
data class VpnServer(
    val name: String,
    val flag: String,
    val country: String,
    val rawConfig: String,
    val type: VpnProtocol = VpnProtocol.VLESS,
    val configFormat: ConfigFormat = ConfigFormat.LINK,
    /** Detected transport type (tcp/grpc/ws/xhttp). */
    val transport: TransportType = TransportType.UNKNOWN,
    /** Socket-level pre-filter result (NOT proof config works). */
    val pingMs: Int? = null,
    /** Authoritative validation status — only "Working" means real Xray test passed. */
    val validationStatus: ValidationStatus = ValidationStatus.NOT_TESTED,
    /** Real delay via Xray-core proxy test (-1 = failed, null = not tested). */
    val realDelayMs: Int? = null,
    /** Human-readable error from the last validation. */
    val validationError: String? = null,
    /** ISO-8601 UTC timestamp of last successful validation. */
    val validatedAt: String? = null,
    /** Xray-core version used for validation. */
    val xrayVersion: String? = null,
    /** Per-client transport compatibility. */
    val clientCompatibility: ClientCompatibility = ClientCompatibility(),
    /** When this config was scraped (ISO-8601 UTC from `servers.created_at`). */
    val createdAt: String? = null,
    /** Telegram channel this config was scraped from (e.g. "@channelname"). */
    val sourceChannel: String? = null,
    /** Server ID from the backend — used for admin DELETE operations. */
    val id: String? = null,
)
