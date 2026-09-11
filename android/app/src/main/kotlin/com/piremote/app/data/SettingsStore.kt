package com.piremote.app.data

import android.content.Context
import java.util.UUID

/**
 * Connection settings, persisted in SharedPreferences.
 *
 * These come from `.secrets/relay.json` on the development machine. The pinned
 * fingerprint is what makes a self-signed certificate safe to use without a
 * domain: the relay's identity is checked by hash rather than by a CA.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("pi-remote", Context.MODE_PRIVATE)

    var endpoints: List<String>
        get() = prefs.getString(KEY_ENDPOINTS, DEFAULT_ENDPOINTS)
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        set(value) = prefs.edit().putString(KEY_ENDPOINTS, value.joinToString("\n")).apply()

    var deviceToken: String
        get() = prefs.getString(KEY_DEVICE_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEVICE_TOKEN, value.trim()).apply()

    /** Colon-separated uppercase SHA-256 fingerprint of the relay certificate. */
    var pinnedSha256: String?
        get() = prefs.getString(KEY_PIN, null)?.takeIf { it.isNotBlank() }
        set(value) {
            val normalized = value?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
            prefs.edit().putString(KEY_PIN, normalized).apply()
        }

    /** Stable identity for this device, so the relay can replace stale sockets. */
    val deviceId: String
        get() {
            val existing = prefs.getString(KEY_DEVICE_ID, null)
            if (existing != null) return existing
            val generated = "android-${UUID.randomUUID().toString().take(8)}"
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
            return generated
        }

    val isConfigured: Boolean
        get() = endpoints.isNotEmpty() && deviceToken.isNotEmpty()

    private companion object {
        const val KEY_ENDPOINTS = "endpoints"
        const val KEY_DEVICE_TOKEN = "deviceToken"
        const val KEY_PIN = "pinnedSha256"
        const val KEY_DEVICE_ID = "deviceId"
        const val DEFAULT_ENDPOINTS = ""
    }
}
