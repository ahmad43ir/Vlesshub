package com.chobgroup.admin_vlesshub.data.remote

import com.chobgroup.admin_vlesshub.data.AdminKeyStore
import com.chobgroup.admin_vlesshub.data.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Admin operations — rename, delete servers/files/proxies.
 * Each call sends the admin key via `X-Admin-Key` header.
 */
object AdminApi {

    private val JSON_MEDIA = "application/json".toMediaType()

    // ── DELETE ──────────────────────────────────────────────

    /** Delete a server config by its ID. */
    suspend fun deleteServer(id: String): Boolean = withContext(Dispatchers.IO) {
        doDelete("${AppConstants.VLESSHUB_API_URL}/servers/$id")
    }

    /** Delete a VPN file by its ID. */
    suspend fun deleteFile(id: Long): Boolean = withContext(Dispatchers.IO) {
        doDelete("${AppConstants.VLESSHUB_API_URL}/files/$id")
    }

    /** Delete a proxy by its ID. */
    suspend fun deleteProxy(id: String): Boolean = withContext(Dispatchers.IO) {
        doDelete("${AppConstants.VLESSHUB_API_URL}/proxies/$id")
    }

    // ── RENAME (PATCH) ─────────────────────────────────────

    /** Rename a server by its ID. */
    suspend fun renameServer(id: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        doPatch("${AppConstants.VLESSHUB_API_URL}/servers/$id", """{"name":"${newName.replace("\"", "\\\"")}"}""")
    }

    /** Rename a VPN file by its ID. */
    suspend fun renameFile(id: Long, newName: String): Boolean = withContext(Dispatchers.IO) {
        doPatch("${AppConstants.VLESSHUB_API_URL}/files/$id", """{"filename":"${newName.replace("\"", "\\\"")}"}""")
    }

    /** Rename a proxy (source label) by its ID. */
    suspend fun renameProxy(id: String, newSource: String): Boolean = withContext(Dispatchers.IO) {
        doPatch("${AppConstants.VLESSHUB_API_URL}/proxies/$id", """{"source":"${newSource.replace("\"", "\\\"")}"}""")
    }

    // ── Internal helpers ────────────────────────────────────

    private fun doPatch(url: String, bodyJson: String): Boolean {
        val adminKey = AdminKeyStore.instance.getAdminKey()
        if (adminKey.isBlank()) return false

        return try {
            val client = PinnedHttpClient.newClient(callTimeoutMillis = 10_000)
            val request = Request.Builder()
                .url(url)
                .patch(bodyJson.toRequestBody(JSON_MEDIA))
                .header("X-Admin-Key", adminKey)
                .header("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return true
                runCatching {
                    val json = JSONObject(body)
                    json.optBoolean("ok", true)
                }.getOrDefault(true)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun doDelete(url: String): Boolean {
        val adminKey = AdminKeyStore.instance.getAdminKey()
        if (adminKey.isBlank()) return false

        return try {
            val client = PinnedHttpClient.newClient(callTimeoutMillis = 10_000)
            val request = Request.Builder()
                .url(url)
                .delete("".toRequestBody(JSON_MEDIA))
                .header("X-Admin-Key", adminKey)
                .header("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return true
                runCatching {
                    val json = JSONObject(body)
                    json.optBoolean("ok", true)
                }.getOrDefault(true)
            }
        } catch (_: Exception) {
            false
        }
    }
}
