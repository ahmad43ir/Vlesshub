package com.chobgroup.admin_vlesshub.ui

import android.content.ClipData
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import com.chobgroup.admin_vlesshub.config.ConfigNormalizer
import com.chobgroup.admin_vlesshub.core.theme.AdminBackgroundGradient
import com.chobgroup.admin_vlesshub.core.theme.AdminColors
import com.chobgroup.admin_vlesshub.data.AdminKeyStore
import com.chobgroup.admin_vlesshub.data.HiddenStore
import com.chobgroup.admin_vlesshub.data.model.ConfigFormat
import com.chobgroup.admin_vlesshub.data.model.VpnServer
import com.chobgroup.admin_vlesshub.data.remote.AdminApi
import com.chobgroup.admin_vlesshub.data.remote.GeoIpResolver
import com.chobgroup.admin_vlesshub.data.repository.RemoteServerRepository
import com.chobgroup.admin_vlesshub.ui.components.AdminGlassCard
import com.chobgroup.admin_vlesshub.ui.components.AdminStatusChip
import com.chobgroup.admin_vlesshub.ui.icons.AdminIcons
import com.chobgroup.admin_vlesshub.config.XrayTestManager
import com.chobgroup.admin_vlesshub.util.ConfigUtils
import com.chobgroup.admin_vlesshub.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

@Composable
fun AdminLinksScreen() {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var servers by remember { mutableStateOf<List<VpnServer>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var pinging by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var renameTarget by remember { mutableStateOf<VpnServer?>(null) }
    var renameText by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var testingRealDelay by remember { mutableStateOf(setOf<String>()) }
    var batchTesting by remember { mutableStateOf(false) }
    var batchProgress by remember { mutableStateOf(0 to 0) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val fetched = RemoteServerRepository().fetchServers()
            .filter { it.id == null || !HiddenStore.instance.isServerHidden(it.id!!) }
        if (fetched.isNotEmpty()) servers = fetched
        loading = false
        enrichGeoFlags(servers) { servers = it }
    }

    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) {
            loading = true
            val fetched = RemoteServerRepository().fetchServers()
                .filter { it.id == null || !HiddenStore.instance.isServerHidden(it.id!!) }
            if (fetched.isNotEmpty()) servers = fetched
            loading = false
            enrichGeoFlags(servers) { servers = it }
        }
    }

    fun pingAll() {
        scope.launch {
            pinging = true
            val updated = servers.toMutableList()
            for (i in updated.indices) {
                val ms = pingServer(updated[i])
                updated[i] = updated[i].copy(pingMs = ms)
                servers = updated.toList()
            }
            pinging = false
        }
    }

    fun hideServer(server: VpnServer) {
        val id = server.id
        if (id == null) {
            scope.launch { snackbar.showSnackbar("Cannot hide (no server ID)") }
            return
        }
        HiddenStore.instance.hideServer(id)
        servers = servers.filterNot { it.id == id }
        scope.launch { snackbar.showSnackbar("Server hidden (local only)") }
    }

    fun removeServer(server: VpnServer) {
        if (!AdminKeyStore.instance.hasKey()) {
            scope.launch { snackbar.showSnackbar("Set admin key in Settings first") }
            return
        }
        scope.launch {
            val id = server.id
            if (id == null) {
                servers = servers.filterNot { it.rawConfig == server.rawConfig }
                snackbar.showSnackbar("Removed locally (no server ID)")
                return@launch
            }
            val ok = AdminApi.deleteServer(id)
            if (ok) {
                servers = servers.filterNot { it.rawConfig == server.rawConfig }
                snackbar.showSnackbar("Server removed")
            } else {
                snackbar.showSnackbar("Failed — check admin key")
            }
        }
    }

    fun doRename(server: VpnServer, newName: String) {
        if (!AdminKeyStore.instance.hasKey()) {
            scope.launch { snackbar.showSnackbar("Set admin key in Settings first") }
            return
        }
        scope.launch {
            val id = server.id
            if (id == null) {
                snackbar.showSnackbar("Cannot rename (no server ID)")
                return@launch
            }
            val ok = AdminApi.renameServer(id, newName)
            if (ok) {
                val idx = servers.indexOfFirst { it.rawConfig == server.rawConfig }
                if (idx >= 0) {
                    servers = servers.toMutableList().also { it[idx] = it[idx].copy(name = newName) }
                }
                snackbar.showSnackbar("Renamed to: $newName")
            } else {
                snackbar.showSnackbar("Failed — check admin key")
            }
        }
    }

    fun tcpingAll() {
        if (pinging || loading) return
        scope.launch {
            pinging = true
            val visible = servers.distinctBy { it.rawConfig }
            for ((idx, server) in visible.withIndex()) {
                val ms = pingServer(server)
                val sIdx = servers.indexOfFirst { it.rawConfig == server.rawConfig }
                if (sIdx >= 0) {
                    servers = servers.toMutableList().also { it[sIdx] = it[sIdx].copy(pingMs = ms) }
                }
                batchProgress = (idx + 1) to visible.size
            }
            pinging = false
            val reachable = servers.count { it.pingMs != null && it.pingMs!! >= 0 }
            snackbar.showSnackbar("TCPing complete: $reachable/${visible.size} reachable")
        }
    }

    fun realDelayAll() {
        if (pinging || batchTesting || loading) return
        scope.launch {
            batchTesting = true
            val visible = servers.distinctBy { it.rawConfig }
            batchProgress = 0 to visible.size
            for ((idx, server) in visible.withIndex()) {
                testingRealDelay = testingRealDelay + server.rawConfig
                val result = XrayTestManager.testConfig(context, server.rawConfig)
                val status = if (result.latencyMs >= 0) com.chobgroup.admin_vlesshub.data.model.ValidationStatus.WORKING else com.chobgroup.admin_vlesshub.data.model.ValidationStatus.FAILED
                val sIdx = servers.indexOfFirst { it.rawConfig == server.rawConfig }
                if (sIdx >= 0) {
                    servers = servers.toMutableList().also {
                        it[sIdx] = it[sIdx].copy(
                            realDelayMs = result.latencyMs,
                            validationStatus = status,
                            validationError = result.error,
                        )
                    }
                }
                testingRealDelay = testingRealDelay - server.rawConfig
                batchProgress = (idx + 1) to visible.size
            }
            batchTesting = false
            val working = servers.count { it.validationStatus == com.chobgroup.admin_vlesshub.data.model.ValidationStatus.WORKING }
            snackbar.showSnackbar("Test complete: $working/${visible.size} working")
        }
    }

    fun removeTimedOut() {
        if (!AdminKeyStore.instance.hasKey()) {
            scope.launch { snackbar.showSnackbar("Set admin key in Settings first") }
            return
        }
        scope.launch {
            val timedOut = servers.filter { it.pingMs == null || it.pingMs == -1 }
            if (timedOut.isEmpty()) {
                snackbar.showSnackbar("No timed-out servers to remove")
                return@launch
            }
            var removed = 0
            for (server in timedOut) {
                val id = server.id
                if (id != null && AdminApi.deleteServer(id)) {
                    removed++
                }
            }
            servers = servers.filterNot { it.pingMs == null || it.pingMs == -1 }
            snackbar.showSnackbar("Removed $removed timed-out server(s)")
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
                    Text("Links", style = MaterialTheme.typography.headlineSmall, color = AdminColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    if (pinging || batchTesting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(10.dp), color = AdminColors.AccentRed, strokeWidth = 1.5.dp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (batchTesting) "Testing ${batchProgress.first}/${batchProgress.second}…"
                                else "TCPing ${batchProgress.first}/${batchProgress.second}…",
                                color = AdminColors.AccentRed,
                                fontSize = 11.sp,
                            )
                        }
                    } else {
                        Text("Admin — copy, ping, refresh, rename, or remove", color = AdminColors.TextSecondary, fontSize = 12.sp)
                    }
                }
                // Ping button — TCPing + real delay for all visible
                Surface(
                    onClick = {
                        if (!pinging && !batchTesting && !loading) {
                            scope.launch {
                                pinging = true
                                val visible = servers.distinctBy { it.rawConfig }
                                for ((idx, server) in visible.withIndex()) {
                                    val ms = pingServer(server)
                                    val sIdx = servers.indexOfFirst { it.rawConfig == server.rawConfig }
                                    if (sIdx >= 0) servers = servers.toMutableList().also { it[sIdx] = it[sIdx].copy(pingMs = ms) }
                                    batchProgress = (idx + 1) to visible.size
                                }
                                pinging = false
                                batchTesting = true
                                batchProgress = 0 to visible.size
                                for ((idx, server) in visible.withIndex()) {
                                    testingRealDelay = testingRealDelay + server.rawConfig
                                    val result = XrayTestManager.testConfig(context, server.rawConfig)
                                    val sIdx = servers.indexOfFirst { it.rawConfig == server.rawConfig }
                                    if (sIdx >= 0) servers = servers.toMutableList().also {
                                        it[sIdx] = it[sIdx].copy(realDelayMs = result.latencyMs, validationError = result.error)
                                    }
                                    testingRealDelay = testingRealDelay - server.rawConfig
                                    batchProgress = (idx + 1) to visible.size
                                }
                                batchTesting = false
                                val real = servers.count { it.realDelayMs != null && it.realDelayMs!! >= 0 }
                                snackbar.showSnackbar("Test complete: $real/${visible.size} working")
                            }
                        }
                    },
                    enabled = !loading && !pinging && !batchTesting && servers.isNotEmpty(),
                ) {
                    if (pinging || batchTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AdminColors.AccentRed, strokeWidth = 2.dp)
                    } else {
                        Icon(AdminIcons.Speed, contentDescription = "Test servers", tint = AdminColors.AccentRed)
                    }
                }
                // Refresh button
                Surface(
                    onClick = { if (!batchTesting) refreshKey++ },
                    enabled = !loading && !pinging && !batchTesting,
                    shape = RoundedCornerShape(10.dp),
                    color = AdminColors.AccentRed.copy(alpha = 0.1f),
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = AdminColors.AccentRed, modifier = Modifier.size(16.dp))
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, enabled = !loading) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = AdminColors.AccentRed)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("TCPing all configs") },
                            enabled = !pinging && !batchTesting && !loading && servers.isNotEmpty(),
                            onClick = { menuOpen = false; tcpingAll() },
                        )
                        DropdownMenuItem(
                            text = { Text("Real delay test") },
                            enabled = !pinging && !batchTesting && !loading && servers.isNotEmpty(),
                            onClick = { menuOpen = false; realDelayAll() },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove all timed-out links") },
                            onClick = { menuOpen = false; removeTimedOut() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AdminColors.AccentRed)
                }
                servers.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No servers — tap Refresh", color = AdminColors.TextMuted, fontSize = 14.sp)
                }
                else -> {
                    val distinct = servers.distinctBy { it.rawConfig }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(distinct, key = { it.rawConfig }) { server ->
                            AdminConfigCard(
                                server = server,
                                testingRealDelay = server.rawConfig in testingRealDelay,
                                onCopy = {
                                    scope.launch {
                                        val patched = ConfigUtils.patchConfigForClient(server.rawConfig)
                                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("config", patched)))
                                        snackbar.showSnackbar("Config copied (patched for client)")
                                    }
                                },
                                onRename = { renameTarget = server; renameText = server.name },
                                onHide = { hideServer(server) },
                                onRemove = { removeServer(server) },
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
    }

    renameTarget?.let { server ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Server", color = AdminColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Name", color = AdminColors.TextSecondary) },
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
                    if (renameText.isNotBlank()) doRename(server, renameText.trim())
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
private fun AdminConfigCard(
    server: VpnServer,
    testingRealDelay: Boolean = false,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onRemove: () -> Unit,
) {
    AdminGlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(server.name, color = AdminColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(2.dp))
                val formatLabel = if (server.configFormat == ConfigFormat.LINK) null else server.configFormat.displayName
                val meta = buildList {
                    server.sourceChannel?.let { add("source: $it") }
                    add("${server.type.displayName} · ${server.transport.displayName}")
                    add("${server.flag} ${server.country}".trim())
                    if (formatLabel != null) add(formatLabel)
                    server.createdAt?.let { iso -> TimeFormat.formatScrapedTime(iso)?.let { add("🕐 $it") } }
                }
                Text(meta.joinToString(" · "), color = AdminColors.TextMuted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // TCPing chip
            server.pingMs?.let { ms ->
                Spacer(Modifier.size(8.dp))
                val chipColor = when {
                    ms < 0 -> AdminColors.ErrorRed
                    ms < 150 -> AdminColors.SuccessGreen
                    ms < 400 -> AdminColors.AccentOrange
                    else -> AdminColors.ErrorRed
                }
                AdminStatusChip(text = if (ms < 0) "Failed" else "${ms}ms", color = chipColor)
            }
            // Real delay chip
            if (testingRealDelay) {
                Spacer(Modifier.size(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = AdminColors.AccentRed, strokeWidth = 2.dp)
            } else server.realDelayMs?.let { ms ->
                Spacer(Modifier.size(8.dp))
                val chipColor = when {
                    ms < 0 -> AdminColors.ErrorRed
                    ms < 300 -> AdminColors.SuccessGreen
                    ms < 800 -> AdminColors.AccentOrange
                    else -> AdminColors.ErrorRed
                }
                AdminStatusChip(text = if (ms < 0) "Failed" else "${ms}ms", color = chipColor)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminActionButton(label = "Copy", icon = AdminIcons.ContentCopy, onClick = onCopy)
            AdminActionButton(label = "Hide", icon = AdminIcons.VisibilityOff, tint = AdminColors.AccentOrange, onClick = onHide)
            if (AdminKeyStore.instance.hasKey()) {
                AdminActionButton(label = "Rename", icon = AdminIcons.Edit, tint = AdminColors.AccentOrange, onClick = onRename)
                AdminActionButton(label = "Remove", icon = AdminIcons.Delete, tint = AdminColors.ErrorRed, onClick = onRemove)
            }
        }
    }
}

@Composable
private fun AdminActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color = AdminColors.AccentRed,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(10.dp), color = tint.copy(alpha = if (enabled) 0.1f else 0.05f)) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp), tint = tint)
        }
    }
}

private suspend fun pingServer(server: VpnServer): Int = withContext(Dispatchers.IO) {
    runCatching {
        val config = ConfigNormalizer.normalize(
            raw = server.rawConfig,
            configFormat = server.configFormat.name.lowercase(),
            protocol = server.type.wireName,
        )
        val useTls = config.security.equals("tls", ignoreCase = true) ||
                config.security.equals("reality", ignoreCase = true)
        val isVless = config.protocol.wireName == "vless"
        val isTrojan = config.protocol.wireName == "trojan"
        val start = System.nanoTime()
        if (useTls && isVless) {
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
                override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<TrustManager>(tm), null)
            val tcp = Socket()
            try {
                tcp.connect(InetSocketAddress(config.address, config.port), 5000)
                val ssl = ctx.socketFactory.createSocket(
                    tcp, config.sni ?: config.address, config.port, true
                ) as SSLSocket
                ssl.soTimeout = 5000
                ssl.startHandshake()
                val uuid = config.uuid?.toByteArray(Charsets.UTF_8)
                    ?: throw IllegalStateException("no UUID")
                val header = buildVlessHeader(uuid, config.address, config.port)
                ssl.outputStream.write(header)
                ssl.outputStream.flush()
                val resp = ByteArray(16)
                val n = ssl.inputStream.read(resp)
                if (n < 2 || resp[1] != 0x00.toByte()) {
                    throw Exception("VLESS handshake rejected (status=${if (n >= 2) resp[1].toInt() else -1})")
                }
                ssl.close()
            } finally {
                tcp.close()
            }
        } else if (useTls && isTrojan) {
            // Trojan: send SHA224(password)\r\n — server replies with 58
            // random bytes if accepted, or closes the connection if rejected.
            val password = config.uuid ?: ""
            if (password.isEmpty()) throw Exception("Trojan config has no password")
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
                override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<TrustManager>(tm), null)
            val tcp = Socket()
            try {
                tcp.connect(InetSocketAddress(config.address, config.port), 5000)
                val ssl = ctx.socketFactory.createSocket(
                    tcp, config.sni ?: config.address, config.port, true
                ) as SSLSocket
                ssl.soTimeout = 5000
                ssl.startHandshake()
                // Send Trojan auth: SHA224(password) + CRLF
                val hash = java.security.MessageDigest.getInstance("SHA-224")
                    .digest(password.toByteArray(Charsets.UTF_8))
                val authLine = hash.joinToString("") { "%02x".format(it) } + "\r\n"
                ssl.outputStream.write(authLine.toByteArray(Charsets.UTF_8))
                ssl.outputStream.flush()
                // Read response — valid Trojan server sends exactly 58 bytes
                // of random data on success, or closes the connection on failure.
                val resp = ByteArray(64)
                val n = ssl.inputStream.read(resp)
                if (n < 58) {
                    throw Exception("Trojan auth rejected (password wrong or backend down)")
                }
                ssl.close()
            } finally {
                tcp.close()
            }
        } else if (useTls) {
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
                override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<TrustManager>(tm), null)
            val tcp = Socket()
            try {
                tcp.connect(InetSocketAddress(config.address, config.port), 5000)
                val ssl = ctx.socketFactory.createSocket(
                    tcp, config.sni ?: config.address, config.port, true
                ) as SSLSocket
                ssl.soTimeout = 5000
                ssl.startHandshake()
                ssl.close()
            } finally {
                tcp.close()
            }
        } else {
            val socket = Socket()
            try { socket.connect(InetSocketAddress(config.address, config.port), 5000) }
            finally { socket.close() }
        }
        ((System.nanoTime() - start) / 1_000_000).toInt()
    }.getOrDefault(-1)
}

private fun buildVlessHeader(uuid: ByteArray, address: String, port: Int): ByteArray {
    val buf = mutableListOf<Byte>()
    buf.add(0x00)
    buf.addAll(uuid.toList())
    buf.add(0x00)
    buf.add(0x01)
    if (address.all { it.isDigit() || it == '.' }) {
        val parts = address.split('.')
        if (parts.size == 4) {
            buf.add(0x01)
            parts.forEach { buf.add(it.toInt().toByte()) }
        } else {
            buf.add(0x03)
            buf.add(address.length.toByte())
            buf.addAll(address.toByteArray(Charsets.UTF_8).toList())
        }
    } else {
        buf.add(0x03)
        buf.add(address.length.toByte())
        buf.addAll(address.toByteArray(Charsets.UTF_8).toList())
    }
    buf.add(((port shr 8) and 0xFF).toByte())
    buf.add((port and 0xFF).toByte())
    buf.add(0x00)
    return buf.toByteArray()
}

private suspend fun enrichGeoFlags(servers: List<VpnServer>, onUpdate: (List<VpnServer>) -> Unit) {
    if (servers.isEmpty()) return
    val updated = servers.toMutableList()
    for (i in updated.indices) {
        val address = runCatching {
            ConfigNormalizer.normalize(
                raw = updated[i].rawConfig,
                configFormat = updated[i].configFormat.name.lowercase(),
                protocol = updated[i].type.wireName,
            ).address
        }.getOrNull() ?: continue
        val info = GeoIpResolver.lookupHost(address) ?: continue
        updated[i] = updated[i].copy(flag = GeoIpResolver.flagEmoji(info.countryCode), country = info.country)
        onUpdate(updated.toList())
    }
}
