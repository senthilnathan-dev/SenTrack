package `in`.senthilnathan_dev.sentrack.feature.personal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import `in`.senthilnathan_dev.sentrack.core.ui.formatDate
import java.time.LocalDate

/** One line of the timeline: something that happened (or is due) on a day, and where to open it. */
internal class TimelineEntry(
    val epochDay: Long,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onOpen: () -> Unit,
)

internal object TimelineIcons {
    val note = Icons.Filled.EditNote
    val achievement = Icons.Filled.EmojiEvents
    val date = Icons.Filled.Cake
    val plan = Icons.Filled.Flag
}

/**
 * Everything from the Personal tab on one line, newest first: notes, achievements, important dates that have
 * come round, and plan items you finished. Type to search across titles.
 */
@Composable
internal fun ColumnScope.TimelineContent(entries: List<TimelineEntry>) {
    var query by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text("Search your timeline") }, leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
    )
    val shown = entries.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) || it.subtitle.contains(query.trim(), ignoreCase = true) }
        .sortedByDescending { it.epochDay }
    if (shown.isEmpty()) {
        Section(if (entries.isEmpty()) "Your story starts here" else "No matches", if (entries.isEmpty()) "Notes, achievements, finished goals and the dates that matter will collect here, newest first." else null) {}
        return
    }
    val today = LocalDate.now().toEpochDay()
    shown.groupBy { LocalDate.ofEpochDay(it.epochDay).let { d -> d.year * 100 + d.monthValue } }.forEach { (_, month) ->
        val first = LocalDate.ofEpochDay(month.first().epochDay)
        Section("${first.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)} ${first.year}") {
            month.forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = e.onOpen).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(e.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(
                            listOf(if (e.epochDay == today) "Today" else formatDate(e.epochDay), e.subtitle).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
