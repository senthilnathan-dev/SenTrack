package `in`.senthilnathan_dev.sentrack.security

import `in`.senthilnathan_dev.sentrack.core.ai.AiClient
import `in`.senthilnathan_dev.sentrack.core.ai.AiResult
import `in`.senthilnathan_dev.sentrack.core.ai.assist.AiPrompts
import `in`.senthilnathan_dev.sentrack.core.ai.assist.Cleanup
import `in`.senthilnathan_dev.sentrack.core.ai.assist.NumberGuard
import `in`.senthilnathan_dev.sentrack.core.ai.assist.Style
import `in`.senthilnathan_dev.sentrack.core.ai.local.LocalLlmHolder
import `in`.senthilnathan_dev.sentrack.core.ai.local.Verdict
import `in`.senthilnathan_dev.sentrack.core.ui.AiAssist
import `in`.senthilnathan_dev.sentrack.core.ui.AiOutcome
import `in`.senthilnathan_dev.sentrack.core.ui.AiRequest
import `in`.senthilnathan_dev.sentrack.core.ui.AiStyle
import `in`.senthilnathan_dev.sentrack.core.ui.AiWhere

/**
 * The app's side of the shared assistant: decides where a request runs (the on-device model if it passed its test,
 * otherwise the user's cloud provider if they added a key, otherwise nowhere), applies the chosen answer style, runs
 * a second checking pass in Accurate style, and screens the result for numbers the text never contained.
 */
class AiAssistImpl(
    private val settings: AiSettingsStore,
    private val models: LocalModelStore,
    private val holder: LocalLlmHolder,
) : AiAssist {
    @Volatile private var ai: AiSettings? = null
    @Volatile private var local: LocalAiState? = null
    @Volatile private var styleId: String? = null

    override suspend fun refresh() {
        ai = settings.load()
        local = settings.loadLocal()
        styleId = settings.styleId()
    }

    private fun localReady(): Boolean {
        val l = local ?: return false
        val verdict = l.verdict?.let { v -> Verdict.entries.firstOrNull { it.name == v } }
        return l.enabled && (verdict == Verdict.GOOD || verdict == Verdict.USABLE) && models.modelFile() != null
    }

    override fun where(): AiWhere {
        val s = ai ?: return AiWhere.None
        return when {
            !s.enabled -> AiWhere.None
            localReady() -> AiWhere.Device
            s.hasKey -> AiWhere.Cloud(s.provider.label)
            else -> AiWhere.None
        }
    }

    override fun defaultStyle(): AiStyle = AiStyle.entries.firstOrNull { it.id == Style.of(styleId).id } ?: AiStyle.NORMAL

    override suspend fun run(request: AiRequest): AiOutcome {
        val started = System.nanoTime()
        val style = Style.of(request.style.id)
        val prompt = AiPrompts.build(request.use.id, request.text, request.extra, style)
        val params = style.params.copy(system = prompt.system)
        return try {
            val (raw, onDevice) = when (where()) {
                AiWhere.Device -> {
                    val file = models.modelFile() ?: return AiOutcome.Failed("The on-device model file is missing. Set it up again in Settings.")
                    holder.use(file.path) { llm ->
                        var draft = Cleanup.clean(llm.ask(prompt.user, params))
                        if (style.selfCheck && draft.isNotBlank()) {
                            val checked = Cleanup.clean(llm.ask(AiPrompts.verify(request.text, draft), params))
                            if (checked.isNotBlank()) draft = checked
                        }
                        draft
                    } to true
                }
                is AiWhere.Cloud -> {
                    val s = ai ?: return AiOutcome.Failed("AI is not set up.")
                    val key = settings.key(s.provider) ?: return AiOutcome.Failed("No API key saved.")
                    when (val r = AiClient.ask(s.provider, s.model, key, prompt.user, prompt.system, params.temperature, maxTokens = params.maxChars / 3)) {
                        is AiResult.Text -> Cleanup.clean(r.text) to false
                        is AiResult.Failure -> return AiOutcome.Failed(r.message)
                    }
                }
                AiWhere.None -> return AiOutcome.Failed("AI is off. Turn it on in Settings, then AI assistant.")
            }
            if (raw.isBlank()) return AiOutcome.Failed("The model gave no answer. Try again, or choose another style.")
            val warnings = NumberGuard.warnings(request.use.id, request.text + "\n" + request.extra, raw)
            AiOutcome.Text(raw, warnings, (System.nanoTime() - started) / 1_000_000, onDevice)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            AiOutcome.Failed("The assistant could not finish: ${e.message ?: e.javaClass.simpleName}.")
        }
    }
}
