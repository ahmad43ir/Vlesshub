package com.chobgroup.admin_vlesshub.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import com.chobgroup.admin_vlesshub.core.theme.AdminBackgroundGradient
import com.chobgroup.admin_vlesshub.core.theme.AdminColors
import com.chobgroup.admin_vlesshub.data.AdminKeyStore
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
    var renameTarget by remember { mutableStateOf<VpnFile?>(null) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val fetched = RemoteVpnFileRepository().fetchFiles()
        if (fetched.isNotEmpty()) files = fetched
        loading = false
    }

    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) {
            loading = true
            val fetched = RemoteVpnFileRepository().fetchFiles()
            if (fetched.isNotEmpty()) files = fetched
            loading = false
        }
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

    fun doRenameFile(file: VpnFile, newName: String) {
        if (!AdminKeyStore.instance.hasKey()) {
            scope.launch { snackbar.showSnackbar("Set admin key in Settings first") }
            return
        }
        scope.launch {
            val ok = AdminApi.renameFile(file.id, newName)
            if (ok) {
                files = files.map { if (it.id == file.id) it.copy(filename = newName) else it }
                snackbar.showSnackbar("Renamed to: $newName")
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
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = AdminColors.AccentRed, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh", color = AdminColors.AccentRed, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
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
                            onRename = { renameTarget = file; renameText = file.filename },
                            onRemove = { removeFile(file) },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
    }

    renameTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename File", color = AdminColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Filename", color = AdminColors.TextSecondary) },
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
                    if (renameText.isNotBlank()) doRenameFile(file, renameText.trim())
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
private fun AdminFileCard(file: VpnFile, onRename: () -> Unit, onRemove: () -> Unit) {
    AdminGlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
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
            if (AdminKeyStore.instance.hasKey()) {
                Spacer(Modifier.size(8.dp))
                Column {
                    Surface(onClick = onRename, shape = RoundedCornerShape(10.dp), color = AdminColors.AccentOrange.copy(alpha = 0.1f)) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AdminIcons.Edit, contentDescription = "Rename", tint = AdminColors.AccentOrange, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Rename", color = AdminColors.AccentOrange, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Surface(onClick = onRemove, shape = RoundedCornerShape(10.dp), color = AdminColors.ErrorRed.copy(alpha = 0.1f)) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AdminIcons.Delete, contentDescription = "Remove", tint = AdminColors.ErrorRed, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Remove", color = AdminColors.ErrorRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
