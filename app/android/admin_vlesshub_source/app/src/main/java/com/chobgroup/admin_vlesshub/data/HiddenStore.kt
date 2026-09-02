package com.chobgroup.admin_vlesshub.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Local-only hidden items store — tracks server IDs and file IDs that the
 * admin has hidden (soft-delete). Hidden items are filtered out of the
 * displayed list but remain in the database.
 */
class HiddenStore private constructor() {

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("hidden_store", Context.MODE_PRIVATE)
    }

    // ── Servers ──────────────────────────────────────

    fun hiddenServerIds(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN_SERVERS, emptySet()) ?: emptySet()

    fun hideServer(id: String) {
        val current = hiddenServerIds().toMutableSet()
        current.add(id)
        prefs.edit().putStringSet(KEY_HIDDEN_SERVERS, current).apply()
    }

    fun unhideServer(id: String) {
        val current = hiddenServerIds().toMutableSet()
        current.remove(id)
        prefs.edit().putStringSet(KEY_HIDDEN_SERVERS, current).apply()
    }

    fun isServerHidden(id: String): Boolean = id in hiddenServerIds()

    // ── Files ────────────────────────────────────────

    fun hiddenFileIds(): Set<Long> =
        prefs.getStringSet(KEY_HIDDEN_FILES, emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()

    fun hideFile(id: Long) {
        val current = hiddenFileIds().map { it.toString() }.toMutableSet()
        current.add(id.toString())
        prefs.edit().putStringSet(KEY_HIDDEN_FILES, current).apply()
    }

    fun unhideFile(id: Long) {
        val current = hiddenFileIds().map { it.toString() }.toMutableSet()
        current.remove(id.toString())
        prefs.edit().putStringSet(KEY_HIDDEN_FILES, current).apply()
    }

    fun isFileHidden(id: Long): Boolean = id in hiddenFileIds()

    // ── Wipe all ─────────────────────────────────────

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_HIDDEN_SERVERS = "hidden_servers"
        private const val KEY_HIDDEN_FILES = "hidden_files"
        val instance: HiddenStore by lazy { HiddenStore() }
    }
}
