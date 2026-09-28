package com.piremote.app.data

import android.content.Context
import org.json.JSONObject

/**
 * Persists session fork lineage (childSessionId/File -> parentSessionId/File)
 * across app and agent sessions.
 */
class SessionLineageStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("pi-session-lineage", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LINEAGE = "fork_lineage_map"
    }

    /**
     * Records that [childKey] (id or file) was forked from [parentKey].
     */
    fun recordFork(childKey: String, parentKey: String) {
        if (childKey.isBlank() || parentKey.isBlank() || childKey == parentKey) return
        val current = getLineageMap().toMutableMap()
        current[childKey] = parentKey
        saveLineageMap(current)
    }

    /**
     * Returns the full lineage map (child -> parent).
     */
    fun getLineageMap(): Map<String, String> {
        val raw = prefs.getString(KEY_LINEAGE, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = json.optString(k)
                    if (v.isNotEmpty()) put(k, v)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveLineageMap(map: Map<String, String>) {
        try {
            val json = JSONObject()
            for ((k, v) in map) {
                json.put(k, v)
            }
            prefs.edit().putString(KEY_LINEAGE, json.toString()).apply()
        } catch (_: Exception) {
            // Ignore persistence errors
        }
    }
}
