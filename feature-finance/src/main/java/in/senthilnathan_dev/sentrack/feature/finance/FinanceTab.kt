package `in`.senthilnathan_dev.sentrack.feature.finance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import `in`.senthilnathan_dev.sentrack.core.data.finance.FinanceRepository
import kotlinx.coroutines.launch

/** Where the user is inside the Finance tab. */
internal sealed interface FinanceRoute {
    data object Overview : FinanceRoute
    data object NewAccount : FinanceRoute
    data class EditAccount(val id: String) : FinanceRoute
}

/**
 * The Finance tab. It owns its own small navigation stack (the system Back button steps back through it) and reads
 * data straight from [FinanceRepository] while it is on screen.
 *
 * @param topActions buttons the app shell wants in the top bar (Settings, Lock).
 */
@Composable
fun FinanceTab(repo: FinanceRepository, topActions: @Composable RowScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(repo) { repo.ensureDefaults() }

    // Created once per time the tab enters the screen: they stop when it leaves (for example when the vault locks).
    val accounts by remember(repo) { repo.accounts() }.collectAsState(initial = emptyList())
    val transactions by remember(repo) { repo.transactions() }.collectAsState(initial = emptyList())
    val summaries = remember(accounts, transactions) { FinanceCalculator.summarize(accounts, transactions) }

    var route by remember { mutableStateOf<FinanceRoute>(FinanceRoute.Overview) }
    BackHandler(enabled = route != FinanceRoute.Overview) { route = FinanceRoute.Overview }

    when (val current = route) {
        FinanceRoute.Overview -> AccountsOverview(
            summaries = summaries,
            onAdd = { route = FinanceRoute.NewAccount },
            onOpen = { route = FinanceRoute.EditAccount(it) },
            topActions = topActions,
        )
        FinanceRoute.NewAccount -> AccountEditor(
            existing = null,
            onSave = { name, kind, opening, note ->
                scope.launch { repo.addAccount(name, kind, opening, note); route = FinanceRoute.Overview }
            },
            onToggleArchive = {},
            onBack = { route = FinanceRoute.Overview },
        )
        is FinanceRoute.EditAccount -> {
            val account = accounts.firstOrNull { it.id == current.id }
            if (account == null) route = FinanceRoute.Overview else AccountEditor(
                existing = account,
                onSave = { name, _, opening, note ->
                    scope.launch {
                        repo.updateAccount(account.copy(name = name, openingBalancePaise = opening, note = note?.ifBlank { null }))
                        route = FinanceRoute.Overview
                    }
                },
                onToggleArchive = {
                    scope.launch { repo.setAccountArchived(account.id, !account.archived); route = FinanceRoute.Overview }
                },
                onBack = { route = FinanceRoute.Overview },
            )
        }
    }
}
