package `in`.senthilnathan_dev.sentrack.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import `in`.senthilnathan_dev.sentrack.core.ai.local.Check
import `in`.senthilnathan_dev.sentrack.core.ai.local.DeviceCheck
import `in`.senthilnathan_dev.sentrack.core.ai.local.Level
import `in`.senthilnathan_dev.sentrack.core.ai.local.LocalModels
import `in`.senthilnathan_dev.sentrack.core.ai.local.ModelOption
import `in`.senthilnathan_dev.sentrack.core.ai.local.Verdict
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.PrimaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import `in`.senthilnathan_dev.sentrack.core.ui.SwitchRow
import `in`.senthilnathan_dev.sentrack.security.LocalAiState
import `in`.senthilnathan_dev.sentrack.security.SetupPhase
import kotlinx.coroutines.launch

/**
 * The on-device model, set up with one tap: SenTrack picks a model that suits this phone, downloads it (resuming if
 * the connection drops), verifies it, tests it, and only then allows it. Everything runs on the phone afterwards.
 */
@Composable
fun LocalAiScreen(onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as SenTrackApp).container
    val setup = container.localSetup
    val store = container.ai
    val models = container.localModels
    val scope = rememberCoroutineScope()

    val ui by setup.ui.collectAsState()
    var saved by remember { mutableStateOf<LocalAiState?>(null) }
    var allowMobileData by remember { mutableStateOf(false) }
    var recommended by remember { mutableStateOf<ModelOption?>(null) }
    var preChecks by remember { mutableStateOf<List<Check>>(emptyList()) }
    val installed = models.installedOption()

    suspend fun reload() { saved = store.loadLocal(); onChanged() }
    LaunchedEffect(Unit) {
        reload()
        val profile = models.readDeviceProfile()
        recommended = setup.recommended()
        preChecks = DeviceCheck.evaluate(profile, (recommended ?: LocalModels.FAST).sizeMb)
    }
    // Refresh the saved state when a setup run finishes.
    LaunchedEffect(ui.phase) { if (ui.phase == SetupPhase.DONE || ui.phase == SetupPhase.IDLE) reload() }

    // Keep the screen awake while downloading or testing so the phone does not sleep mid-way.
    val view = LocalView.current
    DisposableEffect(ui.busy) {
        view.keepScreenOn = ui.busy
        onDispose { view.keepScreenOn = false }
    }

    val s = saved
    AppScreen("On-device AI", onBack = onBack) {
        if (s == null) return@AppScreen
        val verdict = s.verdict?.let { v -> Verdict.entries.firstOrNull { it.name == v } }
        val ready = installed != null && (verdict == Verdict.GOOD || verdict == Verdict.USABLE)
        val target = ui.option ?: recommended ?: LocalModels.FAST

        Section(
            "Private AI on this phone",
            "A language model that runs entirely on your phone. Nothing you type is sent anywhere. SenTrack downloads it, checks it and tests it on your phone for you; there is nothing to configure.",
        ) {}

        if (ready) {
            Section("Ready") {
                Text("${installed?.title}: ${verdict?.label}", style = MaterialTheme.typography.titleMedium)
                Text(verdict?.advice.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                s.summary.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                SwitchRow(
                    title = "Use it in the assistant", checked = s.enabled, enabled = !ui.busy,
                    subtitle = "Answers stay on this phone.",
                    onChange = { on -> scope.launch { store.setLocalEnabled(on); reload() } },
                )
                SecondaryButton("Remove the model", icon = Icons.Filled.Delete, enabled = !ui.busy, onClick = { setup.remove() })
            }
        }

        if (!ready || ui.phase != SetupPhase.IDLE) {
            Section(if (ready) "Set up again" else "Set up") {
                Text(target.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${target.tagline}\nDownload: about ${target.sizeMb} MB · Licence: ${target.license}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val partial = models.partialBytes(target) / (1024 * 1024)

                if (!ui.busy) {
                    if (models.isMetered()) {
                        SwitchRow(
                            title = "Allow mobile data", checked = allowMobileData,
                            subtitle = "You are not on Wi-Fi. The download is large.",
                            onChange = { allowMobileData = it },
                        )
                    }
                    PrimaryButton(
                        if (partial > 0 && installed?.id != target.id) "Resume download ($partial MB done)" else "Set up on-device AI",
                        icon = Icons.Filled.Download,
                        onClick = { setup.start(target, allowMobileData) },
                    )
                } else {
                    ui.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    val p = ui.progress
                    if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    SecondaryButton("Stop", icon = Icons.Filled.Close, onClick = { setup.cancel() })
                }

                if (!ui.busy) ui.message?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = if (ui.phase == SetupPhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
                ui.fallback?.let { smaller ->
                    if (!ui.busy) SecondaryButton("Try the smaller model (${smaller.sizeMb} MB)", onClick = { setup.start(smaller, allowMobileData) })
                }
                if (!ui.busy && ui.verdict != null && !ready) {
                    ui.summary.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }

            val checks = ui.checks.ifEmpty { preChecks }
            if (checks.isNotEmpty()) {
                Section("Your phone") {
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
                }
            }

            if (!ui.busy) {
                Section("Other models", "SenTrack chose the one above for your phone. You can try another.") {
                    LocalModels.catalog.filter { it.id != target.id }.forEach { m ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(m.title, style = MaterialTheme.typography.titleSmall)
                            Text("${m.tagline}\nAbout ${m.sizeMb} MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SecondaryButton("Set up this one instead", onClick = { setup.start(m, allowMobileData) })
                        }
                    }
                }
            }
        }
    }
}
