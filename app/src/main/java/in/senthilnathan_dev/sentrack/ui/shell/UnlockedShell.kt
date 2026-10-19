package `in`.senthilnathan_dev.sentrack.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import `in`.senthilnathan_dev.sentrack.core.data.finance.FinanceRepository
import `in`.senthilnathan_dev.sentrack.core.security.vault.FileVault
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction
import `in`.senthilnathan_dev.sentrack.core.ui.LocalBottomInsetHandled
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import `in`.senthilnathan_dev.sentrack.feature.finance.FinanceTab

private enum class Tab(val label: String, val icon: ImageVector) {
    FINANCE("Finance", Icons.Filled.AccountBalance),
    PERSONAL("Personal", Icons.Filled.Favorite),
    WORK("Work", Icons.Filled.Work),
}

/**
 * The unlocked app: one tab per life area, with Settings and Lock always one tap away.
 * Each tab owns its own screens and Back handling.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnlockedShell(finance: FinanceRepository, files: FileVault, onLock: () -> Unit, onOpenSettings: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(Tab.FINANCE) }
    val topActions: @Composable RowScope.() -> Unit = {
        IconAction(Icons.Filled.Settings, "Settings", onOpenSettings)
        IconAction(Icons.Filled.Lock, "Lock now", onLock)
    }

    Scaffold(
        // Each tab's own screen already handles the system bars at the top; only the bottom bar is added here.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // While the keyboard is open the module bar steps aside, so the screen's own Save bar sits on the keyboard.
            if (!WindowInsets.isImeVisible) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selected == tab,
                            onClick = { selected = tab },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // The bottom bar above already pads for the system navigation bar, so screens inside must not pad again.
        CompositionLocalProvider(LocalBottomInsetHandled provides true) {
            Box(modifier = Modifier.padding(padding)) {
                when (selected) {
                    Tab.FINANCE -> FinanceTab(finance, files, topActions)
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
}

@Composable
private fun ComingSoon(title: String, actions: @Composable RowScope.() -> Unit, description: String) {
    AppScreen(title, actions = actions) {
        Section("Coming in a later phase", description) {}
    }
}
