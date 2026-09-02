package com.chobgroup.admin_vlesshub.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chobgroup.admin_vlesshub.core.theme.AdminBackgroundGradient
import com.chobgroup.admin_vlesshub.core.theme.AdminColors
import com.chobgroup.admin_vlesshub.data.AdminKeyStore
import com.chobgroup.admin_vlesshub.data.ProxyApi
import com.chobgroup.admin_vlesshub.data.model.ProxyItem
import com.chobgroup.admin_vlesshub.data.remote.AdminApi
import com.chobgroup.admin_vlesshub.ui.components.AdminGlassCard
import com.chobgroup.admin_vlesshub.ui.icons.AdminIcons
import com.chobgroup.admin_vlesshub.util.ConfigActions
import kotlinx.coroutines.launch

@Composable
fun AdminMTProtoScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var proxies by remember { mutableStateOf<List<ProxyItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var poolSize by remember { mutableIntStateOf(0) }
    var poolWorking by remember { mutableIntStateOf(0) }
    var loadingMore by remember { mutableStateOf(false) }
    var noMore by remember { mutableStateOf(false) }
    var extraProxies by remember { mutableStateOf<List<ProxyItem>>(emptyList()) }
    var renameTarget by remember { mutableStateOf<ProxyItem?>(null) }
    var renameText by remember { mutableStateOf("") }

    fun loadProxies() {
        loading = true
        noMore = false
        extraProxies = emptyList()
        scope.launch {
            try {
                val batch = ProxyApi.fetchProxies()
                proxies = batch.proxies
                poolSize = batch.poolSize
                poolWorking = batch.working
            } catch (e: Exception) {
                snackbar.showSnackbar("Failed: ${e.message}")
            }
            loading = false
        }
    }

    fun loadMore() {
        if (loadingMore || noMore) return
        loadingMore = true
        scope.launch {
            try {
                val batch = ProxyApi.fetchProxies()
                val known = proxies.map { it.link }.toSet() + extraProxies.map { it.link }.toSet()
                val fresh = batch.proxies.filter { it.link !in known }
                extraProxies = extraProxies + fresh
                if (fresh.isEmpty()) noMore = true
            } catch (_: Exception) {
                noMore = true
            }
            loadingMore = false
        }
    }

    fun removeProxy(proxy: ProxyItem) {
        if (!AdminKeyStore.instance.hasKey()) {
            scope.launch { snackbar.showSnackbar("Set admin key in Settings first") }
            return
        }
        scope.launch {
            val id = proxy.id
            if (id == null) {
                proxies = proxies.filterNot { it.link == proxy.link }
                extraProxies = extraProxies.filterNot { it.link == proxy.link }
                snackbar.showSnackbar("Removed locally (no proxy ID)")
                return@launch
            }
            val ok = AdminApi.deleteProxy(id)
            if (ok) {
                proxies = proxies.filterNot { it.link == proxy.link }
                extraProxies = extraProxies.filterNot { it.link == proxy.link }
                snackbar.showSnackbar("Proxy removed")
            } else {
                snackbar.showSnackbar("Failed — check admin key")
            }
        }
    }

    fun doRenameProxy(proxy: ProxyItem, newSource: String) {
        if (!AdminKeyStore.instance.hasKey()) {
            scope.launch { snackbar.showSnackbar("Set admin key in Settings first") }
            return
        }
        scope.launch {
            val id = proxy.id
            if (id == null) {
                snackbar.showSnackbar("Cannot rename (no proxy ID)")
                return@launch
            }
            val ok = AdminApi.renameProxy(id, newSource)
            if (ok) {
                proxies = proxies.map { if (it.link == proxy.link) it.copy(source = newSource) else it }
                extraProxies = extraProxies.map { if (it.link == proxy.link) it.copy(source = newSource) else it }
                snackbar.showSnackbar("Renamed to: $newSource")
            } else {
                snackbar.showSnackbar("Failed — check admin key")
            }
        }
    }

    LaunchedEffect(Unit) { loadProxies() }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().background(AdminBackgroundGradient).padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("MTProto", style = MaterialTheme.typography.headlineSmall, color = AdminColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text("Admin — manage MTProto proxies", color = AdminColors.TextSecondary, fontSize = 12.sp)
                }
                Surface(
                    onClick = { loadProxies() },
                    enabled = !loading,
                    shape = RoundedCornerShape(10.dp),
                    color = AdminColors.AccentRed.copy(alpha = 0.1f),
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = AdminColors.AccentRed, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            if (poolSize > 0) {
                Surface(shape = RoundedCornerShape(50), color = AdminColors.BgCard.copy(alpha = 0.6f), border = BorderStroke(1.dp, AdminColors.CardBorder)) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(AdminColors.SuccessGreen))
                        Spacer(Modifier.width(8.dp))
                        Text(if (poolWorking > 0) "$poolWorking working of $poolSize" else "$poolSize proxies", color = AdminColors.TextSecondary, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AdminColors.AccentRed) }
                proxies.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No proxies — tap Refresh", color = AdminColors.TextMuted, fontSize = 14.sp) }
                else -> {
                    val allProxies = proxies + extraProxies
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(allProxies.distinctBy { it.link }, key = { it.link }) { proxy ->
                            AdminProxyCard(
                                proxy = proxy,
                                onCopy = {
                                    scope.launch {
                                        val link = ConfigActions.normalizeTelegramProxyLink(proxy.link)
                                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("proxy", link)))
                                        snackbar.showSnackbar("Telegram proxy link copied")
                                    }
                                },
                                onShare = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, proxy.link) }, "Share proxy")) },
                                onOpen = { ConfigActions.openTelegramProxy(context, proxy.link) },
                                onRename = { renameTarget = proxy; renameText = proxy.source ?: proxy.host },
                                onRemove = { removeProxy(proxy) },
                            )
                        }
                        if (!noMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    if (loadingMore) CircularProgressIndicator(color = AdminColors.AccentRed, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                    else Surface(onClick = { loadMore() }, shape = RoundedCornerShape(12.dp), color = AdminColors.AccentRed.copy(alpha = 0.14f), border = BorderStroke(1.dp, AdminColors.AccentRed.copy(alpha = 0.4f))) {
                                        Icon(AdminIcons.Speed, contentDescription = "More", modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).size(18.dp), tint = AdminColors.AccentRed)
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
    }

    renameTarget?.let { proxy ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Proxy", color = AdminColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Source / Label", color = AdminColors.TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AdminColors.AccentRed,
                        unfocusedBorderColor = AdminColors.GlassBorder,
                        focusedTextColor = AdminColors.TextPrimary,
                        unfocusedTextColor = AdminColors.TextPrimary,
                        cursorColor = AdminColors.AccentRed,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) doRenameProxy(proxy, renameText.trim())
                    renameTarget = null
                }) { Text("Rename", color = AdminColors.AccentRed) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel", color = AdminColors.TextSecondary) }
            },
            containerColor = AdminColors.BgCard,
        )
    }
}

@Composable
private fun AdminProxyCard(proxy: ProxyItem, onCopy: () -> Unit, onShare: () -> Unit, onOpen: () -> Unit, onRename: () -> Unit, onRemove: () -> Unit) {
    AdminGlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(AdminColors.SuccessGreen))
            Spacer(Modifier.width(10.dp))
            val sourceLabel = proxy.source?.trim()?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("@")) it else "@$it" }
            Column(Modifier.weight(1f)) {
                Text(sourceLabel ?: proxy.host, color = AdminColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sourceLabel != null) Text("${proxy.host}:${proxy.port}", color = AdminColors.TextMuted, fontSize = 11.sp)
            }
            if (sourceLabel == null) Text(":${proxy.port}", color = AdminColors.TextMuted, fontSize = 14.sp)
        }
        proxy.source?.takeIf { it.isNotBlank() }?.let { Spacer(Modifier.height(6.dp)); Text("source: $it", color = AdminColors.TextMuted, fontSize = 11.sp) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProxyChip("Copy", AdminIcons.ContentCopy, onCopy)
            ProxyChip("Share", Icons.Default.Share, onShare)
            ProxyChip("Open", Icons.Filled.Refresh, onOpen)
            if (AdminKeyStore.instance.hasKey()) {
                ProxyChip("Rename", AdminIcons.Edit, onRename, tint = AdminColors.AccentOrange)
                ProxyChip("Remove", AdminIcons.Delete, onRemove, tint = AdminColors.ErrorRed)
            }
        }
    }
}

@Composable
private fun ProxyChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, tint: androidx.compose.ui.graphics.Color = AdminColors.AccentRed) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = tint.copy(alpha = 0.1f)) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
            Spacer(Modifier.width(6.dp))
            Text(label, color = tint, fontSize = 12.sp)
        }
    }
}
