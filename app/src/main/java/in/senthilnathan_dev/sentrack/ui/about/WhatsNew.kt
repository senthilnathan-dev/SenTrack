package `in`.senthilnathan_dev.sentrack.ui.about

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Remembers which app version the user last saw, so the release notes appear once after an update (never on a fresh
 * install, where there is nothing "new" to them). Kept in ordinary preferences because it holds nothing private and
 * must be readable the moment the vault unlocks.
 */
internal object WhatsNewState {
    private const val PREFS = "sentrack_ui"
    private const val KEY = "last_seen_version_code"

    /** True when the user has run an earlier version and has not yet seen this one's notes. Records first runs silently. */
    fun shouldShow(context: Context, currentCode: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY, -1L)
        if (last == -1L) { prefs.edit().putLong(KEY, currentCode).apply(); return false }
        return last < currentCode
    }

    fun markSeen(context: Context, currentCode: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY, currentCode).apply()
    }
}

/** Shows the current version's release notes once after an update. Place it anywhere inside the unlocked app. */
@Composable
fun WhatsNewDialog() {
    val context = LocalContext.current
    val version = rememberVersion()
    var show by remember { mutableStateOf(false) }
    val release = remember(version) {
        LegalText.load(context, "changelog.txt").map { Release.from(it) }.firstOrNull { it.version == version.name }
    }
    LaunchedEffect(version) { show = release != null && WhatsNewState.shouldShow(context, version.code) }
    if (!show || release == null) return
    AlertDialog(
        onDismissRequest = { WhatsNewState.markSeen(context, version.code); show = false },
        title = { Text("What's new in ${release.version}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                release.sections.forEach { b -> NoteBlock(b) }
            }
        },
        confirmButton = { TextButton(onClick = { WhatsNewState.markSeen(context, version.code); show = false }) { Text("Got it") } },
    )
}

@Composable
private fun ColumnScope.NoteBlock(b: DocBlock) {
    b.heading?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
    Text(b.text, style = MaterialTheme.typography.bodyMedium)
}

