package com.chobgroup.admin_vlesshub.config

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libv2ray.Libv2ray
import java.io.File

/**
 * Manages real-delay testing via libv2ray (AndroidLibXrayLite) JNI.
 *
 * Uses [Libv2ray.measureOutboundDelay] to start a temporary xray-core instance
 * in-process, make an HTTP request through the configured proxy, and report
 * the real round-trip latency. This is the same approach v2rayNG uses.
 *
 * For each config:
 *   1. Initializes xray-core environment (geo data, first run only)
 *   2. Generates xray JSON config from the URI
 *   3. Calls measureOutboundDelay (in-process, no subprocess)
 *   4. Reports real latency or failure
 */
object XrayTestManager {

    private const val TEST_URL = "http://cp.cloudflare.com/"
    private const val MEASURE_TIMEOUT_MS = 15_000L

    private var initialized = false
    private val initLock = Any()

    /**
     * Result of a real-delay test.
     */
    data class TestResult(
        val latencyMs: Int,     // -1 = failed
        val error: String? = null,
    )

    /**
     * Run a real-delay test for a single config.
     * Must be called from a background thread.
     */
    suspend fun testConfig(context: Context, rawConfig: String): TestResult = withContext(Dispatchers.IO) {
        try {
            // 1. Ensure libv2ray environment is initialized
            ensureInitialized(context)

            // 2. Generate xray config JSON
            val xrayConfig = XrayConfigGenerator.generateForMeasure(rawConfig)
                ?: return@withContext TestResult(-1, "Can't parse config")

            // 3. Measure real outbound delay through xray-core
            val delay = Libv2ray.measureOutboundDelay(xrayConfig, TEST_URL)

            if (delay < 0) {
                TestResult(-1, "Config rejected by xray-core")
            } else {
                TestResult(delay.toInt())
            }
        } catch (e: Exception) {
            val msg = when {
                e.message?.contains("timeout", true) == true -> "Connection timeout"
                e.message?.contains("connect", true) == true -> "Can't connect to server"
                e.message?.contains("tls", true) == true -> "TLS handshake failed"
                e.message?.contains("reality", true) == true -> "Reality handshake failed"
                e.message?.contains("auth", true) == true -> "Authentication failed"
                e.message?.contains("uuid", true) == true -> "Invalid UUID"
                else -> e.message?.take(200) ?: "Unknown error"
            }
            TestResult(-1, msg)
        }
    }

    /**
     * Batch test multiple configs sequentially.
     * Returns a map of rawConfig → TestResult.
     */
    suspend fun testConfigs(
        context: Context,
        configs: List<String>,
        onProgress: (Int, Int, String, TestResult) -> Unit = { _, _, _, _ -> },
    ): Map<String, TestResult> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, TestResult>()
        for ((idx, config) in configs.withIndex()) {
            val result = testConfig(context, config)
            onProgress(idx + 1, configs.size, config, result)
            results[config] = result
        }
        results
    }

    // ── Internal helpers ───────────────────────────────────────

    /**
     * Initialize libv2ray environment: extract geo data to app files dir,
     * then call Libv2ray.initCoreEnv().
     */
    private fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return

            // Extract geo data files from assets to app-private dir
            val envDir = File(context.filesDir, "xray_env")
            envDir.mkdirs()
            listOf("geoip.dat", "geosite.dat").forEach { name ->
                val target = File(envDir, name)
                if (!target.exists()) {
                    context.assets.open(name).use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            // Initialize libv2ray environment
            Libv2ray.initCoreEnv(envDir.absolutePath, "")
            initialized = true
        }
    }
}
