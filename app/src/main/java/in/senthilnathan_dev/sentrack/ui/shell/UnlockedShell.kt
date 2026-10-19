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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.School
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
import `in`.senthilnathan_dev.sentrack.core.alerts.ReminderCenter
import `in`.senthilnathan_dev.sentrack.core.alerts.ReminderRepository
import `in`.senthilnathan_dev.sentrack.core.data.finance.FinanceRepository
import `in`.senthilnathan_dev.sentrack.core.data.personal.HabitRepository
import `in`.senthilnathan_dev.sentrack.core.data.personal.LifeRepository
import `in`.senthilnathan_dev.sentrack.core.data.career.CareerRepository
import `in`.senthilnathan_dev.sentrack.core.data.work.WorkRepository
import `in`.senthilnathan_dev.sentrack.feature.career.CareerTab
import `in`.senthilnathan_dev.sentrack.feature.work.WorkTab
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteRepository
import `in`.senthilnathan_dev.sentrack.core.security.vault.FileVault
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction
import `in`.senthilnathan_dev.sentrack.core.ui.LocalBottomInsetHandled
import `in`.senthilnathan_dev.sentrack.core.ui.LocalExternalFlowStarter
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import `in`.senthilnathan_dev.sentrack.feature.finance.FinanceTab
import `in`.senthilnathan_dev.sentrack.feature.personal.PersonalTab
import `in`.senthilnathan_dev.sentrack.feature.reminders.RemindersScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    FINANCE("Finance", Icons.Filled.AccountBalance),
    PERSONAL("Personal", Icons.Filled.Favorite),
    WORK("Work", Icons.Filled.Work),
    CAREER("Career", Icons.Filled.School),
}

/**
 * The unlocked app: one tab per life area, with Settings and Lock always one tap away.
 * Each tab owns its own screens and Back handling.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnlockedShell(
    finance: FinanceRepository,
    files: FileVault,
    notes: NoteRepository,
    habits: HabitRepository,
    life: LifeRepository,
    work: WorkRepository,
    career: CareerRepository,
    reminders: ReminderRepository,
    reminderCenter: ReminderCenter,
    onLock: () -> Unit,
    onOpenSettings: () -> Unit,
    onExternalFlow: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(Tab.FINANCE) }
    var showReminders by rememberSaveable { mutableStateOf(false) }
    val topActions: @Composable RowScope.() -> Unit = {
        IconAction(Icons.Filled.Notifications, "Reminders", { showReminders = true })
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
                            onClick = { selected = tab; showReminders = false },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // The bottom bar above already pads for the system navigation bar, so screens inside must not pad again.
        CompositionLocalProvider(LocalBottomInsetHandled provides true, LocalExternalFlowStarter provides onExternalFlow) {
            Box(modifier = Modifier.padding(padding)) {
                if (showReminders) {
                    RemindersScreen(reminders, reminderCenter, onBack = { showReminders = false })
                    return@Box
                }
                when (selected) {
                    Tab.FINANCE -> FinanceTab(finance, files, topActions)
                    Tab.PERSONAL -> PersonalTab(notes, habits, life, reminders, files, topActions)
                    Tab.WORK -> WorkTab(work, files, topActions)
                    Tab.CAREER -> CareerTab(career, reminders, files, topActions)
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
