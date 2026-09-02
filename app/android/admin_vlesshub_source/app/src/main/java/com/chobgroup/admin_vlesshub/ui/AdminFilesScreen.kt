package com.chobgroup.admin_vlesshub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.IconButton
import com.chobgroup.admin_vlesshub.core.theme.AdminBackgroundGradient
import com.chobgroup.admin_vlesshub.core.theme.AdminColors
import com.chobgroup.admin_vlesshub.data.AdminKeyStore
import com.chobgroup.admin_vlesshub.data.HiddenStore
import com.chobgroup.admin_vlesshub.data.model.VpnFile
import com.chobgroup.admin_vlesshub.data.remote.AdminApi
import com.chobgroup.admin_vlesshub.data.repository.RemoteVpnFileRepository
import kotlinx.coroutines.launch
import com.chobgroup.admin_vlesshub.ui.components.AdminGlassCard
import com.chobgroup.admin_vlesshub.ui.icons.AdminIcons
import com.chobgroup.admin_vlesshub.util.TimeFormat

@Composable
fun AdminFilesScreen() {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var files by remember { mutableStateOf<List<VpnFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var longPressTarget by remember { mutableStateOf<VpnFile?>(null) }

    LaunchedEffect(Unit) {
        val fetched = RemoteVpnFileRepository().fetchFiles()
            .filter { !HiddenStore.instance.isFileHidden(it.id) }
        if (fetched.isNotEmpty()) files = fetched
        loading = false
    }

    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) {
            loading = true
            val fetched = RemoteVpnFileRepository().fetchFiles()
                .filter { !HiddenStore.instance.isFileHidden(it.id) }
            if (fetched.isNotEmpty()) files = fetched
            loading = false
        }
    }

    fun hideFile(file: VpnFile) {
        HiddenStore.instance.hideFile(file.id)
        files = files.filterNot { it.id == file.id }
        scope.launch { snackbar.showSnackbar("File hidden (local only): ${file.filename}") }
    }

    fun removeFile(file: VpnFile) {
        if (!AdminKeyStore.instance.hasKey()) {
            scope.launch { snackbar.showSnackbar("Set admin key in Settings first") }
            return
        }
        scope.launch {
            val ok = AdminApi.deleteFile(file.id)
            if (ok) {
                files = files.filterNot { it.id == file.id }
                snackbar.showSnackbar("File removed: ${file.filename}")
            } else {
                snackbar.showSnackbar("Failed — check admin key")
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().background(AdminBackgroundGradient).padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Files", style = MaterialTheme.typography.headlineSmall, color = AdminColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text("Admin — manage VPN config files", color = AdminColors.TextSecondary, fontSize = 12.sp)
                }
                Surface(
                    onClick = { refreshKey++ },
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

            when {
                loading && files.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AdminColors.AccentRed)
                }
                files.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No files — tap Refresh", color = AdminColors.TextMuted, fontSize = 14.sp)
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    items(files, key = { it.id }) { file ->
                        AdminFileCard(
                            file = file,
                            onLongPress = { longPressTarget = file },
                            onHide = { hideFile(file) },
                            onRemove = { removeFile(file) },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
    }

    longPressTarget?.let { file ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { longPressTarget = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AdminColors.BgCard,
                border = BorderStroke(1.dp, AdminColors.GlassBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("File Options", color = AdminColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { longPressTarget = null }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = AdminColors.TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("\"${file.filename}\"", color = AdminColors.TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            onClick = { longPressTarget = null; hideFile(file) },
                            shape = RoundedCornerShape(10.dp),
                            color = AdminColors.AccentOrange.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, AdminColors.AccentOrange.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(AdminIcons.VisibilityOff, contentDescription = null, tint = AdminColors.AccentOrange, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Hide", color = AdminColors.AccentOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        Surface(
                            onClick = { longPressTarget = null; removeFile(file) },
                            shape = RoundedCornerShape(10.dp),
                            color = AdminColors.ErrorRed.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, AdminColors.ErrorRed.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(AdminIcons.Delete, contentDescription = null, tint = AdminColors.ErrorRed, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Delete", color = AdminColors.ErrorRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AdminFileCard(file: VpnFile, onLongPress: () -> Unit, onHide: () -> Unit, onRemove: () -> Unit) {
    AdminGlassCard(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = Modifier.combinedClickable(onLongClick = onLongPress, onClick = {}),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AdminColors.BgDarkEmerald.copy(alpha = 0.8f),
                border = BorderStroke(1.dp, AdminColors.GlassBorder),
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Text(file.format.take(4).uppercase(), fontSize = 12.sp, color = AdminColors.AccentRed, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.filename, color = AdminColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                val fileSource = file.sourceChannel?.let { if (it.startsWith("@")) it else "@$it" }
                Text(
                    buildString {
                        if (fileSource != null) append("source: $fileSource · ")
                        append("protocol: ${file.format}")
                    },
                    color = AdminColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(onClick = onHide, shape = RoundedCornerShape(8.dp), color = AdminColors.AccentOrange.copy(alpha = 0.1f)) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(com.chobgroup.admin_vlesshub.ui.icons.AdminIcons.VisibilityOff, contentDescription = "Hide", modifier = Modifier.size(14.dp), tint = AdminColors.AccentOrange)
                            Spacer(Modifier.width(4.dp))
                            Text("Hide", fontSize = 10.sp, color = AdminColors.AccentOrange)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                val time = TimeFormat.formatScrapedTime(file.uploadedAt)
                Text(
                    buildString {
                        append(formatFileSize(file.sizeBytes))
                        if (time != null) append(" · 🕗 $time")
                        if (file.configCount > 0) append(" · ${file.configCount} configs")
                    },
                    color = AdminColors.TextMuted, fontSize = 10.5.sp,
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
