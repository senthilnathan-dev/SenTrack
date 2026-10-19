package `in`.senthilnathan_dev.sentrack.ui.ai

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.SenTrackApp
import `in`.senthilnathan_dev.sentrack.core.ai.local.BenchmarkScoring
import `in`.senthilnathan_dev.sentrack.core.ai.local.Check
import `in`.senthilnathan_dev.sentrack.core.ai.local.DeviceCheck
import `in`.senthilnathan_dev.sentrack.core.ai.local.Level
import `in`.senthilnathan_dev.sentrack.core.ai.local.LocalLlm
import `in`.senthilnathan_dev.sentrack.core.ai.local.LocalModels
import `in`.senthilnathan_dev.sentrack.core.ai.local.Verdict
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
import `in`.senthilnathan_dev.sentrack.core.ui.PrimaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import `in`.senthilnathan_dev.sentrack.core.ui.SwitchRow
import `in`.senthilnathan_dev.sentrack.security.LocalAiState
import kotlinx.coroutines.launch

/**
 * The on-device model: get a model file, import it, test whether this phone can run it well, and only then allow its
 * use. Everything here runs on the phone; nothing is sent anywhere.
 */
@Composable
fun LocalAiScreen(onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as SenTrackApp).container
    val store = container.ai
    val models = container.localModels
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<LocalAiState?>(null) }
    var fileName by remember { mutableStateOf(models.modelFile()?.name) }
    var fileMb by remember { mutableStateOf(models.modelFile()?.length()?.div(1024 * 1024)?.toInt()) }
    var copiedBytes by remember { mutableStateOf<Long?>(null) }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var checks by remember { mutableStateOf<List<Check>>(emptyList()) }

    suspend fun reload() { state = store.loadLocal(); onChanged() }
    fun refreshChecks() {
        val size = fileMb ?: LocalModels.catalog.first().approxSizeMb
        checks = DeviceCheck.evaluate(models.readDeviceProfile(), size)
    }
    LaunchedEffect(Unit) { reload(); refreshChecks() }

    // Keep the screen awake during the test so the phone does not sleep mid-measurement.
    val view = LocalView.current
    DisposableEffect(testing) {
        view.keepScreenOn = testing
        onDispose { view.keepScreenOn = false }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            error = null; copiedBytes = 0
            try {
                val file = models.import(uri) { copiedBytes = it }
                store.clearLocal()
                fileName = file.name; fileMb = (file.length() / (1024 * 1024)).toInt()
                reload(); refreshChecks()
            } catch (e: Exception) {
                error = e.message ?: "Could not import that file."
            }
            copiedBytes = null
        }
    }

    val s = state
    AppScreen("On-device model", onBack = onBack) {
        if (s == null) return@AppScreen

        Section(
            "Private AI on this phone",
            "A small language model that runs entirely on your phone. Nothing you type is sent anywhere. It is smaller and slower than the cloud services, and some phones cannot run it well, so SenTrack tests yours first.",
        ) {}

        Section("1. Get a model file", "SenTrack does not download models itself, because Gemma's licence has to be accepted by you. Open a page, sign in if asked, accept the licence and download the .litertlm file, then come back and import it.") {
            LocalModels.catalog.forEach { m ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(m.title, style = MaterialTheme.typography.titleSmall)
                    Text("${m.about}\nAbout ${m.approxSizeMb} MB · ${m.license}\nFile: ${m.fileName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SecondaryButton("Open download page", icon = Icons.Filled.OpenInBrowser, onClick = {
                        container.beginExternalFlow()
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    })
                }
            }
        }

        Section("2. Import it") {
            if (fileName != null) {
                Text("$fileName${fileMb?.let { " ($it MB)" } ?: ""}", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text("No model imported yet.", style = MaterialTheme.typography.bodyMedium)
            }
            copiedBytes?.let { bytes ->
                Text("Copying… ${Formatter.formatShortFileSize(context, bytes)}", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Message(error, MessageKind.ERROR)
            SecondaryButton(
                if (fileName == null) "Choose the model file" else "Choose a different file",
                icon = Icons.Filled.FileOpen, enabled = copiedBytes == null && !testing,
                onClick = { container.beginExternalFlow(); picker.launch(arrayOf("*/*")) },
            )
            if (fileName != null) SecondaryButton("Remove the model", icon = Icons.Filled.Delete, enabled = !testing, onClick = {
                scope.launch { models.delete(); store.clearLocal(); fileName = null; fileMb = null; reload(); refreshChecks() }
            })
        }

        Section("3. Test my phone") {
            checks.forEach { c ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        when (c.level) { Level.OK -> Icons.Filled.CheckCircle; Level.WARN -> Icons.Filled.Warning; Level.FAIL -> Icons.Filled.Error },
                        contentDescription = c.level.name,
                        tint = when (c.level) { Level.OK -> MaterialTheme.colorScheme.primary; Level.WARN -> Color(0xFFE08E45); Level.FAIL -> MaterialTheme.colorScheme.error },
                    )
                    Column {
                        Text(c.label, style = MaterialTheme.typography.bodyLarge)
                        Text(c.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                "The test loads the model, writes a short paragraph and checks two small tasks. It takes about a minute, uses battery and may warm the phone. Keep SenTrack open until it finishes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                if (testing) "Testing…" else "Run the test",
                icon = Icons.Filled.PlayArrow, busy = testing,
                enabled = fileName != null && !testing && copiedBytes == null && DeviceCheck.canProceed(checks),
                onClick = {
                    val file = models.modelFile() ?: return@PrimaryButton
                    scope.launch {
                        testing = true; error = null
                        refreshChecks()
                        try {
                            LocalLlm(file.path).use { llm ->
                                val result = llm.benchmark()
                                val verdict = BenchmarkScoring.verdict(result)
                                store.saveBenchmark(file.name, verdict.name, BenchmarkScoring.describe(result))
                                if (verdict == Verdict.TOO_SLOW || verdict == Verdict.POOR_QUALITY) store.setLocalEnabled(false)
                            }
                        } catch (e: Throwable) {
                            error = "The test could not finish: ${e.message ?: e.javaClass.simpleName}. The model may be too large for this phone."
                        }
                        reload(); refreshChecks()
                        testing = false
                    }
                },
            )
            val verdict = s.verdict?.let { v -> Verdict.entries.firstOrNull { it.name == v } }
            if (verdict != null) {
                Text("Result for ${s.modelName}: ${verdict.label}", style = MaterialTheme.typography.titleMedium)
                Text(verdict.advice, style = MaterialTheme.typography.bodyMedium)
                s.summary.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }

        val verdict = s.verdict?.let { v -> Verdict.entries.firstOrNull { it.name == v } }
        val allowed = fileName != null && (verdict == Verdict.GOOD || verdict == Verdict.USABLE)
        Section("4. Use it") {
            SwitchRow(
                title = "Use the on-device model in the assistant", checked = s.enabled && allowed, enabled = allowed && !testing,
                subtitle = if (allowed) "Answers stay on this phone." else "Available once the test result is Good or Usable.",
                onChange = { on -> scope.launch { store.setLocalEnabled(on); reload() } },
            )
        }
    }
}
