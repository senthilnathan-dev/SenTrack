package `in`.senthilnathan_dev.sentrack.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import `in`.senthilnathan_dev.sentrack.core.data.finance.FinanceRepository
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import `in`.senthilnathan_dev.sentrack.feature.finance.FinanceTab

private enum class Tab(val label: String, val glyph: String) {
    FINANCE("Finance", "₹"),
    PERSONAL("Personal", "★"),
    WORK("Work", "▤"),
}

/**
 * The unlocked app: one tab per life area, with Settings and Lock always one tap away.
 * Each tab owns its own screens and Back handling.
 */
@Composable
fun UnlockedShell(finance: FinanceRepository, onLock: () -> Unit, onOpenSettings: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(Tab.FINANCE) }
    val topActions: @Composable RowScope.() -> Unit = {
        TextButton(onClick = onOpenSettings) { Text("Settings") }
        TextButton(onClick = onLock) { Text("Lock") }
    }

    Scaffold(
        // Each tab's own screen already handles the system bars at the top; only the bottom bar is added here.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { Text(tab.glyph) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selected) {
                Tab.FINANCE -> FinanceTab(finance, topActions)
                Tab.PERSONAL -> ComingSoon(
                    "Personal", topActions,
                    "Notes, habits, goals and bucket list, achievements, important dates and reminders.",
                )
                Tab.WORK -> ComingSoon(
                    "Work", topActions,
                    "Punch in and out, leave calendar, tasks and appreciation, plus career tracking and your workspace of apps and sites.",
                )
            }
        }
    }
}

@Composable
private fun ComingSoon(title: String, actions: @Composable RowScope.() -> Unit, description: String) {
    AppScreen(title, actions = actions) {
        Section("Coming in a later phase", description) {}
    }
}
