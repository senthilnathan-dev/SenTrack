package `in`.senthilnathan_dev.sentrack.feature.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.data.finance.AccountEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.CategoryEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.CategoryType
import `in`.senthilnathan_dev.sentrack.core.data.finance.TransactionEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.TxType
import `in`.senthilnathan_dev.sentrack.core.ui.ActionBar
import `in`.senthilnathan_dev.sentrack.core.ui.PickerField
import `in`.senthilnathan_dev.sentrack.core.ui.DateField
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
import `in`.senthilnathan_dev.sentrack.core.ui.PrimaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import `in`.senthilnathan_dev.sentrack.core.ui.SwitchRow
import java.time.LocalDate
import java.util.UUID

internal fun TxType.title(): String = when (this) {
    TxType.EXPENSE -> "Expense"
    TxType.INCOME -> "Income"
    TxType.TRANSFER -> "Transfer"
    TxType.INVEST -> "Invest"
    TxType.WITHDRAW -> "Withdraw"
}

private fun accountOptions(list: List<AccountEntity>): List<Pair<String, String?>> = list.map { Pair<String, String?>(it.name, it.id) }

private fun nameOf(accounts: List<AccountEntity>, id: String?, ifNone: String): String =
    accounts.firstOrNull { it.id == id }?.name ?: ifNone

/**
 * Add or edit one entry. The fields shown depend on the type:
 * expense and income use one account and a category; a transfer moves between two spendable accounts; investing
 * moves money into an investment (from an account, or from outside such as an employer PF contribution);
 * withdrawing takes money out of one investment and works out the gain or loss.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntryScreen(
    existing: TransactionEntity?,
    initialType: TxType,
    initialAccountId: String?,
    summaries: List<AccountSummary>,
    categories: List<CategoryEntity>,
    onSave: (tx: TransactionEntity, thenSplit: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onAddCategory: (name: String, type: CategoryType) -> Unit,
    onBack: () -> Unit,
) {
    val accounts = summaries.map { it.account }
        .filter { !it.archived || it.id == existing?.accountId || it.id == existing?.toAccountId }
    val liquid = accounts.filter { !it.kind.isInvestment }
    val investments = accounts.filter { it.kind.isInvestment }

    var type by remember { mutableStateOf(existing?.type ?: initialType) }
    var amountText by remember { mutableStateOf(existing?.let { Money.toInput(it.amountPaise) }.orEmpty()) }
    var date by remember { mutableStateOf(existing?.dateEpochDay ?: LocalDate.now().toEpochDay()) }
    var accountId by remember { mutableStateOf(existing?.accountId) }
    var toAccountId by remember { mutableStateOf(existing?.toAccountId) }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var withdrawAll by remember { mutableStateOf(existing == null) }
    var principalText by remember { mutableStateOf(existing?.principalReturnedPaise?.let { Money.toInput(it) }.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    // Start from sensible defaults the first time the screen opens for a new entry.
    remember {
        if (existing == null) {
            val preferred = accounts.firstOrNull { it.id == initialAccountId }
            when (initialType) {
                TxType.WITHDRAW -> accountId = (preferred?.takeIf { it.kind.isInvestment } ?: investments.singleOrNull())?.id
                TxType.INVEST -> { accountId = liquid.singleOrNull()?.id; toAccountId = preferred?.takeIf { it.kind.isInvestment }?.id }
                else -> accountId = (preferred?.takeIf { !it.kind.isInvestment } ?: liquid.singleOrNull())?.id
            }
        }
        true
    }

    fun resetFor(t: TxType) {
        type = t
        categoryId = null
        toAccountId = null
        accountId = if (t == TxType.WITHDRAW) investments.singleOrNull()?.id else liquid.singleOrNull()?.id
        error = null
    }

    // For a withdrawal: the principal that can still be returned from the chosen investment.
    val availablePrincipal = run {
        val balance = summaries.firstOrNull { it.account.id == accountId }?.balancePaise ?: 0L
        val own = if (existing?.type == TxType.WITHDRAW && existing.accountId == accountId) existing.principalReturnedPaise ?: existing.amountPaise else 0L
        balance + own
    }
    val received = Money.parse(amountText)
    val principal = if (withdrawAll) availablePrincipal else Money.parse(principalText)

    fun build(): TransactionEntity? {
        val amount = Money.parse(amountText)
        if (amount == null || amount <= 0) { error = "Enter an amount greater than zero."; return null }
        when (type) {
            TxType.EXPENSE, TxType.INCOME -> if (accountId == null) { error = "Choose an account."; return null }
            TxType.TRANSFER -> when {
                accountId == null || toAccountId == null -> { error = "Choose both accounts."; return null }
                accountId == toAccountId -> { error = "Choose two different accounts."; return null }
            }
            TxType.INVEST -> if (toAccountId == null) { error = "Choose the investment."; return null }
            TxType.WITHDRAW -> when {
                accountId == null -> { error = "Choose the investment you are withdrawing from."; return null }
                principal == null || principal < 0 -> { error = "Enter how much invested money this returns."; return null }
                principal > availablePrincipal -> { error = "That is more than the ${Money.format(availablePrincipal)} still invested here."; return null }
            }
        }
        error = null
        return TransactionEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            dateEpochDay = date,
            type = type,
            amountPaise = amount,
            accountId = accountId,
            toAccountId = if (type == TxType.EXPENSE || type == TxType.INCOME) null else toAccountId,
            categoryId = if (type == TxType.EXPENSE || type == TxType.INCOME) categoryId else null,
            principalReturnedPaise = if (type == TxType.WITHDRAW) principal else null,
            note = note.trim().ifEmpty { null },
            createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
        )
    }
    AppScreen(
        if (existing == null) "Add entry" else "Edit entry",
        onBack = onBack,
        actions = { if (onDelete != null) IconAction(Icons.Filled.Delete, "Delete this entry", { confirmDelete = true }) },
        // Save stays pinned at the bottom, within thumb reach, with any error shown right above it.
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                PrimaryButton("Save", onClick = { build()?.let { onSave(it, false) } }, icon = Icons.Filled.Check)
                if (type == TxType.INCOME && existing == null) {
                    SecondaryButton("Save and split into investments", onClick = { build()?.let { onSave(it, true) } }, icon = Icons.Filled.CallSplit)
                }
            }
        },
    ) {
        Section("Type") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER, TxType.INVEST, TxType.WITHDRAW).forEach { t ->
                    FilterChip(selected = type == t, onClick = { if (type != t) resetFor(t) }, label = { Text(t.title()) })
                }
            }
            Text(
                when (type) {
                    TxType.EXPENSE -> "Money you spent."
                    TxType.INCOME -> "Money you received, such as salary."
                    TxType.TRANSFER -> "Moving money between your own spendable accounts."
                    TxType.INVEST -> "Money you put into an investment (SIP, FD, PF, gold...)."
                    TxType.WITHDRAW -> "Money you take out of an investment. SenTrack works out the gain or loss."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section("Details") {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text(if (type == TxType.WITHDRAW) "Amount received (₹)" else "Amount (₹)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            DateField("Date", date) { date = it }

            when (type) {
                TxType.EXPENSE, TxType.INCOME -> {
                    PickerField(
                        if (type == TxType.EXPENSE) "Paid from" else "Received in",
                        nameOf(liquid, accountId, "Choose an account"),
                        accountOptions(liquid),
                    ) { accountId = it }
                    val list = categories.filter {
                        (it.type == if (type == TxType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME) &&
                            (!it.archived || it.id == categoryId)
                    }
                    PickerField(
                        "Category",
                        list.firstOrNull { it.id == categoryId }?.name ?: "No category",
                        listOf(Pair<String, String?>("No category", null)) + list.map { Pair<String, String?>(it.name, it.id) },
                    ) { categoryId = it }
                    TextButton(onClick = { newCategoryName = ""; showNewCategory = true }) { Text("+ New category") }
                }
                TxType.TRANSFER -> {
                    PickerField("From", nameOf(liquid, accountId, "Choose an account"), accountOptions(liquid)) { accountId = it }
                    PickerField("To", nameOf(liquid, toAccountId, "Choose an account"), accountOptions(liquid.filter { it.id != accountId })) { toAccountId = it }
                }
                TxType.INVEST -> {
                    PickerField(
                        "Money comes from",
                        if (accountId == null) "Outside my accounts (for example employer PF)" else nameOf(liquid, accountId, ""),
                        listOf(Pair<String, String?>("Outside my accounts (for example employer PF)", null)) + accountOptions(liquid),
                    ) { accountId = it }
                    PickerField("Invest in", nameOf(investments, toAccountId, "Choose an investment"), accountOptions(investments)) { toAccountId = it }
                    if (investments.isEmpty()) {
                        Text("Add an investment account first (Accounts tab).", color = MaterialTheme.colorScheme.error)
                    }
                }
                TxType.WITHDRAW -> {
                    PickerField("Withdraw from", nameOf(investments, accountId, "Choose an investment"), accountOptions(investments)) { accountId = it }
                    PickerField(
                        "Money goes to",
                        if (toAccountId == null) "Not tracked in SenTrack" else nameOf(liquid, toAccountId, ""),
                        listOf(Pair<String, String?>("Not tracked in SenTrack", null)) + accountOptions(liquid),
                    ) { toAccountId = it }
                    if (investments.isEmpty()) {
                        Text("Add an investment account first (Accounts tab).", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (type == TxType.WITHDRAW) {
            Section("Gain or loss", "How much of your own invested money this withdrawal returns decides the gain or loss.") {
                SwitchRow(
                    title = "Withdrawing everything that is left",
                    subtitle = "Invested money still in this investment: ${Money.format(availablePrincipal)}",
                    checked = withdrawAll,
                    onChange = { withdrawAll = it },
                )
                if (!withdrawAll) {
                    OutlinedTextField(
                        value = principalText,
                        onValueChange = { principalText = it },
                        label = { Text("Invested money this returns (₹)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (received != null && principal != null) {
                    val gain = received - principal
                    Text(
                        when {
                            gain > 0 -> "Gain: ${Money.formatSigned(gain)}"
                            gain < 0 -> "Loss: ${Money.format(-gain)}"
                            else -> "No gain or loss"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (gain >= 0) ChartColors.income else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

    }

    if (showNewCategory) {
        val categoryType = if (type == TxType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        AlertDialog(
            onDismissRequest = { showNewCategory = false },
            title = { Text(if (categoryType == CategoryType.INCOME) "New income category" else "New expense category") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCategoryName.isNotBlank()) onAddCategory(newCategoryName.trim(), categoryType)
                    showNewCategory = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showNewCategory = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("Balances and totals will be recalculated without it. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private data class SplitRow(val destId: String?, val amount: String)

/**
 * Split money from one account across several destinations in one go, for example a salary: part to one mutual
 * fund, part to another, part to PF, and the rest stays in the account. Each row becomes one entry (an investment,
 * or a transfer when the destination is a spendable account).
 */
@Composable
internal fun SplitScreen(
    summaries: List<AccountSummary>,
    initialFromId: String?,
    onSave: (List<TransactionEntity>) -> Unit,
    onBack: () -> Unit,
) {
    val active = summaries.filter { !it.account.archived }
    val liquid = active.filter { !it.account.kind.isInvestment }
    var fromId by remember { mutableStateOf(initialFromId?.takeIf { id -> liquid.any { it.account.id == id } } ?: liquid.singleOrNull()?.account?.id) }
    var date by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
    val rows = remember { mutableStateListOf(SplitRow(null, ""), SplitRow(null, "")) }
    var error by remember { mutableStateOf<String?>(null) }

    val destinations = active.filter { it.account.id != fromId }
        .sortedByDescending { it.account.kind.isInvestment }
        .map { Pair<String, String?>("${it.account.name} (${it.account.kind.label})", it.account.id) }
    val total = rows.sumOf { Money.parse(it.amount) ?: 0L }
    val fromSummary = active.firstOrNull { it.account.id == fromId }
    val left = fromSummary?.let { it.balancePaise - total }

    fun submitSplit() {
        val source = fromId
        val filled = rows.filter { it.destId != null || it.amount.isNotBlank() }
        val parsed = filled.map { it.destId to Money.parse(it.amount) }
        when {
            source == null -> error = "Choose the account the money comes from."
            parsed.isEmpty() -> error = "Add at least one destination."
            parsed.any { it.first == null || it.second == null || it.second!! <= 0 } -> error = "Every row needs a destination and an amount."
            else -> {
                val now = System.currentTimeMillis()
                val byId = active.associateBy { it.account.id }
                onSave(
                    parsed.mapIndexed { i, (dest, amount) ->
                        val investing = byId[dest]?.account?.kind?.isInvestment == true
                        TransactionEntity(
                            id = UUID.randomUUID().toString(),
                            dateEpochDay = date,
                            type = if (investing) TxType.INVEST else TxType.TRANSFER,
                            amountPaise = amount!!,
                            accountId = source,
                            toAccountId = dest,
                            categoryId = null,
                            principalReturnedPaise = null,
                            note = null,
                            createdAtMillis = now + i,
                        )
                    },
                )
            }
        }
    }

    AppScreen(
        "Split money",
        onBack = onBack,
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                PrimaryButton("Save split", icon = Icons.Filled.Check, onClick = ::submitSplit)
            }
        },
    ) {
        Section("From", "Choose where the money is now, then share it across your investments. Whatever you do not allocate stays in the account.") {
            PickerField(
                "Account",
                fromSummary?.account?.name ?: "Choose an account",
                liquid.map { Pair<String, String?>(it.account.name, it.account.id) },
            ) { fromId = it }
            if (fromSummary != null) {
                Text("Balance: ${Money.format(fromSummary.balancePaise)}", style = MaterialTheme.typography.bodyMedium)
            }
            DateField("Date", date) { date = it }
        }

        Section("Where it goes") {
            rows.forEachIndexed { index, row ->
                PickerField(
                    "Destination ${index + 1}",
                    destinations.firstOrNull { it.second == row.destId }?.first ?: "Choose",
                    destinations,
                ) { rows[index] = row.copy(destId = it) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = row.amount,
                        onValueChange = { rows[index] = row.copy(amount = it) },
                        label = { Text("Amount (₹)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    if (rows.size > 1) IconAction(Icons.Filled.Delete, "Remove destination ${index + 1}", { rows.removeAt(index) })
                }
            }
            SecondaryButton("Add another destination", onClick = { rows.add(SplitRow(null, "")) }, icon = Icons.Filled.Add)
        }

        Section("Summary") {
            Text("Allocated: ${Money.format(total)}", style = MaterialTheme.typography.titleMedium)
            if (fromSummary != null && left != null) {
                Text(
                    if (left >= 0) "Left in ${fromSummary.account.name}: ${Money.format(left)}" else "More than the account holds by ${Money.format(-left)}",
                    color = if (left >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
