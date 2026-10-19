package `in`.senthilnathan_dev.sentrack.feature.finance

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.data.finance.FinanceRepository
import `in`.senthilnathan_dev.sentrack.core.data.finance.TaxDocCategory
import `in`.senthilnathan_dev.sentrack.core.data.finance.TaxDocumentEntity
import `in`.senthilnathan_dev.sentrack.core.security.vault.FileVault
import `in`.senthilnathan_dev.sentrack.core.data.finance.TxType
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.LocalExternalFlowStarter
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

/** Where the user is inside the Finance tab. */
internal sealed interface FinanceRoute {
    data object Home : FinanceRoute
    data object NewAccount : FinanceRoute
    data class EditAccount(val id: String) : FinanceRoute
    data class AccountDetail(val id: String) : FinanceRoute
    data object ArchivedAccounts : FinanceRoute
    data class Entry(val txId: String?, val type: TxType = TxType.EXPENSE, val accountId: String? = null) : FinanceRoute
    data class Split(val fromId: String?) : FinanceRoute
    data object Categories : FinanceRoute
    data class GoalEditor(val id: String?) : FinanceRoute
    data object ArchivedGoals : FinanceRoute
    data class Filing(val id: String) : FinanceRoute
    data class Viewer(val docId: String) : FinanceRoute
}

private enum class FinanceSection(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Filled.Dashboard),
    ACCOUNTS("Accounts", Icons.Filled.AccountBalanceWallet),
    ACTIVITY("Activity", Icons.Filled.Receipt),
    PLAN("Plan", Icons.Filled.Savings),
}

/**
 * The Finance tab. It lands on the Dashboard, with Accounts and Activity one tap away and an "Add entry" button
 * always at hand. It keeps its own back stack (the system Back button steps back through it) and reads data
 * straight from [FinanceRepository] while it is on screen.
 *
 * @param topActions buttons the app shell wants in the top bar (Settings, Lock).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceTab(repo: FinanceRepository, files: FileVault, topActions: @Composable RowScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(repo) { repo.ensureDefaults() }

    // Created once per time the tab enters the screen: they stop when it leaves (for example when the vault locks).
    val accounts by remember(repo) { repo.accounts() }.collectAsState(initial = emptyList())
    val transactions by remember(repo) { repo.transactions() }.collectAsState(initial = emptyList())
    val categories by remember(repo) { repo.categories() }.collectAsState(initial = emptyList())
    val budgets by remember(repo) { repo.budgets() }.collectAsState(initial = emptyList())
    val goals by remember(repo) { repo.goals() }.collectAsState(initial = emptyList())
    val goalLinks by remember(repo) { repo.goalAccounts() }.collectAsState(initial = emptyList())
    val taxFilings by remember(repo) { repo.taxFilings() }.collectAsState(initial = emptyList())
    val taxDocuments by remember(repo) { repo.taxDocuments() }.collectAsState(initial = emptyList())
    val summaries = remember(accounts, transactions) { FinanceCalculator.summarize(accounts, transactions) }
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val linkedByGoal = remember(goalLinks) { goalLinks.groupBy({ it.goalId }, { it.accountId }).mapValues { it.value.toSet() } }
    val goalProgress = remember(goals, linkedByGoal, summaries) {
        goals.map { FinanceCalculator.goalProgress(it, linkedByGoal[it.id] ?: emptySet(), summaries, LocalDate.now()) }
    }
    val thisMonth = remember(transactions) { YearMonth.now().let { FinanceCalculator.monthSummary(transactions, it.year, it.monthValue) } }

    // ---- Tax documents: import from the phone, save a readable copy, with progress and result messages.
    val context = LocalContext.current.applicationContext
    val taxService = remember(repo, files) { TaxDocumentService(context, repo, files) }
    val beginExternalFlow = LocalExternalFlowStarter.current   // keeps auto-lock from firing while the system picker is open
    var importing by remember { mutableStateOf(false) }
    var taxNotice by remember { mutableStateOf<String?>(null) }
    var taxProblem by remember { mutableStateOf<String?>(null) }
    var importTarget by remember { mutableStateOf<Pair<String, TaxDocCategory>?>(null) }   // filing id + document type
    var exportingDoc by remember { mutableStateOf<TaxDocumentEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val target = importTarget
        if (target != null && uris.isNotEmpty()) {
            scope.launch {
                importing = true; taxNotice = null; taxProblem = null
                val result = taxService.importFiles(target.first, target.second, uris)
                importing = false
                if (result.imported > 0) taxNotice = "Added ${result.imported} document(s), encrypted."
                if (result.failed.isNotEmpty()) taxProblem = "Could not add: " + result.failed.joinToString(", ")
            }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val doc = exportingDoc
        if (uri != null && doc != null) {
            scope.launch {
                taxNotice = null; taxProblem = null
                if (taxService.export(doc, uri)) taxNotice = "Saved a readable copy of ${doc.name}." else taxProblem = "Could not save the copy."
            }
        }
    }

    var section by rememberSaveable { mutableStateOf(FinanceSection.DASHBOARD) }
    val stack = remember { mutableStateListOf<FinanceRoute>(FinanceRoute.Home) }
    val route = stack.last()
    fun open(r: FinanceRoute) { stack.add(r) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun home() { stack.clear(); stack.add(FinanceRoute.Home) }
    BackHandler(enabled = stack.size > 1) { back() }

    when (route) {
        FinanceRoute.Home -> AppScreen(
            "Finance",
            actions = topActions,
            // The section switcher lives at the bottom, where the thumb rests (just above the app's module bar).
            bottomBar = {
                PrimaryTabRow(selectedTabIndex = section.ordinal, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    FinanceSection.entries.forEach { s ->
                        Tab(
                            selected = section == s,
                            onClick = { section = s },
                            text = { Text(s.label) },
                            icon = { Icon(s.icon, contentDescription = null) },
                        )
                    }
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { open(FinanceRoute.Entry(null)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add entry") },
                )
            },
        ) {
            when (section) {
                FinanceSection.DASHBOARD -> DashboardContent(
                    summaries = summaries,
                    transactions = transactions,
                    categories = categoryMap,
                    budgets = budgets,
                    goals = goalProgress.filterNot { it.goal.archived },
                    onAddAccount = { open(FinanceRoute.NewAccount) },
                    onSplit = { open(FinanceRoute.Split(null)) },
                    onOpenPlan = { section = FinanceSection.PLAN },
                )
                FinanceSection.ACCOUNTS -> {
                    AccountsContent(
                        summaries = summaries,
                        onAdd = { open(FinanceRoute.NewAccount) },
                        onOpen = { open(FinanceRoute.AccountDetail(it)) },
                        onOpenArchived = { open(FinanceRoute.ArchivedAccounts) },
                    )
                    SecondaryButton("Manage categories", onClick = { open(FinanceRoute.Categories) }, icon = Icons.Filled.Category)
                }
                FinanceSection.ACTIVITY -> ActivityContent(
                    transactions = transactions,
                    accounts = accountMap,
                    categories = categoryMap,
                    onOpen = { open(FinanceRoute.Entry(it)) },
                )
                FinanceSection.PLAN -> {
                PlanContent(
                    budgets = budgets,
                    categories = categories,
                    month = thisMonth,
                    goals = goalProgress.filterNot { it.goal.archived },
                    archivedGoalCount = goalProgress.count { it.goal.archived },
                    onSetBudget = { id, limit -> scope.launch { repo.setBudget(id, limit) } },
                    onOpenGoal = { open(FinanceRoute.GoalEditor(it)) },
                    onOpenArchivedGoals = { open(FinanceRoute.ArchivedGoals) },
                )
                TaxFilingsSection(
                    filings = taxFilings,
                    documents = taxDocuments,
                    onOpen = { taxNotice = null; taxProblem = null; open(FinanceRoute.Filing(it)) },
                    onAddYear = { year ->
                        scope.launch {
                            val filing = newFiling(year)
                            repo.saveTaxFiling(filing)
                            taxNotice = null; taxProblem = null
                            open(FinanceRoute.Filing(filing.id))
                        }
                    },
                )
                }
            }
            Spacer(Modifier.height(72.dp))   // keeps the last card clear of the floating button
        }

        FinanceRoute.NewAccount -> AccountEditor(
            existing = null,
            onSave = { name, kind, opening, note ->
                scope.launch { repo.addAccount(name, kind, opening, note); back() }
            },
            onBack = ::back,
        )

        is FinanceRoute.EditAccount -> {
            val account = accounts.firstOrNull { it.id == route.id }
            if (account == null) back() else AccountEditor(
                existing = account,
                onSave = { name, _, opening, note ->
                    scope.launch {
                        repo.updateAccount(account.copy(name = name, openingBalancePaise = opening, note = note?.ifBlank { null }))
                        back()
                    }
                },
                onBack = ::back,
            )
        }

        FinanceRoute.ArchivedAccounts -> ArchivedAccountsScreen(
            archived = summaries.filter { it.account.archived },
            onOpen = { open(FinanceRoute.AccountDetail(it)) },
            onRestore = { id -> scope.launch { repo.setAccountArchived(id, false) } },
            onBack = ::back,
        )

        is FinanceRoute.AccountDetail -> {
            val summary = summaries.firstOrNull { it.account.id == route.id }
            if (summary == null) back() else AccountDetailScreen(
                summary = summary,
                transactions = transactions,
                accounts = accountMap,
                categories = categoryMap,
                onAddEntry = {
                    open(FinanceRoute.Entry(null, if (summary.account.kind.isInvestment) TxType.INVEST else TxType.EXPENSE, summary.account.id))
                },
                onEdit = { open(FinanceRoute.EditAccount(summary.account.id)) },
                onToggleArchive = { scope.launch { repo.setAccountArchived(summary.account.id, !summary.account.archived); home() } },
                onOpenTx = { open(FinanceRoute.Entry(it)) },
                onBack = ::back,
            )
        }

        is FinanceRoute.Entry -> {
            val existing = route.txId?.let { id -> transactions.firstOrNull { it.id == id } }
            val deleteExisting: (() -> Unit)? = if (existing == null) null else fun() {
                scope.launch { repo.deleteTransaction(existing.id); back() }
            }
            if (route.txId != null && existing == null) back() else EntryScreen(
                existing = existing,
                initialType = route.type,
                initialAccountId = route.accountId,
                summaries = summaries,
                categories = categories,
                onSave = { tx, thenSplit ->
                    scope.launch {
                        repo.saveTransaction(tx)
                        back()
                        if (thenSplit) open(FinanceRoute.Split(tx.accountId))
                    }
                },
                onDelete = deleteExisting,
                onAddCategory = { name, type -> scope.launch { repo.addCategory(name, type) } },
                onBack = ::back,
            )
        }

        is FinanceRoute.Split -> SplitScreen(
            summaries = summaries,
            initialFromId = route.fromId,
            onSave = { txs -> scope.launch { txs.forEach { repo.saveTransaction(it) }; back() } },
            onBack = ::back,
        )

        FinanceRoute.Categories -> CategoriesScreen(
            categories = categories,
            onAdd = { name, type -> scope.launch { repo.addCategory(name, type) } },
            onToggleArchive = { c -> scope.launch { repo.saveCategory(c.copy(archived = !c.archived)) } },
            onBack = ::back,
        )

        is FinanceRoute.GoalEditor -> {
            val goal = route.id?.let { id -> goals.firstOrNull { it.id == id } }
            if (route.id != null && goal == null) back() else {
                val linked = goal?.let { linkedByGoal[it.id] } ?: emptySet()
                GoalEditorScreen(
                    existing = goal,
                    linkedAccountIds = linked,
                    summaries = summaries,
                    onSave = { saved, accountIds -> scope.launch { repo.saveGoal(saved, accountIds); back() } },
                    onToggleArchive = goal?.let { g ->
                        fun() { scope.launch { repo.saveGoal(g.copy(archived = !g.archived), linked); back() } }
                    },
                    onBack = ::back,
                )
            }
        }

        is FinanceRoute.Filing -> {
            val filing = taxFilings.firstOrNull { it.id == route.id }
            if (filing == null) back() else FilingDetailScreen(
                filing = filing,
                documents = taxDocuments.filter { it.filingId == filing.id },
                importing = importing,
                notice = taxNotice,
                problem = taxProblem,
                onSave = { updated -> scope.launch { repo.saveTaxFiling(updated); taxNotice = "Details saved." } },
                onPickDocuments = { category ->
                    importTarget = filing.id to category
                    beginExternalFlow()
                    picker.launch(arrayOf("*/*"))
                },
                onOpenDocument = { open(FinanceRoute.Viewer(it)) },
                onExportDocument = { doc -> exportingDoc = doc; beginExternalFlow(); exporter.launch(doc.name) },
                onDeleteDocument = { doc ->
                    scope.launch {
                        taxService.delete(doc)
                        taxNotice = "Deleted ${doc.name}."
                    }
                },
                onBack = ::back,
            )
        }

        is FinanceRoute.Viewer -> {
            val doc = taxDocuments.firstOrNull { it.id == route.docId }
            if (doc == null) back() else DocumentViewerScreen(
                doc = doc,
                service = taxService,
                files = files,
                onExport = { exportingDoc = doc; beginExternalFlow(); exporter.launch(doc.name) },
                onBack = ::back,
            )
        }

        FinanceRoute.ArchivedGoals -> ArchivedGoalsScreen(
            archived = goalProgress.filter { it.goal.archived },
            onRestore = { id ->
                goals.firstOrNull { it.id == id }?.let { g ->
                    scope.launch { repo.saveGoal(g.copy(archived = false), linkedByGoal[id] ?: emptySet()) }
                }
            },
            onBack = ::back,
        )
    }
}
