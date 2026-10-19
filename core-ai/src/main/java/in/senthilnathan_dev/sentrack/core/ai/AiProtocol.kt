package `in`.senthilnathan_dev.sentrack.core.ai

import org.json.JSONArray
import org.json.JSONObject

/** The cloud AI services SenTrack can talk to with the user's own key. Off by default. */
enum class AiProvider(val label: String, val defaultModel: String, val keyHint: String) {
    GEMINI("Google Gemini", "gemini-2.5-flash", "Key from Google AI Studio"),
    OPENAI("OpenAI", "gpt-4o-mini", "Key from platform.openai.com"),
    CLAUDE("Claude (Anthropic)", "claude-haiku-5-5", "Key from console.anthropic.com");

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name }
    }
}

/** What to do with the text. */
enum class AiTask(val label: String) {
    SUMMARIZE("Summarize"), REWRITE("Make clearer"), FIX("Fix spelling and grammar"), CUSTOM("Custom instruction");
}

/** A ready-to-send HTTPS request. The key travels only in [headers], never in the address. */
data class HttpSpec(val url: String, val headers: Map<String, String>, val body: String)

/** Builds provider requests and reads their replies. Pure functions, so they are unit-tested without a network. */
object AiProtocol {
    /** The exact instruction sent along with the user's text. Shown to the user before sending. */
    fun prompt(task: AiTask, text: String, custom: String = ""): String {
        val instruction = when (task) {
            AiTask.SUMMARIZE -> "Summarize the following text briefly. Keep names, numbers and dates exact."
            AiTask.REWRITE -> "Rewrite the following text to be clearer and better organised. Keep the meaning, the language and the facts."
            AiTask.FIX -> "Fix spelling and grammar in the following text. Change nothing else. Return only the corrected text."
            AiTask.CUSTOM -> custom.trim().ifEmpty { "Help with the following text." }
        }
        return "$instruction\n\n---\n$text"
    }

    fun request(
        provider: AiProvider, model: String, key: String, prompt: String,
        system: String = "", temperature: Double? = null, maxTokens: Int = 1500,
    ): HttpSpec = when (provider) {
        AiProvider.GEMINI -> HttpSpec(
            url = "https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent",
            headers = mapOf("x-goog-api-key" to key, "Content-Type" to "application/json"),
            body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                if (system.isNotBlank()) put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                put("generationConfig", JSONObject().put("maxOutputTokens", maxTokens).apply { temperature?.let { put("temperature", it) } })
            }.toString(),
        )
        AiProvider.OPENAI -> HttpSpec(
            url = "https://api.openai.com/v1/chat/completions",
            headers = mapOf("Authorization" to "Bearer $key", "Content-Type" to "application/json"),
            // Temperature is left at the service default here: some newer OpenAI models reject any other value.
            body = JSONObject().put("model", model.trim()).put(
                "messages",
                JSONArray().apply {
                    if (system.isNotBlank()) put(JSONObject().put("role", "system").put("content", system))
                    put(JSONObject().put("role", "user").put("content", prompt))
                },
            ).toString(),
        )
        AiProvider.CLAUDE -> HttpSpec(
            url = "https://api.anthropic.com/v1/messages",
            headers = mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01", "Content-Type" to "application/json"),
            body = JSONObject().apply {
                put("model", model.trim())
                put("max_tokens", maxTokens)
                if (system.isNotBlank()) put("system", system)
                temperature?.let { put("temperature", it) }
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            }.toString(),
        )
    }


    /** The reply text from a provider's JSON, or null when the shape is not what was expected. */
    fun parse(provider: AiProvider, json: String): String? = runCatching {
        val root = JSONObject(json)
        when (provider) {
            AiProvider.GEMINI -> root.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            AiProvider.OPENAI -> root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            AiProvider.CLAUDE -> root.getJSONArray("content").getJSONObject(0).getString("text")
        }.trim()
    }.getOrNull()

    /** A short, human reason from a provider's error JSON (all three put it under "error"), or null. */
    fun parseError(json: String): String? = runCatching {
        val error = JSONObject(json).get("error")
        if (error is JSONObject) error.optString("message").ifBlank { null } else error.toString()
    }.getOrNull()
}
