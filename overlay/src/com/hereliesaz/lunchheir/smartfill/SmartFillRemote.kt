package com.hereliesaz.lunchheir.smartfill

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * The optional **cloud refiner** half of the hybrid auto-fill — and the part that makes "use any
 * AI" real. It is provider-agnostic: it speaks to whatever endpoint an [AiProvider] points at, in
 * either the OpenAI chat-completions shape (the lingua franca for OpenAI, Gemini's compat endpoint,
 * Groq, Together, local Ollama / LM Studio, …) or the Anthropic messages shape. Adding a backend is
 * configuration, not code.
 *
 * Deliberately dependency-free — `java.net.HttpURLConnection` + `org.json`, both always present — so
 * the overlay adds no library and stays trivial to rebase onto new Lawnchair releases.
 *
 * It is a *refiner*: given the on-device baseline's members + the full candidate set + an optional
 * title, it asks the model which installed apps fit the group's purpose and what to call it, then
 * maps the answer back onto real [AppSignals]. Any failure (network, auth, bad JSON) returns null so
 * the caller silently keeps the on-device result. It only runs behind explicit user consent + a key.
 *
 * Dormant until [SmartFill] wires it in; instantiated nowhere yet.
 */
class SmartFillRemote(private val provider: AiProvider) {

    /**
     * Ask the configured model to refine membership + title. Returns null on any error so the
     * on-device [SmartFillEngine.Result] stands. Runs on [Dispatchers.IO] (blocking HTTP).
     */
    suspend fun refine(
        seeds: List<AppSignals>,
        candidates: List<AppSignals>,
        title: String?,
    ): SmartFillEngine.Result? = withContext(Dispatchers.IO) {
        try {
            val content = when (provider.format) {
                AiProvider.Format.ANTHROPIC -> callAnthropic(buildUserPrompt(seeds, candidates, title))
                else -> callOpenAiCompatible(buildUserPrompt(seeds, candidates, title))
            }
            parse(content, seeds, candidates)
        } catch (e: Exception) {
            null
        }
    }

    private fun buildUserPrompt(seeds: List<AppSignals>, candidates: List<AppSignals>, title: String?): String {
        val apps = candidates.joinToString("\n") { "- ${it.label} (${it.packageName})" }
        val seedList = seeds.joinToString(", ") { it.label }.ifBlank { "(none)" }
        val titleLine = if (title.isNullOrBlank()) "(none provided)" else title
        return buildString {
            append("Group the user's installed Android apps.\n")
            append("Seed apps already in the group: ").append(seedList).append('\n')
            append("Group title (a hint, may be empty): ").append(titleLine).append('\n')
            append("Installed apps:\n").append(apps).append('\n')
            append(
                "\nReturn ONLY a JSON object: {\"title\": string, \"packages\": [package names]}. " +
                    "Include every installed app that fits the group's pattern (type, developer, or " +
                    "purpose), including the seeds. If a title was provided, refine it; otherwise " +
                    "propose one. No prose, no code fences.",
            )
        }
    }

    private fun callOpenAiCompatible(userPrompt: String): String {
        val body = JSONObject()
            .put("model", provider.model)
            .put("temperature", 0)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", userPrompt)),
            )
        val headers = HashMap(provider.extraHeaders)
        if (provider.apiKey.isNotBlank()) headers["Authorization"] = "Bearer ${provider.apiKey}"
        val response = JSONObject(post(provider.baseUrl, headers, body.toString()))
        return response.getJSONArray("choices")
            .getJSONObject(0).getJSONObject("message").getString("content")
    }

    private fun callAnthropic(userPrompt: String): String {
        val body = JSONObject()
            .put("model", provider.model)
            .put("max_tokens", 1024)
            .put("system", SYSTEM_PROMPT)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", userPrompt)),
            )
        val headers = HashMap(provider.extraHeaders)
        if (provider.apiKey.isNotBlank()) headers["x-api-key"] = provider.apiKey
        headers.putIfAbsent("anthropic-version", "2023-06-01")
        val response = JSONObject(post(provider.baseUrl, headers, body.toString()))
        return response.getJSONArray("content").getJSONObject(0).getString("text")
    }

    /** Map the model's chosen package names back onto real [AppSignals]; ignore ones not installed. */
    private fun parse(content: String, seeds: List<AppSignals>, candidates: List<AppSignals>): SmartFillEngine.Result? {
        val json = extractJsonObject(content) ?: return null
        val byPackage = (candidates + seeds).associateBy { it.packageName }

        val members = LinkedHashMap<String, AppSignals>()
        seeds.forEach { members[it.key] = it }
        val packages = json.optJSONArray("packages") ?: JSONArray()
        for (i in 0 until packages.length()) {
            byPackage[packages.optString(i)]?.let { members[it.key] = it }
        }

        val suggested = json.optString("title").takeIf { it.isNotBlank() }
        return SmartFillEngine.Result(members.values.toList(), suggested, admitted = emptyList())
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "You organize a phone's apps into a themed group. You reply with strict JSON only."

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000

        /** Minimal JSON POST over HttpURLConnection. Throws on non-2xx (caught by [refine]). */
        private fun post(urlString: String, headers: Map<String, String>, body: String): String {
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (code !in 200..299) throw RuntimeException("HTTP $code: $text")
                return text
            } finally {
                conn.disconnect()
            }
        }

        /** Pull the first balanced {...} out of a model reply that may wrap it in prose/fences. */
        internal fun extractJsonObject(content: String): JSONObject? {
            val start = content.indexOf('{')
            val end = content.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return try {
                JSONObject(content.substring(start, end + 1))
            } catch (e: Exception) {
                null
            }
        }
    }
}
