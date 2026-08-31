package com.chobgroup.vlesshub.core.theme

import androidx.compose.ui.graphics.Color

/**
 * VlessHub v2.10 — refined "cyber-organic" palette.
 * Dark deep-forest background + neon green accent. Dark theme only.
 * Inspired by clean, professional VPN app designs (Begzar, Mullvad, etc.)
 */
object VlessHubColors {
    // ── Backgrounds ─────────────────────────────────────────
    val BgDeepForest = Color(0xFF0B1A12)
    val BgDarkEmerald = Color(0xFF0A1912)
    val BgCard = Color(0x1A10251B)
    val BgCardElevated = Color(0x2210251B)

    // ── Accent ──────────────────────────────────────────────
    val AccentNeon = Color(0xFF4CFF88)
    val AccentLime = Color(0xFF7DFFA8)
    val AccentTeal = Color(0xFF26A69A)

    // ── Text ────────────────────────────────────────────────
    val TextPrimary = Color(0xFFF0F7F2)
    val TextSecondary = Color(0xFFB8C9BE)
    val TextMuted = Color(0xFF7A8F82)
    val TextOnAccent = Color(0xFF0B1A12)

    // ── Borders ─────────────────────────────────────────────
    val CardBorder = Color(0x33FFFFFF)
    val GlassBorder = Color(0x1FFFFFFF)
    val CardBorderFocused = Color(0x664CFF88)

    // ── Status ──────────────────────────────────────────────
    val Success = Color(0xFF4CFF88)
    val SuccessDim = Color(0x144CFF88)
    val Warning = Color(0xFFFFA726)
    val WarningDim = Color(0x1AFFA726)
    val Error = Color(0xFFFF5252)
    val ErrorDim = Color(0x14FF5252)
    val Info = Color(0xFF42A5F5)
    val InfoDim = Color(0x1442A5F5)

    // ── Legacy aliases ──────────────────────────────────────
    val CardTranslucent = Color(0x1410251B)
    val WarningOrange = Warning
    val ErrorRed = Error
}
