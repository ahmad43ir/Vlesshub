package com.chobgroup.vlesshub.ads

import android.app.Application
import android.content.Context
import android.util.Log
import com.adivery.sdk.Adivery
import com.adivery.sdk.AdiveryListener

/**
 * Adivery wiring — the ONLY ad network for VlessHub.
 *
 * Interstitial-only model (no rewarded video):
 *  - Copy/Export: interstitial on every 5th distinct tap — action completes on close.
 *  - Refresh: interstitial — refresh runs when ad closes.
 *  - File download: interstitial — download runs when ad closes.
 *  - 60s app-wide cooldown caps interstitials to one per minute.
 *
 * If the ad isn't ready or cooldown is active, the action proceeds immediately.
 */
object AdiveryAdsManager {

    // -- CONFIGURATION — Adivery dashboard (VlessHub app) --
    private const val APP_ID = "3bfedc15-0410-4bff-b593-98d89fe3f7b6"
    private const val INTERSTITIAL_PLACEMENT_ID = "459ffbdd-40f3-43d1-b4eb-d5ddb864b25c"

    private const val TAG = "AdiveryAdsManager"
    // One interstitial ad per 60s max — app-wide.
    private const val INTERSTITIAL_COOLDOWN_MS = 60_000L

    @Volatile
    private var configured = false

    private var appContext: Context? = null

    /** Pending interstitial "closed" continuation (at most one at a time). */
    @Volatile
    private var pendingInterstitial: (() -> Unit)? = null

    private var lastInterstitialShownAt = 0L

    // -- Initialization --

    /** Called once from [com.chobgroup.vlesshub.MainActivity]. */
    fun init(context: Context) {
        if (configured) return
        configured = true
        appContext = context.applicationContext
        runCatching {
            Adivery.configure(appContext as Application, APP_ID)
            Adivery.addGlobalListener(object : AdiveryListener() {
                override fun onInterstitialAdClosed(placementId: String) {
                    Log.i(TAG, "Interstitial closed: $placementId")
                    reloadInterstitial()
                    pendingInterstitial?.invoke()
                    pendingInterstitial = null
                }

                override fun onInterstitialAdLoaded(placementId: String) {
                    Log.i(TAG, "Interstitial loaded: $placementId")
                }

                override fun log(tag: String, message: String) {
                    Log.d("Adivery-$tag", message)
                }
            })
            val ctx: Context = appContext ?: return@runCatching
            if (isConfigured()) {
                Adivery.prepareInterstitialAd(ctx, INTERSTITIAL_PLACEMENT_ID)
            }
        }.onFailure {
            Log.w(TAG, "Adivery init failed — actions proceed without ads", it)
        }
    }

    // -- Interstitial --

    fun isInterstitialReady(): Boolean =
        isConfigured() && isLoaded(INTERSTITIAL_PLACEMENT_ID)

    /** True once a real placement ID is set (not a placeholder). */
    fun isAdConfigured(): Boolean = isConfigured()

    /**
     * Shows the Adivery interstitial if loaded (and the 60s cooldown is clear),
     * then calls [onFinished] when the user closes it. If the ad isn't ready
     * or the cooldown is active, returns false (caller should proceed without ad).
     */
    fun maybeShowInterstitial(onFinished: () -> Unit): Boolean {
        if (!isInterstitialReady()) return false
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShownAt < INTERSTITIAL_COOLDOWN_MS) return false
        if (pendingInterstitial != null) {
            // An ad is already on screen — don't stack.
            return true
        }
        pendingInterstitial = onFinished
        return runCatching {
            lastInterstitialShownAt = now
            Adivery.showAd(INTERSTITIAL_PLACEMENT_ID)
            true
        }.onFailure {
            Log.w(TAG, "Interstitial show failed", it)
            pendingInterstitial = null
            onFinished()
        }.getOrElse { false }
    }

    /** Kick a fresh interstitial load. */
    fun prepareInterstitial() {
        reloadInterstitial()
    }

    // -- Internals --

    private fun isLoaded(placementId: String): Boolean = runCatching {
        Adivery.isLoaded(placementId)
    }.getOrDefault(false)

    private fun isConfigured(): Boolean =
        !INTERSTITIAL_PLACEMENT_ID.startsWith("REPLACE_") && appContext != null

    private fun reloadInterstitial() {
        appContext?.let {
            if (isConfigured()) {
                runCatching { Adivery.prepareInterstitialAd(it, INTERSTITIAL_PLACEMENT_ID) }
            }
        }
    }
}
