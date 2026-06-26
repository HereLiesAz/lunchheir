package com.hereliesaz.lunchheir.smartfill

/**
 * Provider-agnostic configuration for the optional cloud refiner. **Any** AI backend is usable, not
 * just one vendor: the refiner (increment 3) reads this config to know where to send the request,
 * how to authenticate, and which wire format to speak.
 *
 * Most hosted and local backends speak the OpenAI chat-completions shape (OpenAI, Gemini's compat
 * endpoint, Groq, Together, Mistral, local Ollama / LM Studio, …), so [Format.OPENAI] is the default
 * lingua franca; [Format.ANTHROPIC] is the second built-in; [Format.CUSTOM] points at an arbitrary
 * endpoint. Adding a backend is filling in this config, not writing new code.
 *
 * This is a dormant value type — the networking that consumes it lands in increment 3, behind the
 * Smart-fill consent toggle plus a user-provided [apiKey]. Nothing here makes a request.
 *
 * @param baseUrl  full endpoint URL, e.g. "https://api.openai.com/v1/chat/completions" or a local
 *                 "http://127.0.0.1:11434/v1/chat/completions".
 * @param model    model identifier, e.g. "gpt-4o-mini", "claude-..." , "llama3.1", "gemini-...".
 * @param apiKey   user-supplied key; sent per [Format] (Bearer for OpenAI, x-api-key for Anthropic).
 *                 May be blank for keyless local servers.
 * @param format   which request/response shape to use.
 * @param extraHeaders any additional headers a backend requires (e.g. Anthropic's version header).
 */
data class AiProvider(
    val baseUrl: String,
    val model: String,
    val apiKey: String = "",
    val format: Format = Format.OPENAI,
    val extraHeaders: Map<String, String> = emptyMap(),
) {
    enum class Format { OPENAI, ANTHROPIC, CUSTOM }

    companion object {
        /** Convenience presets; the user can edit any field, including [baseUrl] for proxies. */
        fun openAi(model: String, apiKey: String) =
            AiProvider("https://api.openai.com/v1/chat/completions", model, apiKey, Format.OPENAI)

        fun anthropic(model: String, apiKey: String) =
            AiProvider(
                "https://api.anthropic.com/v1/messages", model, apiKey, Format.ANTHROPIC,
                mapOf("anthropic-version" to "2023-06-01"),
            )

        /** Gemini and most local runtimes expose an OpenAI-compatible endpoint. */
        fun openAiCompatible(baseUrl: String, model: String, apiKey: String = "") =
            AiProvider(baseUrl, model, apiKey, Format.OPENAI)
    }
}
