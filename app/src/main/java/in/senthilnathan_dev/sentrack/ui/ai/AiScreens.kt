package `in`.senthilnathan_dev.sentrack.ui.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.SenTrackApp
import `in`.senthilnathan_dev.sentrack.core.ai.AiClient
import `in`.senthilnathan_dev.sentrack.core.ai.AiProtocol
import `in`.senthilnathan_dev.sentrack.core.ai.AiProvider
import `in`.senthilnathan_dev.sentrack.core.ai.AiResult
import `in`.senthilnathan_dev.sentrack.core.ai.AiTask
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
import `in`.senthilnathan_dev.sentrack.core.ui.NavRow
import `in`.senthilnathan_dev.sentrack.core.ui.PrimaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.SecretField
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import `in`.senthilnathan_dev.sentrack.core.ui.SwitchRow
import `in`.senthilnathan_dev.sentrack.security.AiSettings
import kotlinx.coroutines.launch

private enum class AiPage { SETTINGS, ASSISTANT }

/**
 * AI assistant: settings (off by default, your own key) and a small text assistant. Cloud providers only ever see
 * the text you paste and send, and the screen shows exactly what will be sent before you tap Send.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiHost(onBack: () -> Unit) {
    val store = (LocalContext.current.applicationContext as SenTrackApp).container.ai
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf(AiPage.SETTINGS) }
    var settings by remember { mutableStateOf<AiSettings?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    BackHandler { if (page == AiPage.ASSISTANT) page = AiPage.SETTINGS else onBack() }
    suspend fun reload() { settings = store.load() }
    LaunchedEffect(Unit) { reload() }
    val s = settings

    if (s == null) {
        AppScreen("AI assistant", onBack = onBack) {}
        return
    }

    when (page) {
        AiPage.SETTINGS -> {
            var keyText by remember { mutableStateOf("") }
            var modelText by remember(s.provider, s.model) { mutableStateOf(s.model) }
            AppScreen("AI assistant", onBack = onBack) {
                Section(
                    "Optional and off by default",
                    "SenTrack works fully without AI. If you switch it on, text you choose to send goes to the company you pick, using your own key, under their terms. Nothing is sent in the background and none of your records are shared automatically.",
                ) {
                    SwitchRow(
                        icon = Icons.Filled.AutoAwesome, title = "Turn on the AI assistant", checked = s.enabled,
                        onChange = { on -> scope.launch { store.setEnabled(on); reload() } },
                    )
                }
                Section("Provider") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AiProvider.entries.forEach { p ->
                            FilterChip(selected = s.provider == p, onClick = { scope.launch { store.setProvider(p); reload() } }, label = { Text(p.label) })
                        }
                    }
                    OutlinedTextField(value = modelText, onValueChange = { modelText = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    SecretField(if (s.hasKey) "New API key (leave empty to keep the saved one)" else "API key (${s.provider.keyHint})", keyText, { keyText = it })
                    Message(message, MessageKind.SUCCESS)
                    PrimaryButton("Save", onClick = {
                        scope.launch {
                            store.setModel(s.provider, modelText.ifBlank { s.provider.defaultModel })
                            if (keyText.isNotBlank()) store.setKey(s.provider, keyText)
                            keyText = ""
                            reload()
                            message = "Saved. The key is kept encrypted on this phone."
                        }
                    })
                    Text(
                        if (s.hasKey) "A key is saved for ${s.provider.label}." else "No key saved for ${s.provider.label} yet.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Section("Use it") {
                    NavRow("Open the assistant", if (s.enabled && s.hasKey) "Summarize, tidy or fix text" else "Turn it on and save a key first", { if (s.enabled && s.hasKey) page = AiPage.ASSISTANT else message = null }, Icons.Filled.AutoAwesome)
                }
                Section("On-device model", "A model that runs entirely on this phone, so nothing leaves it, is planned. It is not available yet.") {}
            }
        }

        AiPage.ASSISTANT -> {
            var task by rememberSaveable { mutableStateOf(AiTask.SUMMARIZE) }
            var custom by rememberSaveable { mutableStateOf("") }
            var text by rememberSaveable { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var result by remember { mutableStateOf<String?>(null) }
            var error by remember { mutableStateOf<String?>(null) }
            val clipboard = LocalClipboardManager.current
            val prompt = AiProtocol.prompt(task, text, custom)

            AppScreen("Assistant", onBack = { page = AiPage.SETTINGS }) {
                Section("Your text") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AiTask.entries.forEach { t -> FilterChip(selected = task == t, onClick = { task = t }, label = { Text(t.label) }) }
                    }
                    if (task == AiTask.CUSTOM) OutlinedTextField(value = custom, onValueChange = { custom = it }, label = { Text("What should it do?") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Paste or type text") }, minLines = 5, modifier = Modifier.fillMaxWidth())
                }
                if (text.isNotBlank()) {
                    Section("Exactly what will be sent to ${s.provider.label}", "Review it. Remove anything private before sending.") {
                        Text(prompt, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Message(error, MessageKind.ERROR)
                PrimaryButton(if (busy) "Asking…" else "Send to ${s.provider.label}", enabled = text.isNotBlank() && !busy, busy = busy, icon = Icons.Filled.Send, onClick = {
                    scope.launch {
                        busy = true; error = null; result = null
                        val key = store.key(s.provider)
                        if (key == null) error = "No key saved." else when (val r = AiClient.ask(s.provider, s.model, key, prompt)) {
                            is AiResult.Text -> result = r.text
                            is AiResult.Failure -> error = r.message
                        }
                        busy = false
                    }
                })
                result?.let { out ->
                    Section("Result") {
                        Text(out, style = MaterialTheme.typography.bodyMedium)
                        SecondaryButton("Copy", icon = Icons.Filled.ContentCopy, onClick = { clipboard.setText(AnnotatedString(out)) })
                    }
                }
            }
        }
    }
}
