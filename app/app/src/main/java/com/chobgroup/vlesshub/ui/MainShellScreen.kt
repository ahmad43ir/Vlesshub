package com.chobgroup.vlesshub.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import com.chobgroup.vlesshub.core.theme.VlessHubColors
import com.chobgroup.vlesshub.ui.icons.AppIcons

/**
 * VlessHub main shell — 4 tabs:
 *  - **Links**   — VLESS/V2Ray config launcher
 *  - **MTProto** — Telegram proxy batches
 *  - **Files**   — VPN config files
 *  - **Settings** — About, privacy & support
 */
@Composable
fun MainShellScreen() {
    var currentTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = VlessHubColors.BgDeepForest,
        bottomBar = {
            BottomBar(
                items = listOf(
                    BottomBarItem(0, "Links", Icons.AutoMirrored.Outlined.List, Icons.AutoMirrored.Filled.List),
                    BottomBarItem(1, "MTProto", Icons.AutoMirrored.Outlined.Send, Icons.AutoMirrored.Filled.Send),
                    BottomBarItem(2, "Files", AppIcons.Folder, AppIcons.Folder),
                    BottomBarItem(3, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
                ),
                currentTab = currentTab,
                onSelect = { currentTab = it },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (currentTab) {
                0 -> LinksScreen()
                1 -> HomeScreen()
                2 -> FilesScreen()
                else -> SettingsScreen()
            }
        }
    }
}

private data class BottomBarItem(
    val index: Int,
    val label: String,
    val outlined: ImageVector,
    val filled: ImageVector,
)

/** Professional neon bottom bar with smooth color transitions and pill indicator. */
@Composable
private fun BottomBar(
    items: List<BottomBarItem>,
    currentTab: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(VlessHubColors.BgDeepForest)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(
            color = VlessHubColors.CardBorder,
            thickness = 0.5.dp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            items.forEach { item ->
                val selected = item.index == currentTab
                val iconTint by animateColorAsState(
                    targetValue = if (selected) VlessHubColors.AccentNeon else VlessHubColors.TextMuted,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "iconTint",
                )
                val textColor by animateColorAsState(
                    targetValue = if (selected) VlessHubColors.AccentNeon else VlessHubColors.TextMuted,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "textColor",
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) VlessHubColors.AccentNeon.copy(alpha = 0.08f)
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(item.index) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        if (selected) item.filled else item.outlined,
                        contentDescription = item.label,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.label,
                        color = textColor,
                        fontSize = 10.sp,
                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                        else androidx.compose.ui.text.font.FontWeight.Normal,
                    )
                }
            }
        }
    }
}
