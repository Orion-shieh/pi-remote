package com.piremote.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, THEME_LIGHT) ?: THEME_LIGHT
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value).apply()
            _themeModeFlow.value = value
        }

    /** Font scale factor for user and AI messages in graphical view. */
    var guiFontScale: Float
        get() = prefs.getFloat(KEY_GUI_FONT_SCALE, DEFAULT_GUI_FONT_SCALE)
        set(value) {
            val clamped = value.coerceIn(0.75f, 1.50f)
            prefs.edit().putFloat(KEY_GUI_FONT_SCALE, clamped).apply()
            _guiFontScaleFlow.value = clamped
        }

    /**
     * Working directory new sessions launch in (home screen selector). Empty
     * means "follow each preset's own cwd".
     */
    var quickLaunchCwd: String
        get() = prefs.getString(KEY_QUICK_LAUNCH_CWD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_QUICK_LAUNCH_CWD, value.trim()).apply()

    // ------------------------------------------------ Local Native Agent Configuration
    var localAgentApiKey: String
        get() = prefs.getString(KEY_LOCAL_AGENT_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LOCAL_AGENT_API_KEY, value.trim()).apply()

    var localAgentEndpoint: String
        get() = prefs.getString(KEY_LOCAL_AGENT_ENDPOINT, DEFAULT_LOCAL_AGENT_ENDPOINT) ?: DEFAULT_LOCAL_AGENT_ENDPOINT
        set(value) = prefs.edit().putString(KEY_LOCAL_AGENT_ENDPOINT, value.trim()).apply()

    var localAgentModel: String
        get() = prefs.getString(KEY_LOCAL_AGENT_MODEL, DEFAULT_LOCAL_AGENT_MODEL) ?: DEFAULT_LOCAL_AGENT_MODEL
        set(value) = prefs.edit().putString(KEY_LOCAL_AGENT_MODEL, value.trim()).apply()

    var localAgentWorkspace: String
        get() = prefs.getString(KEY_LOCAL_AGENT_WORKSPACE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LOCAL_AGENT_WORKSPACE, value.trim()).apply()

    var localAgentProviderId: String
        get() = prefs.getString(KEY_LOCAL_AGENT_PROVIDER_ID, "deepseek") ?: "deepseek"
        set(value) = prefs.edit().putString(KEY_LOCAL_AGENT_PROVIDER_ID, value.trim()).apply()

    fun getProviderApiKey(providerId: String): String {
        val stored = prefs.getString("provider_key_$providerId", "") ?: ""
        if (stored.isNotBlank()) return stored
        if (providerId == localAgentProviderId || (providerId == "deepseek" && localAgentApiKey.isNotBlank())) {
            return localAgentApiKey
        }
        return ""
    }

    fun setProviderApiKey(providerId: String, apiKey: String) {
        val clean = apiKey.trim()
        prefs.edit().putString("provider_key_$providerId", clean).apply()
        if (providerId == localAgentProviderId) {
            localAgentApiKey = clean
        }
    }

    fun switchToProvider(providerId: String, endpoint: String, model: String? = null) {
        localAgentProviderId = providerId
        if (endpoint.isNotBlank()) {
            localAgentEndpoint = endpoint
        }
        val key = getProviderApiKey(providerId)
        localAgentApiKey = key
        if (!model.isNullOrBlank()) {
            localAgentModel = model
        }
    }

    fun getProviderEndpoint(providerId: String): String {
        val saved = prefs.getString("provider_endpoint_$providerId", null)
        if (!saved.isNullOrBlank()) return saved
        return when (providerId.lowercase()) {
            "deepseek" -> "https://api.deepseek.com/v1"
            "siliconflow" -> "https://api.siliconflow.cn/v1"
            "amd_radeon" -> "https://developer.amd.com.cn/radeon/api/v1"
            "openai" -> "https://api.openai.com/v1"
            "anthropic" -> "https://api.anthropic.com/v1"
            "google" -> "https://generativelanguage.googleapis.com/v1beta"
            "openrouter" -> "https://openrouter.ai/api/v1"
            "moonshot" -> "https://api.moonshot.cn/v1"
            "groq" -> "https://api.groq.com/openai/v1"
            "mistral" -> "https://api.mistral.ai/v1"
            "ollama" -> "http://127.0.0.1:11434/v1"
            else -> ""
        }
    }

    fun setProviderEndpoint(providerId: String, endpoint: String) {
        val clean = endpoint.trim()
        prefs.edit().putString("provider_endpoint_$providerId", clean).apply()
        if (providerId == localAgentProviderId && clean.isNotBlank()) {
            localAgentEndpoint = clean
        }
    }

    fun getLocalAgentCustomModels(): List<Pair<String, String>> {
        val raw = prefs.getString("local_agent_custom_models", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            val list = mutableListOf<Pair<String, String>>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val prov = obj.optString("provider")
                val id = obj.optString("id")
                if (prov.isNotBlank() && id.isNotBlank()) {
                    list.add(prov to id)
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addLocalAgentCustomModels(provider: String, modelIds: List<String>) {
        val current = getLocalAgentCustomModels().toMutableList()
        for (id in modelIds) {
            val clean = id.trim()
            if (clean.isNotBlank() && current.none { it.first.equals(provider, ignoreCase = true) && it.second == clean }) {
                current.add(provider to clean)
            }
        }
        val arr = org.json.JSONArray()
        for (item in current) {
            arr.put(org.json.JSONObject().put("provider", item.first).put("id", item.second))
        }
        prefs.edit().putString("local_agent_custom_models", arr.toString()).apply()
    }

    fun removeLocalAgentCustomModel(provider: String, modelId: String) {
        val current = getLocalAgentCustomModels().filterNot {
            it.first.equals(provider, ignoreCase = true) && it.second == modelId
        }
        val arr = org.json.JSONArray()
        for (item in current) {
            arr.put(org.json.JSONObject().put("provider", item.first).put("id", item.second))
        }
        prefs.edit().putString("local_agent_custom_models", arr.toString()).apply()
    }

    fun getLocalAgentDeletedModels(): Set<String> {
        return prefs.getStringSet("local_agent_deleted_models", emptySet()) ?: emptySet()
    }

    fun addLocalAgentDeletedModel(provider: String, modelId: String) {
        val set = getLocalAgentDeletedModels().toMutableSet()
        val p = provider.trim().lowercase()
        val id = modelId.trim().lowercase()
        set.add("$p/$id")
        set.add(id)
        prefs.edit().putStringSet("local_agent_deleted_models", set).apply()
        removeLocalAgentCustomModel(provider, modelId)
    }

    fun restoreLocalAgentDeletedModel(provider: String, modelId: String) {
        val set = getLocalAgentDeletedModels().toMutableSet()
        val p = provider.trim().lowercase()
        val id = modelId.trim().lowercase()
        set.remove("$p/$id")
        set.remove(id)
        prefs.edit().putStringSet("local_agent_deleted_models", set).apply()
    }

    fun clearLocalAgentProvider(provider: String) {
        val p = provider.trim().lowercase()
        prefs.edit().remove("provider_key_$p").remove("provider_endpoint_$p").apply()
        if (localAgentProviderId.equals(p, ignoreCase = true)) {
            localAgentApiKey = ""
        }
        val current = getLocalAgentCustomModels().filterNot { it.first.equals(p, ignoreCase = true) }
        val arr = org.json.JSONArray()
        for (item in current) {
            arr.put(org.json.JSONObject().put("provider", item.first).put("id", item.second))
        }
        prefs.edit().putString("local_agent_custom_models", arr.toString()).apply()
    }

    private val _themeModeFlow = MutableStateFlow(prefs.getString(KEY_THEME_MODE, THEME_LIGHT) ?: THEME_LIGHT)
    val themeModeFlow: StateFlow<String> = _themeModeFlow.asStateFlow()

    private val _guiFontScaleFlow = MutableStateFlow(prefs.getFloat(KEY_GUI_FONT_SCALE, DEFAULT_GUI_FONT_SCALE))
    val guiFontScaleFlow: StateFlow<Float> = _guiFontScaleFlow.asStateFlow()

    init {
        if (!prefs.getBoolean(KEY_THEME_INITIALIZED_V2, false)) {
            prefs.edit().putString(KEY_THEME_MODE, THEME_LIGHT).putBoolean(KEY_THEME_INITIALIZED_V2, true).apply()
            _themeModeFlow.value = THEME_LIGHT
        }
        if (prefs.contains(KEY_GUI_FONT_SCALE) && kotlin.math.abs(prefs.getFloat(KEY_GUI_FONT_SCALE, 1.0f) - 1.05f) < 0.01f) {
            guiFontScale = 1.00f
        }
    }

    val isConfigured: Boolean
        get() = endpoints.isNotEmpty() && deviceToken.isNotEmpty()

    val isLocalAgentConfigured: Boolean
        get() = localAgentApiKey.isNotBlank()

    companion object {
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
        const val THEME_SYSTEM = "system"

        const val DEFAULT_GUI_FONT_SCALE = 1.0f
        const val GUI_FONT_SCALE_SMALL = 0.85f
        const val GUI_FONT_SCALE_NORMAL = 1.00f
        const val GUI_FONT_SCALE_LARGE = 1.15f
        const val GUI_FONT_SCALE_HUGE = 1.30f

        private const val KEY_GUI_FONT_SCALE = "gui_font_scale"
        private const val KEY_THEME_MODE = "themeMode"
        private const val KEY_THEME_INITIALIZED_V2 = "themeInitialized_v2"
        private const val KEY_ENDPOINTS = "endpoints"
        private const val KEY_DEVICE_TOKEN = "deviceToken"
        private const val KEY_PIN = "pinnedSha256"
        private const val KEY_DEVICE_ID = "deviceId"
        private const val KEY_QUICK_LAUNCH_CWD = "quickLaunchCwd"
        private const val DEFAULT_ENDPOINTS = ""

        private const val KEY_LOCAL_AGENT_API_KEY = "local_agent_api_key"
        private const val KEY_LOCAL_AGENT_ENDPOINT = "local_agent_endpoint"
        private const val KEY_LOCAL_AGENT_MODEL = "local_agent_model"
        private const val KEY_LOCAL_AGENT_WORKSPACE = "local_agent_workspace"
        private const val KEY_LOCAL_AGENT_PROVIDER_ID = "local_agent_provider_id"

        const val DEFAULT_LOCAL_AGENT_ENDPOINT = "https://api.deepseek.com/v1"
        const val DEFAULT_LOCAL_AGENT_MODEL = "deepseek-chat"
    }
}
