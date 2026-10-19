package `in`.senthilnathan_dev.sentrack.feature.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.data.finance.AccountEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.CategoryEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.CategoryType
import `in`.senthilnathan_dev.sentrack.core.data.finance.TransactionEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.TxType
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
import `in`.senthilnathan_dev.sentrack.core.ui.PrimaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import java.time.LocalDate

/** What an entry looks like in a list: a title, a second line, and a signed amount with a colour. */
private class TxLine(val title: String, val subtitle: String, val amount: String, val color: Color?)

@Composable
private fun describe(tx: TransactionEntity, accounts: Map<String, AccountEntity>, categories: Map<String, CategoryEntity>): TxLine {
    val from = tx.accountId?.let { accounts[it]?.name }
    val to = tx.toAccountId?.let { accounts[it]?.name }
    val category = tx.categoryId?.let { categories[it]?.name }
    val note = tx.note?.let { " · $it" }.orEmpty()
    return when (tx.type) {
        TxType.EXPENSE -> TxLine(category ?: "Expense", (from ?: "Account") + note, "-" + Money.format(tx.amountPaise), MaterialTheme.colorScheme.error)
        TxType.INCOME -> TxLine(category ?: "Income", (from ?: "Account") + note, "+" + Money.format(tx.amountPaise), ChartColors.income)
        TxType.TRANSFER -> TxLine("Transfer", "${from ?: "?"} → ${to ?: "?"}$note", Money.format(tx.amountPaise), null)
        TxType.INVEST -> TxLine("Invested in ${to ?: "investment"}", "From ${from ?: "outside my accounts"}$note", Money.format(tx.amountPaise), ChartColors.invested)
        TxType.WITHDRAW -> {
            val gain = tx.amountPaise - (tx.principalReturnedPaise ?: tx.amountPaise)
            val gainText = if (gain == 0L) "no gain or loss" else if (gain > 0) "gain ${Money.format(gain)}" else "loss ${Money.format(-gain)}"
            TxLine("Withdrawn from ${from ?: "investment"}", "To ${to ?: "not tracked"} · $gainText$note", "+" + Money.format(tx.amountPaise), ChartColors.income)
        }
    }
}

/** One tappable row in an entries list. */
@Composable
internal fun TxRow(tx: TransactionEntity, accounts: Map<String, AccountEntity>, categories: Map<String, CategoryEntity>, onClick: () -> Unit) {
    val line = describe(tx, accounts, categories)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(line.title, style = MaterialTheme.typography.bodyLarge)
            Text(line.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(line.amount, style = MaterialTheme.typography.bodyLarge, color = line.color ?: MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
    }
}

private fun dateHeading(epochDay: Long): String {
    val today = LocalDate.now().toEpochDay()
    return when (epochDay) {
        today -> "Today"
        today - 1 -> "Yesterday"
        else -> formatDate(epochDay)
    }
}

/** Entries grouped under date headings, newest first. */
@Composable
internal fun TxGroups(
    transactions: List<TransactionEntity>,
    accounts: Map<String, AccountEntity>,
    categories: Map<String, CategoryEntity>,
    onOpen: (String) -> Unit,
) {
    transactions.groupBy { it.dateEpochDay }.entries.sortedByDescending { it.key }.forEach { (day, list) ->
        Section(dateHeading(day)) {
            list.forEachIndexed { i, tx ->
                if (i > 0) HorizontalDivider()
                TxRow(tx, accounts, categories) { onOpen(tx.id) }
            }
        }
    }
}

private enum class Range(val label: String) { MONTH("This month"), FY("This financial year"), ALL("All time") }

/** The Activity section: every entry, filterable by type and period. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.ActivityContent(
    transactions: List<TransactionEntity>,
    accounts: Map<String, AccountEntity>,
    categories: Map<String, CategoryEntity>,
    onOpen: (String) -> Unit,
) {
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }   // TxType name, null = all
    var range by rememberSaveable { mutableStateOf(Range.MONTH) }
    val today = LocalDate.now()

    val visible = transactions.filter { tx ->
        val d = LocalDate.ofEpochDay(tx.dateEpochDay)
        val inRange = when (range) {
            Range.MONTH -> d.year == today.year && d.month == today.month
            Range.FY -> FinanceInsights.financialYearOf(d) == FinanceInsights.financialYearOf(today)
            Range.ALL -> true
        }
        inRange && (typeFilter == null || tx.type.name == typeFilter)
    }

    Section("Filter") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Range.entries.forEach { r -> FilterChip(selected = range == r, onClick = { range = r }, label = { Text(r.label) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = typeFilter == null, onClick = { typeFilter = null }, label = { Text("All types") })
            TxType.entries.forEach { t ->
                FilterChip(selected = typeFilter == t.name, onClick = { typeFilter = t.name }, label = { Text(t.title()) })
            }
        }
    }

    if (visible.isEmpty()) {
        Section("No entries", "Nothing matches these filters. Tap “Add entry” to record one.") {}
    } else {
        TxGroups(visible, accounts, categories, onOpen)
    }
}

/** Details of one account: its numbers, its entries, and shortcuts to add an entry or edit it. */
@Composable
internal fun AccountDetailScreen(
    summary: AccountSummary,
    transactions: List<TransactionEntity>,
    accounts: Map<String, AccountEntity>,
    categories: Map<String, CategoryEntity>,
    onAddEntry: () -> Unit,
    onEdit: () -> Unit,
    onOpenTx: (String) -> Unit,
    onBack: () -> Unit,
) {
    val a = summary.account
    val mine = transactions.filter { it.accountId == a.id || it.toAccountId == a.id }
    AppScreen(a.name, onBack = onBack) {
        Section(a.kind.label) {
            if (a.kind.isInvestment) {
                Text(Money.format(summary.balancePaise), style = MaterialTheme.typography.headlineMedium)
                Text("Invested money still in this investment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                StatRow("Total invested", Money.format(summary.investedPaise))
                StatRow("Total received from withdrawals", Money.format(summary.withdrawnPaise))
                StatRow(
                    "Realised gain or loss",
                    Money.formatSigned(summary.realisedGainPaise),
                    if (summary.realisedGainPaise > 0) ChartColors.income else if (summary.realisedGainPaise < 0) MaterialTheme.colorScheme.error else null,
                )
            } else {
                Text(Money.format(summary.balancePaise), style = MaterialTheme.typography.headlineMedium)
                Text("Current balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            a.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        PrimaryButton("Add entry for this account", onClick = onAddEntry)
        SecondaryButton("Edit account", onClick = onEdit)
        if (mine.isEmpty()) {
            Section("Entries", "No entries yet.") {}
        } else {
            TxGroups(mine, accounts, categories, onOpenTx)
        }
    }
}

@Composable
internal fun StatRow(label: String, value: String, color: Color? = null) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}

/** Add, rename or archive categories. */
@Composable
internal fun CategoriesScreen(
    categories: List<CategoryEntity>,
    onAdd: (String, CategoryType) -> Unit,
    onToggleArchive: (CategoryEntity) -> Unit,
    onBack: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf(CategoryType.EXPENSE) }
    var error by remember { mutableStateOf<String?>(null) }

    AppScreen("Categories", onBack = onBack) {
        Section("Add a category") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = newType == CategoryType.EXPENSE, onClick = { newType = CategoryType.EXPENSE }, label = { Text("Expense") })
                FilterChip(selected = newType == CategoryType.INCOME, onClick = { newType = CategoryType.INCOME }, label = { Text("Income") })
            }
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Message(error, MessageKind.ERROR)
            PrimaryButton("Add", onClick = {
                val name = newName.trim()
                when {
                    name.isEmpty() -> error = "Enter a name."
                    categories.any { it.type == newType && it.name.equals(name, ignoreCase = true) } -> error = "That category already exists."
                    else -> { onAdd(name, newType); newName = ""; error = null }
                }
            })
        }
        listOf(CategoryType.EXPENSE to "Expense categories", CategoryType.INCOME to "Income categories").forEach { (type, title) ->
            Section(title, "Archiving hides a category from pickers; past entries keep it.") {
                categories.filter { it.type == type }.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            c.name + if (c.archived) " (archived)" else "",
                            modifier = Modifier.weight(1f),
                            color = if (c.archived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        TextButton(onClick = { onToggleArchive(c) }) { Text(if (c.archived) "Restore" else "Archive") }
                    }
                }
            }
        }
    }
}
