package `in`.senthilnathan_dev.sentrack.security

import `in`.senthilnathan_dev.sentrack.core.ai.local.BenchmarkScoring
import `in`.senthilnathan_dev.sentrack.core.ai.local.Check
import `in`.senthilnathan_dev.sentrack.core.ai.local.DeviceCheck
import `in`.senthilnathan_dev.sentrack.core.ai.local.LocalLlm
import `in`.senthilnathan_dev.sentrack.core.ai.local.ModelDownloader
import `in`.senthilnathan_dev.sentrack.core.ai.local.ModelOption
import `in`.senthilnathan_dev.sentrack.core.ai.local.SetupPlanner
import `in`.senthilnathan_dev.sentrack.core.ai.local.Verdict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SetupPhase { IDLE, CHECKING, DOWNLOADING, VERIFYING, TESTING, DONE, FAILED }

/** Everything the on-device setup screen shows. */
data class SetupUi(
    val phase: SetupPhase = SetupPhase.IDLE,
    val option: ModelOption? = null,
    /** 0..1 for downloading and verifying, or null when not measurable. */
    val progress: Float? = null,
    val message: String? = null,
    val checks: List<Check> = emptyList(),
    val verdict: Verdict? = null,
    val summary: List<String> = emptyList(),
    /** A smaller model to offer when this one was too slow or answered badly. */
    val fallback: ModelOption? = null,
) {
    val busy: Boolean
        get() = phase == SetupPhase.CHECKING || phase == SetupPhase.DOWNLOADING || phase == SetupPhase.VERIFYING || phase == SetupPhase.TESTING
}

/**
 * One-tap setup of the on-device model: check the phone, download (resuming if interrupted), verify the checksum,
 * run the speed and accuracy test, and record the verdict. Runs in an app-wide scope so leaving the screen does not
 * stop a long download. If the model is too slow or answers badly, the next smaller model is offered.
 */
class LocalAiSetup(
    private val models: LocalModelStore,
    private val settings: AiSettingsStore,
    private val scope: CoroutineScope,
) {
    private val _ui = MutableStateFlow(SetupUi())
    val ui: StateFlow<SetupUi> = _ui.asStateFlow()
    private var job: Job? = null

    /** The model to start with on this phone. */
    fun recommended(): ModelOption = SetupPlanner.recommend(models.readDeviceProfile())

    /** Starts (or resumes) setup of [option]. Large downloads on mobile data need [allowMobileData]. */
    fun start(option: ModelOption, allowMobileData: Boolean) {
        if (_ui.value.busy) return
        job = scope.launch {
            _ui.value = SetupUi(phase = SetupPhase.CHECKING, option = option, message = "Checking your phone…")
            try {
                val checks = DeviceCheck.evaluate(models.readDeviceProfile(), option.sizeMb)
                _ui.update { it.copy(checks = checks) }
                if (!DeviceCheck.canProceed(checks)) {
                    fail(option, "This phone cannot run ${option.title} yet. See the checks above.", SetupPlanner.fallbackFor(option))
                    return@launch
                }
                val installed = models.installedOption()
                if (installed?.id != option.id) {
                    if (!models.isOnline()) { fail(option, "No internet connection. Connect and try again."); return@launch }
                    if (models.isMetered() && !allowMobileData) {
                        fail(option, "You are on mobile data and this is a ${option.sizeMb} MB download. Connect to Wi-Fi, or allow mobile data and try again.")
                        return@launch
                    }
                    if (installed != null) models.delete()   // one model at a time
                }
                _ui.update { it.copy(phase = SetupPhase.DOWNLOADING, message = "Downloading ${option.title} (${option.sizeMb} MB)…", progress = 0f) }
                val file = ModelDownloader.fetch(
                    option, models.dir,
                    onDownloading = { done, total ->
                        _ui.update {
                            it.copy(
                                phase = SetupPhase.DOWNLOADING, progress = done.toFloat() / total,
                                message = "Downloading ${option.title}: ${done / (1024 * 1024)} of ${total / (1024 * 1024)} MB",
                            )
                        }
                    },
                    onVerifying = { done, total ->
                        _ui.update { it.copy(phase = SetupPhase.VERIFYING, progress = done.toFloat() / total, message = "Checking the file is intact…") }
                    },
                )

                _ui.update {
                    it.copy(phase = SetupPhase.TESTING, progress = null, message = "Testing speed and accuracy on this phone. This takes about a minute. Keep SenTrack open.")
                }
                val result = LocalLlm(file.path).use { it.benchmark() }
                val verdict = BenchmarkScoring.verdict(result)
                val summary = BenchmarkScoring.describe(result)
                settings.saveBenchmark(file.name, verdict.name, summary)
                val usable = verdict == Verdict.GOOD || verdict == Verdict.USABLE
                settings.setLocalEnabled(usable)
                _ui.value = SetupUi(
                    phase = SetupPhase.DONE, option = option, checks = checks, verdict = verdict, summary = summary,
                    message = if (usable) "Ready. ${verdict.advice}" else verdict.advice,
                    fallback = if (usable) null else SetupPlanner.fallbackFor(option),
                )
            } catch (e: CancellationException) {
                _ui.value = SetupUi(phase = SetupPhase.IDLE, option = option, message = "Stopped. A partly finished download is kept, so you can resume.")
                throw e
            } catch (e: Throwable) {
                fail(option, e.message ?: "Something went wrong (${e.javaClass.simpleName}).")
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Removes the model and forgets the result. */
    fun remove() {
        if (_ui.value.busy) return
        scope.launch {
            models.delete()
            settings.clearLocal()
            _ui.value = SetupUi()
        }
    }

    private fun fail(option: ModelOption, message: String, fallback: ModelOption? = null) {
        _ui.update { it.copy(phase = SetupPhase.FAILED, option = option, message = message, progress = null, fallback = fallback) }
    }
}
