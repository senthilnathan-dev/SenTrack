package `in`.senthilnathan_dev.sentrack.security

import `in`.senthilnathan_dev.sentrack.core.ai.AiProvider
import `in`.senthilnathan_dev.sentrack.core.data.DatabaseHolder
import `in`.senthilnathan_dev.sentrack.core.data.MetaEntry

/** What the user chose for the AI assistant. The key is stored only inside the encrypted database. */
data class AiSettings(
    val enabled: Boolean,
    val provider: AiProvider,
    val model: String,
    val hasKey: Boolean,
)

/**
 * AI settings, kept in the encrypted database's meta table so an API key is protected like all other data and is
 * included in (encrypted) backups. AI is **off by default**; nothing is ever sent while it is off.
 */
class AiSettingsStore(private val holder: DatabaseHolder) {
    private fun dao() = holder.get().metaDao()

    private suspend fun get(key: String): String? = dao().get(key)
    private suspend fun put(key: String, value: String) = dao().put(MetaEntry(key, value))

    suspend fun load(): AiSettings {
        val provider = AiProvider.of(get("ai.provider")) ?: AiProvider.GEMINI
        return AiSettings(
            enabled = get("ai.enabled") == "1",
            provider = provider,
            model = get("ai.model.${provider.name}")?.takeIf { it.isNotBlank() } ?: provider.defaultModel,
            hasKey = !get("ai.key.${provider.name}").isNullOrBlank(),
        )
    }

    suspend fun setEnabled(enabled: Boolean) = put("ai.enabled", if (enabled) "1" else "0")
    suspend fun setProvider(provider: AiProvider) = put("ai.provider", provider.name)
    suspend fun setModel(provider: AiProvider, model: String) = put("ai.model.${provider.name}", model.trim())
    suspend fun setKey(provider: AiProvider, key: String) = put("ai.key.${provider.name}", key.trim())

    suspend fun loadLocal(): LocalAiState = LocalAiState(
        enabled = get("ai.local.enabled") == "1",
        modelName = get("ai.local.model")?.takeIf { it.isNotBlank() },
        verdict = get("ai.local.verdict")?.takeIf { it.isNotBlank() },
        summary = get("ai.local.summary")?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
    )

    suspend fun setLocalEnabled(enabled: Boolean) = put("ai.local.enabled", if (enabled) "1" else "0")

    /** Remembers which model was tested and how it did, so the verdict survives restarts. */
    suspend fun saveBenchmark(modelName: String, verdict: String, summary: List<String>) {
        put("ai.local.model", modelName)
        put("ai.local.verdict", verdict)
        put("ai.local.summary", summary.joinToString("\n"))
    }

    suspend fun clearLocal() {
        put("ai.local.enabled", "0"); put("ai.local.model", ""); put("ai.local.verdict", ""); put("ai.local.summary", "")
    }

    /** The stored key for [provider], for the one request that needs it. Never shown on screen. */
    suspend fun key(provider: AiProvider): String? = get("ai.key.${provider.name}")?.takeIf { it.isNotBlank() }
}

/** The state of the on-device model: whether the user wants it, and how the last phone test went. */
data class LocalAiState(val enabled: Boolean, val modelName: String?, val verdict: String?, val summary: List<String>)

