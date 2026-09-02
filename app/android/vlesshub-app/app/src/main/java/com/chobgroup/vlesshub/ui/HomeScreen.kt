package com.chobgroup.vlesshub.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chobgroup.vlesshub.ads.AdiveryAdsManager
import com.chobgroup.vlesshub.core.theme.VlessHubColors
import com.chobgroup.vlesshub.data.ProxyApi
import com.chobgroup.vlesshub.data.ProxyItem
import com.chobgroup.vlesshub.util.ConfigActions
import com.chobgroup.vlesshub.data.repository.ServerCacheStore
import com.chobgroup.vlesshub.ui.icons.AppIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// â”€â”€â”€ State â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

sealed interface HomeUiState {
    data object Initial : HomeUiState
    data object Loading : HomeUiState
    data class Success(val batch: ProxyApi.ProxyBatch) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Initial)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** First load happens without an interstitial; refreshes are ad-gated. */
    var hasLoadedOnce by mutableStateOf(false)
        private set

    /** Extra pages appended by the "More" button (10 per press). */
    val extraProxies = mutableStateListOf<ProxyItem>()

    var loadingMore by mutableStateOf(false)
        private set

    /** Set when another page isn't available — hides the "More" button. */
    var noMore by mutableStateOf(false)
        private set

    init {
        loadProxies()
    }

    fun loadProxies() {
        _uiState.value = HomeUiState.Loading
        extraProxies.clear()
        noMore = false
        viewModelScope.launch {
            try {
                val batch = ProxyApi.fetchProxies()
                hasLoadedOnce = true
                _uiState.value = HomeUiState.Success(batch)
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Failed to load proxies")
            }
        }
    }

    /** Appends the next 10 proxies (a fresh random batch minus dupes). */
    fun loadMore() {
        if (loadingMore || noMore) return
        loadingMore = true
        viewModelScope.launch {
            try {
                val batch = ProxyApi.fetchProxies()
                val known = (uiState.value as? HomeUiState.Success)
                    ?.batch?.proxies.orEmpty().map { it.link }.toSet() +
                    extraProxies.map { it.link }.toSet()
                val fresh = batch.proxies.filter { it.link !in known }
                extraProxies.addAll(fresh)
                if (fresh.isEmpty()) noMore = true
            } catch (_: Exception) {
                noMore = true
            } finally {
                loadingMore = false
            }
        }
    }
}

// â”€â”€â”€ Screen â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // â”€â”€ Ad gates (mirrors RootNet v2.3) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    val cache = ServerCacheStore.instance
    var actionCount by rememberSaveable { mutableIntStateOf(cache.actionCount("proxies")) }
    var countedProxies by remember { mutableStateOf(cache.countedConfigs("proxies")) }

    fun performGatedAction(proxyLink: String, action: () -> Unit) {
        if (proxyLink !in countedProxies) {
            countedProxies = countedProxies + proxyLink
            actionCount++
            cache.setCountedConfigs(countedProxies, "proxies")
            cache.setActionCount(actionCount, "proxies")
        }
        if (actionCount >= 5) {
            actionCount = 0
            countedProxies = emptySet()
            cache.setActionCount(0, "proxies")
            cache.setCountedConfigs(emptySet(), "proxies")
            AdiveryAdsManager.maybeShowInterstitial(onFinished = { action() })
        } else {
            action()
        }
    }

    val onGetProxies: () -> Unit = {
        if (viewModel.hasLoadedOnce) {
            val doRefresh: () -> Unit = {
                viewModel.loadProxies()
                cache.resetActionTracking("proxies")
                actionCount = 0
                countedProxies = emptySet()
            }
            if (!AdiveryAdsManager.maybeShowInterstitial(onFinished = doRefresh)) {
                doRefresh()
            }
        } else {
            viewModel.loadProxies()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = VlessHubColors.BgDeepForest,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(VlessHubColors.BgDarkEmerald, VlessHubColors.BgDeepForest),
                    ),
                )
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            // Header
            Text(
                "MTProto",
                color = VlessHubColors.AccentNeon,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Free MTProto proxies for Telegram â€” 10 random picks per batch",
                color = VlessHubColors.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))

            // Status
            when (val state = uiState) {
                is HomeUiState.Error -> ErrorBanner(state.message)
                else -> {}
            }
            Spacer(Modifier.height(12.dp))

            // Get proxies button
            Button(
                onClick = onGetProxies,
                enabled = uiState !is HomeUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VlessHubColors.AccentNeon,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (uiState is HomeUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (viewModel.hasLoadedOnce) "Refresh proxies" else "Get 10 proxies",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Proxy list / states
            when (val state = uiState) {
                is HomeUiState.Success -> {
                    val allProxies = state.batch.proxies + viewModel.extraProxies
                    if (allProxies.isEmpty()) {
                        CenteredHint("No proxies available right now â€” try again later.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(allProxies.distinctBy { it.link }, key = { it.link }) { proxy ->
                                ProxyCard(
                                    proxy = proxy,
                                    onCopy = {
                                        performGatedAction(proxy.link) {
                                            scope.launch {
                                                val link = ConfigActions.normalizeTelegramProxyLink(proxy.link)
                                                clipboard.setClipEntry(
                                                    ClipEntry(ClipData.newPlainText("proxy", link)),
                                                )
                                                snackbarHostState.showSnackbar("Telegram proxy link copied")
                                            }
                                        }
                                    },
                                    onShare = { performGatedAction(proxy.link) { shareProxy(context, proxy.link) } },
                                    onOpen = { performGatedAction(proxy.link) { openInTelegram(context, proxy.link) } },
                                )
                            }
                            if (!viewModel.noMore) {
                                item {
                                    if (viewModel.loadingMore) {
                                        Box(
                                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(
                                                color = VlessHubColors.AccentNeon,
                                                modifier = Modifier.size(22.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        }
                                    } else {
                                        MoreButton(label = "More") {
                                            // Picture ad before each extra batch.
                                            val shown = AdiveryAdsManager.maybeShowInterstitial(
                                                onFinished = { viewModel.loadMore() },
                                            )
                                            if (!shown) {
                                                viewModel.loadMore()
                                            }
                                        }
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }
                is HomeUiState.Error -> CenteredHint("Couldn't load proxies.\n${state.message}")
                is HomeUiState.Initial, is HomeUiState.Loading -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = VlessHubColors.AccentNeon)
                }
            }

        }


        }
    }
}

// Pieces â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun PoolStatusChip(working: Int, poolSize: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = VlessHubColors.CardTranslucent,
        border = BorderStroke(1.dp, VlessHubColors.CardBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(VlessHubColors.AccentNeon),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (working > 0) {
                    "$working working of $poolSize in the pool"
                } else {
                    "$poolSize proxies in the pool"
                },
                color = VlessHubColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = VlessHubColors.ErrorRed.copy(alpha = 0.12f),
    ) {
        Text(
            text = "âš  $message",
            modifier = Modifier.padding(12.dp),
            color = VlessHubColors.ErrorRed,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ColumnScope.CenteredHint(text: String) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = VlessHubColors.TextMuted,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun ProxyCard(
    proxy: ProxyItem,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = VlessHubColors.CardTranslucent,
        border = BorderStroke(1.dp, VlessHubColors.CardBorder),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(VlessHubColors.AccentLime),
                )
                Spacer(Modifier.width(10.dp))
                // Naming template: the channel the proxy was scraped from is
                // the card's name; the raw host:port drops to a detail line.
                val sourceLabel = proxy.source?.trim()?.takeIf { it.isNotBlank() }?.let {
                    if (it.startsWith("@")) it else "@$it"
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = sourceLabel ?: proxy.host,
                        color = VlessHubColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (sourceLabel != null) {
                        Text(
                            text = "${proxy.host}:${proxy.port}",
                            color = VlessHubColors.TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
                Spacer(Modifier.weight(0.3f))
                if (sourceLabel == null) {
                    Text(
                        text = ":${proxy.port}",
                        color = VlessHubColors.TextMuted,
                        fontSize = 14.sp,
                    )
                }
            }
            proxy.source?.takeIf { it.isNotBlank() }?.let { source ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "source: $source",
                    color = VlessHubColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip("Copy", AppIcons.ContentCopy, onCopy)
                ActionChip("Share", Icons.Default.Share, onShare)
                ActionChip("Open", Icons.AutoMirrored.Filled.Send, onOpen)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = VlessHubColors.AccentNeon.copy(alpha = 0.1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = VlessHubColors.AccentNeon)
            Spacer(Modifier.width(6.dp))
            Text(label, color = VlessHubColors.AccentNeon, fontSize = 12.sp)
        }
    }
}

// â”€â”€â”€ Actions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

private fun shareProxy(context: Context, link: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, link)
    }
    context.startActivity(Intent.createChooser(intent, "Share proxy"))
}

private fun openInTelegram(context: Context, link: String) {
    ConfigActions.openWithDefaultApp(context, ConfigActions.normalizeTelegramProxyLink(link))
}
