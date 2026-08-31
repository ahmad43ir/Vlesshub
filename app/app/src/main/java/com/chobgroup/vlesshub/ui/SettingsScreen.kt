package com.chobgroup.vlesshub.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chobgroup.vlesshub.core.theme.BackgroundGradient
import com.chobgroup.vlesshub.core.theme.VlessHubColors
import com.chobgroup.vlesshub.data.AppConstants
import com.chobgroup.vlesshub.ui.icons.AppIcons
import com.chobgroup.vlesshub.util.ChobGroupLink

/**
 * Settings tab — professional layout with About section,
 * contact cards, and close-source notice (v2.10+).
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    fun sendSupportEmail(subject: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(AppConstants.CONTACT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, "Hi VlessHub team,\n\nMy Telegram channel: @\nReason:\n\nThanks,")
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Send email")) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        // ── Header ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = VlessHubColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text("About, privacy & support", color = VlessHubColors.TextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(20.dp))

        // ── About Card ────────────────────────────────────────────────────
        AboutCard()
        Spacer(Modifier.height(24.dp))

        // ── General ───────────────────────────────────────────────────────
        SectionLabel("GENERAL")
        Spacer(Modifier.height(8.dp))
        GlassSettingsRow(
            title = "More apps",
            subtitle = "Explore all Chob Group apps",
            icon = AppIcons.Apps,
            onClick = { ChobGroupLink.open(context) },
        )
        Spacer(Modifier.height(6.dp))
        GlassSettingsRow(
            title = "Privacy Policy",
            subtitle = "How we handle your data",
            icon = AppIcons.Folder,
            onClick = { openUrl(AppConstants.PRIVACY_POLICY_URL) },
        )
        Spacer(Modifier.height(24.dp))

        // ── Support ───────────────────────────────────────────────────────
        SectionLabel("SUPPORT — CHANNEL OWNERS")
        Spacer(Modifier.height(8.dp))
        GlassSettingsRow(
            title = "Remove my Telegram channel",
            subtitle = "Email us — we'll purge your configs",
            icon = Icons.Filled.MailOutline,
            onClick = { sendSupportEmail(AppConstants.SUBJECT_REMOVE_CHANNEL) },
        )
        Spacer(Modifier.height(6.dp))
        GlassSettingsRow(
            title = "Add my Telegram channel",
            subtitle = "Share fresh configs with VlessHub users",
            icon = Icons.Filled.Star,
            onClick = { sendSupportEmail(AppConstants.SUBJECT_ADD_CHANNEL) },
        )
        Spacer(Modifier.height(24.dp))

        // ── Close-Source Notice ───────────────────────────────────────────
        CloseSourceNotice()
        Spacer(Modifier.height(24.dp))

        // ── Footer ────────────────────────────────────────────────────────
        Text(
            "VlessHub is a config hub — it shares VLESS links, VPN files\nand MTProto proxies scraped from public Telegram channels.\nIt never tunnels your traffic.",
            color = VlessHubColors.TextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AboutCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = VlessHubColors.BgCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, VlessHubColors.CardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Logo orb
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = VlessHubColors.AccentNeon.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, VlessHubColors.AccentNeon.copy(alpha = 0.4f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        AppIcons.Folder,
                        contentDescription = "VlessHub",
                        tint = VlessHubColors.AccentNeon,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "VlessHub",
                color = VlessHubColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "v2.10.0",
                color = VlessHubColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = VlessHubColors.AccentNeon.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, VlessHubColors.AccentNeon.copy(alpha = 0.3f)),
            ) {
                Text(
                    "Free & Open Config Hub",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    color = VlessHubColors.AccentNeon,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun CloseSourceNotice() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = VlessHubColors.WarningDim,
        border = androidx.compose.foundation.BorderStroke(1.dp, VlessHubColors.Warning.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = VlessHubColors.Warning.copy(alpha = 0.2f),
                ) {
                    Icon(
                        Icons.Filled.MailOutline,
                        contentDescription = null,
                        tint = VlessHubColors.Warning,
                        modifier = Modifier.padding(6.dp).size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Closed Source from v2.11",
                    color = VlessHubColors.Warning,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Starting with version 2.11, VlessHub will be developed and distributed as closed source. This decision was made for security reasons — to protect user privacy and prevent misuse of the codebase. v2.10 is the last open-source release.",
                color = VlessHubColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = VlessHubColors.AccentNeon,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun GlassSettingsRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.MailOutline,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = VlessHubColors.BgCard,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, VlessHubColors.GlassBorder),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = VlessHubColors.AccentNeon.copy(alpha = 0.1f),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = VlessHubColors.AccentNeon,
                    modifier = Modifier.padding(6.dp).size(16.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = VlessHubColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, color = VlessHubColors.TextMuted, fontSize = 11.sp)
                }
            }
            if (onClick != null) {
                Icon(
                    AppIcons.ContentCopy,
                    contentDescription = null,
                    tint = VlessHubColors.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
