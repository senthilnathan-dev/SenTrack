package `in`.senthilnathan_dev.sentrack.core.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/*
 * The shared face of the AI assistant. Feature modules ask for help through [AiAssist] and show it with
 * [AiAssistButton]; they never see which model answers. The app provides the real implementation, so a feature module
 * needs no AI code of its own and keeps working (the button simply explains how to switch AI on) when AI is off.
 */

/** What the assistant can be asked to do. [id] selects the wording in the prompt table. */
enum class AiUse(val id: String, val label: String) {
    FIX("fix", "Fix spelling and grammar"),
    REWRITE("rewrite", "Make it clearer"),
    SHORTEN("shorten", "Make it shorter"),
    SUMMARIZE("summarize", "Summarize"),
    ACTION_ITEMS("action_items", "List action items"),
    TITLE("title", "Suggest a title"),
    STEPS("steps", "Break into steps"),
    POLISH("polish", "Write it up well"),
    FOLLOW_UP("follow_up", "Draft a follow-up email"),
    INTERVIEW_PREP("interview_prep", "Interview questions to prepare"),
    STUDY_PLAN("study_plan", "Make a study plan"),
    DAY_PLAN("day_plan", "Plan my day"),
    EXPLAIN_MONTH("explain_month", "Explain this month"),
    CATEGORY("category", "Suggest a category"),
}

/** Fast, Normal or Accurate: speed against care. */
enum class AiStyle(val id: String, val label: String, val blurb: String) {
    FAST("fast", "Fast", "Short answers, quickest."),
    NORMAL("normal", "Normal", "A good balance."),
    ACCURATE("accurate", "Accurate", "Most careful. Checks its own answer, so about twice as long."),
}

/** Where a request would run right now. */
sealed interface AiWhere {
    data object None : AiWhere
    data object Device : AiWhere
    data class Cloud(val provider: String) : AiWhere
}

data class AiRequest(val use: AiUse, val text: String, val extra: String, val style: AiStyle)

sealed interface AiOutcome {
    data class Text(val text: String, val warnings: List<String>, val millis: Long, val onDevice: Boolean) : AiOutcome
    data class Failed(val message: String) : AiOutcome
}

interface AiAssist {
    /** Re-reads the settings; cheap. Called when a menu opens so the answer to [where] is current. */
    suspend fun refresh()
    fun where(): AiWhere
    fun defaultStyle(): AiStyle
    suspend fun run(request: AiRequest): AiOutcome
}

/** Provided by the app shell. Null in previews and tests. */
val LocalAiAssist = compositionLocalOf<AiAssist?> { null }

/**
 * A sparkle button that opens a menu of [uses]. Picking one runs the assistant on [text] and shows the answer for the
 * user to accept ([onApply]), copy or discard. With a cloud provider the exact text is shown and must be confirmed
 * first; on-device requests start at once because nothing leaves the phone.
 *
 * @param extra context passed along with the text (for example the list of categories); sent as part of the request.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistButton(
    uses: List<AiUse>,
    text: () -> String,
    onApply: (AiUse, String) -> Unit,
    extra: (AiUse) -> String = { "" },
    applyLabel: (AiUse) -> String = { "Use this" },
) {
    val assist = LocalAiAssist.current ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<AiUse?>(null) }
    var source by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(AiStyle.NORMAL) }
    var where by remember { mutableStateOf<AiWhere>(AiWhere.None) }
    var outcome by remember { mutableStateOf<AiOutcome?>(null) }
    var running by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }

    fun run(use: AiUse) {
        scope.launch {
            running = true; outcome = null
            outcome = assist.run(AiRequest(use, source, extra(use), style))
            running = false
        }
    }

    IconAction(Icons.Filled.AutoAwesome, "AI assist", {
        scope.launch { assist.refresh(); where = assist.where() }
        menu = true
    })
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        if (where == AiWhere.None) {
            DropdownMenuItem(
                text = { Text("AI is off. Turn it on in Settings, then AI assistant.") },
                onClick = { menu = false }, enabled = false,
            )
        } else {
            uses.forEach { use ->
                DropdownMenuItem(
                    text = { Text(use.label) },
                    onClick = {
                        menu = false
                        source = text()
                        style = assist.defaultStyle()
                        active = use
                        outcome = null
                        confirmed = where is AiWhere.Device
                        if (source.isNotBlank() && where is AiWhere.Device) run(use)
                    },
                )
            }
        }
    }

    val use = active
    if (use != null) {
        val cloud = where as? AiWhere.Cloud
        AlertDialog(
            onDismissRequest = { if (!running) active = null },
            title = { Text(use.label) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when {
                        source.isBlank() -> Text("There is nothing to work on yet. Write something first.")
                        cloud != null && !confirmed -> {
                            Text("This exact text will be sent to ${cloud.provider}, using your key:", style = MaterialTheme.typography.labelMedium)
                            Text(source.take(2_000) + if (source.length > 2_000) "…" else "", style = MaterialTheme.typography.bodySmall)
                            if (extra(use).isNotBlank()) Text(extra(use), style = MaterialTheme.typography.bodySmall)
                        }
                        else -> {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AiStyle.entries.forEach { s ->
                                    FilterChip(
                                        selected = style == s, enabled = !running,
                                        onClick = { style = s; run(use) },
                                        label = { Text(s.label) },
                                    )
                                }
                            }
                            Text(style.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (running) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(if (where is AiWhere.Device) "Working on this phone…" else "Waiting for the answer…", style = MaterialTheme.typography.bodySmall)
                            }
                            when (val o = outcome) {
                                is AiOutcome.Text -> {
                                    SelectionContainer { Text(o.text, style = MaterialTheme.typography.bodyMedium) }
                                    o.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                                    Text(
                                        "%.1f s · %s".format(java.util.Locale.ENGLISH, o.millis / 1000.0, if (o.onDevice) "on this phone" else "cloud"),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                is AiOutcome.Failed -> Text(o.message, color = MaterialTheme.colorScheme.error)
                                null -> {}
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val o = outcome
                when {
                    cloud != null && !confirmed && source.isNotBlank() -> TextButton(onClick = { confirmed = true; run(use) }) { Text("Send") }
                    o is AiOutcome.Text -> TextButton(onClick = { onApply(use, o.text); active = null }) { Text(applyLabel(use)) }
                    else -> {}
                }
            },
            dismissButton = {
                val o = outcome
                Column {
                    if (o is AiOutcome.Text) TextButton(onClick = {
                        copySensitive(context, o.text)
                    }) { Text("Copy") }
                    TextButton(onClick = { active = null }, enabled = !running) { Text(if (o is AiOutcome.Text) "Discard" else "Cancel") }
                }
            },
        )
    }
}

/**
 * Copies text to the clipboard marked as sensitive, so Android hides it from the clipboard preview and keyboard
 * suggestions. The clipboard is still readable by apps that are in the foreground, so copy only when you mean to.
 */
fun copySensitive(context: Context, text: String) {
    val clip = ClipData.newPlainText("SenTrack", text)
    clip.description.extras = android.os.PersistableBundle().apply { putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true) }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}
