package app.lawnchair.lunchheir.smartfill

import android.content.Context

/**
 * Persists the user's choice of cloud refiner, if any. The cloud pass is **off by default** and
 * gated twice: an explicit [cloudEnabled] consent flag *and* a usable [provider] (endpoint + model;
 * a key unless the backend is keyless/local). With neither, [SmartFill] runs purely on-device.
 *
 * Stored in a private SharedPreferences (overlay-contained, no upstream patch). The provider is fully
 * user-described — base URL, model, key, and wire [AiProvider.Format] — so any AI backend works:
 * OpenAI-compatible (incl. Gemini's compat endpoint and local Ollama / LM Studio), Anthropic, or a
 * custom endpoint. The key is the user's own; it never leaves the device except to their chosen host.
 */
class SmartFillConfig(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var cloudEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Build the configured provider, or null if cloud is off / not enough is set to call out. */
    fun provider(): AiProvider? {
        if (!cloudEnabled) return null
        val baseUrl = prefs.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        val model = prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() } ?: return null
        val format = runCatching { AiProvider.Format.valueOf(prefs.getString(KEY_FORMAT, null) ?: "") }
            .getOrDefault(AiProvider.Format.OPENAI)
        return AiProvider(
            baseUrl = baseUrl,
            model = model,
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            format = format,
        )
    }

    /** Persist a provider config (and implicitly the format/endpoint the user picked). */
    fun setProvider(provider: AiProvider) {
        prefs.edit()
            .putString(KEY_BASE_URL, provider.baseUrl)
            .putString(KEY_MODEL, provider.model)
            .putString(KEY_API_KEY, provider.apiKey)
            .putString(KEY_FORMAT, provider.format.name)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "lunchheir_smartfill"
        private const val KEY_ENABLED = "cloud_enabled"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_FORMAT = "format"
    }
}
