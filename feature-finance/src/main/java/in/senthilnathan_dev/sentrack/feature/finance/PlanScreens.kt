package `in`.senthilnathan_dev.sentrack.feature.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.data.finance.BudgetEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.CategoryEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.CategoryType
import `in`.senthilnathan_dev.sentrack.core.data.finance.GoalEntity
import `in`.senthilnathan_dev.sentrack.core.ui.ActionBar
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
import `in`.senthilnathan_dev.sentrack.core.ui.PrimaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import java.time.LocalDate
import java.util.UUID

/** Green while comfortably inside the limit, amber from 80%, red once over. */
@Composable
internal fun budgetColor(status: BudgetStatus): Color = when {
    status.isOver -> MaterialTheme.colorScheme.error
    status.fraction >= 0.8 -> ChartColors.palette[1]
    else -> ChartColors.income
}

/** One budget: category, spent of limit, a progress bar, and what is left (or by how much it is exceeded). */
@Composable
internal fun BudgetRow(name: String, status: BudgetStatus, onClick: (() -> Unit)? = null) {
    val color = budgetColor(status)
    Column(
        modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${Money.format(status.spentPaise)} of ${Money.format(status.limitPaise)}", style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(
            progress = { status.fraction.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = color,
        )
        Text(
            if (status.isOver) "Over by ${Money.format(-status.remainingPaise)}" else "${Money.format(status.remainingPaise)} left",
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/** One goal: name, progress bar, amounts, and what it takes to get there on time. */
@Composable
internal fun GoalRow(progress: GoalProgress, onClick: (() -> Unit)? = null) {
    val goal = progress.goal
    val color = if (progress.isReached) ChartColors.income else ChartColors.invested
    Column(
        modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(goal.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${(progress.fraction * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = color)
        }
        LinearProgressIndicator(progress = { progress.fraction.toFloat() }, modifier = Modifier.fillMaxWidth(), color = color)
        Text(
            "${Money.format(progress.currentPaise)} of ${Money.format(goal.targetPaise)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val date = goal.targetDateEpochDay   // copied locally: a property from another module cannot be smart-cast
        val detail = when {
            progress.isReached -> "Goal reached"
            date == null -> "${Money.format(progress.remainingPaise)} to go"
            progress.monthsLeft == null -> "Target date passed · ${Money.format(progress.remainingPaise)} to go"
            else -> "By ${formatDate(date)} · set aside ${Money.format(progress.neededPerMonthPaise ?: 0)} a month"
        }
        Text(detail, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

private sealed interface BudgetDialogTarget {
    data object Add : BudgetDialogTarget
    data class Edit(val categoryId: String) : BudgetDialogTarget
}

/**
 * The Plan section: monthly budgets for this month, and savings goals.
 *
 * @param month this month's totals, used for how much each budget has been spent.
 */
@Composable
internal fun ColumnScope.PlanContent(
    budgets: List<BudgetEntity>,
    categories: List<CategoryEntity>,
    month: MonthSummary,
    goals: List<GoalProgress>,
    archivedGoalCount: Int,
    onSetBudget: (categoryId: String, limitPaise: Long) -> Unit,
    onOpenGoal: (String?) -> Unit,
    onOpenArchivedGoals: () -> Unit,
) {
    val categoryById = categories.associateBy { it.id }
    val statuses = FinanceCalculator.budgetStatuses(budgets, month).sortedByDescending { it.fraction }
    var dialog by remember { mutableStateOf<BudgetDialogTarget?>(null) }

    Section("Budgets this month", "Your spending so far against the limits you set. A budget is a monthly limit for one spending category.") {
        if (statuses.isEmpty()) {
            Text("No budgets yet. Add one for a category you want to keep an eye on, such as Food or Shopping.")
        } else {
            val totalLimit = statuses.sumOf { it.limitPaise }
            val totalSpent = statuses.sumOf { it.spentPaise }
            Text(
                "Budgeted ${Money.format(totalLimit)} · Spent ${Money.format(totalSpent)}",
                style = MaterialTheme.typography.titleSmall,
            )
            statuses.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider()
                BudgetRow(categoryById[s.categoryId]?.name ?: "Category", s) { dialog = BudgetDialogTarget.Edit(s.categoryId) }
            }
        }
        // Spending in categories that have no budget, so nothing slips by unnoticed.
        val budgeted = budgets.map { it.categoryId }.toSet()
        val unbudgeted = month.expenseByCategory.filter { it.key != null && it.key !in budgeted }.entries.sortedByDescending { it.value }.take(3)
        if (unbudgeted.isNotEmpty()) {
            Text(
                "Not budgeted yet: " + unbudgeted.joinToString(", ") { "${categoryById[it.key]?.name ?: "Category"} ${Money.format(it.value)}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SecondaryButton("Add budget", onClick = { dialog = BudgetDialogTarget.Add }, icon = Icons.Filled.Add)
    }

    Section("Goals", "Choose what you are saving for and which accounts count towards it: gold, FD, RD, cash, a wallet, anything.") {
        if (goals.isEmpty()) {
            Text("No goals yet.")
        } else {
            goals.forEachIndexed { i, g ->
                if (i > 0) HorizontalDivider()
                GoalRow(g) { onOpenGoal(g.goal.id) }
            }
        }
        PrimaryButton("Add goal", onClick = { onOpenGoal(null) }, icon = Icons.Filled.Add)
        if (archivedGoalCount > 0) {
            SecondaryButton("Archived goals ($archivedGoalCount)", onClick = onOpenArchivedGoals, icon = Icons.Filled.Archive)
        }
    }

    when (val target = dialog) {
        null -> Unit
        BudgetDialogTarget.Add -> BudgetDialog(
            choices = categories.filter { it.type == CategoryType.EXPENSE && !it.archived && it.id !in budgets.map { b -> b.categoryId } },
            editing = null,
            currentLimitPaise = null,
            onSet = { id, limit -> onSetBudget(id, limit); dialog = null },
            onDismiss = { dialog = null },
        )
        is BudgetDialogTarget.Edit -> BudgetDialog(
            choices = emptyList(),
            editing = categoryById[target.categoryId],
            currentLimitPaise = budgets.firstOrNull { it.categoryId == target.categoryId }?.monthlyLimitPaise,
            onSet = { id, limit -> onSetBudget(id, limit); dialog = null },
            onDismiss = { dialog = null },
        )
    }
}

/** Set, change or remove one budget's monthly limit. */
@Composable
private fun BudgetDialog(
    choices: List<CategoryEntity>,
    editing: CategoryEntity?,
    currentLimitPaise: Long?,
    onSet: (String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosenId by remember { mutableStateOf(editing?.id) }
    var amountText by remember { mutableStateOf(currentLimitPaise?.let { Money.toInput(it) }.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Add a budget" else "Budget for ${editing.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (editing == null) {
                    if (choices.isEmpty()) {
                        Text("Every spending category already has a budget.")
                    } else {
                        PickerField(
                            "Category",
                            choices.firstOrNull { it.id == chosenId }?.name ?: "Choose a category",
                            choices.map { Pair<String, String?>(it.name, it.id) },
                        ) { chosenId = it }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Monthly limit (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Message(error, MessageKind.ERROR)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val limit = Money.parse(amountText)
                when {
                    chosenId == null -> error = "Choose a category."
                    limit == null || limit <= 0 -> error = "Enter a limit greater than zero."
                    else -> onSet(chosenId!!, limit)
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (editing != null) TextButton(onClick = { onSet(editing.id, 0) }) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Create or edit a goal, and choose which accounts count towards it. */
@Composable
internal fun GoalEditorScreen(
    existing: GoalEntity?,
    linkedAccountIds: Set<String>,
    summaries: List<AccountSummary>,
    onSave: (GoalEntity, Set<String>) -> Unit,
    onToggleArchive: (() -> Unit)?,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var targetText by remember { mutableStateOf(existing?.let { Money.toInput(it.targetPaise) }.orEmpty()) }
    var targetDate by remember { mutableStateOf(existing?.targetDateEpochDay) }
    var selected by remember { mutableStateOf(linkedAccountIds) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmArchive by remember { mutableStateOf(false) }

    // Archived accounts only show up here if they are already linked to this goal.
    val choices = summaries.filter { !it.account.archived || it.account.id in linkedAccountIds }
    val countedNow = choices.filter { it.account.id in selected }.sumOf { it.balancePaise }

    fun submit() {
        val target = Money.parse(targetText)
        when {
            name.isBlank() -> error = "Give the goal a name."
            target == null || target <= 0 -> error = "Enter a target amount greater than zero."
            selected.isEmpty() -> error = "Choose at least one account that counts towards this goal."
            else -> onSave(
                GoalEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = name.trim(),
                    targetPaise = target,
                    targetDateEpochDay = targetDate,
                    archived = existing?.archived ?: false,
                    createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
                ),
                selected,
            )
        }
    }

    AppScreen(
        if (existing == null) "New goal" else "Edit goal",
        onBack = onBack,
        actions = {
            if (existing != null && onToggleArchive != null) {
                IconAction(
                    if (existing.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                    if (existing.archived) "Restore goal" else "Archive goal",
                    { if (existing.archived) onToggleArchive() else confirmArchive = true },
                )
            }
        },
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                PrimaryButton("Save", onClick = ::submit, icon = Icons.Filled.Check)
            }
        },
    ) {
        Section("Goal") {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("What are you saving for?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = targetText,
                onValueChange = { targetText = it },
                label = { Text("Target amount (₹)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            val date = targetDate
            if (date == null) {
                SecondaryButton("Add a target date", onClick = { targetDate = LocalDate.now().plusYears(1).toEpochDay() })
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) { DateField("Target date", date) { targetDate = it } }
                    IconAction(Icons.Filled.Close, "Remove target date", { targetDate = null })
                }
            }
        }

        Section("Accounts that count", "Progress is the money currently in these accounts (for investments, the amount invested).") {
            if (choices.isEmpty()) Text("Add an account first (Accounts tab).")
            choices.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = if (s.account.id in selected) selected - s.account.id else selected + s.account.id },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(checked = s.account.id in selected, onCheckedChange = null)
                    Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(s.account.name, style = MaterialTheme.typography.bodyLarge)
                        Text(s.account.kind.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Money.format(s.balancePaise), style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider()
            StatRow("Counted today", Money.format(countedNow))
        }
    }

    if (confirmArchive && onToggleArchive != null) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Archive this goal?") },
            text = { Text("It disappears from your plan but is kept. You can restore it from Archived goals.") },
            confirmButton = { TextButton(onClick = { confirmArchive = false; onToggleArchive() }) { Text("Archive") } },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancel") } },
        )
    }
}

/** Goals that were archived; restore one to bring it back. */
@Composable
internal fun ArchivedGoalsScreen(archived: List<GoalProgress>, onRestore: (String) -> Unit, onBack: () -> Unit) {
    AppScreen("Archived goals", onBack = onBack) {
        if (archived.isEmpty()) {
            Section("Nothing archived", "Archive a goal from its page to hide it without losing it.") {}
        } else {
            Section("Archived") {
                archived.forEachIndexed { i, g ->
                    if (i > 0) HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(g.goal.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${Money.format(g.currentPaise)} of ${Money.format(g.goal.targetPaise)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconAction(Icons.Filled.Unarchive, "Restore ${g.goal.name}", { onRestore(g.goal.id) })
                    }
                }
            }
        }
    }
}
