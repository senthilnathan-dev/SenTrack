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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.data.finance.AccountEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.CategoryEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.CategoryType
import `in`.senthilnathan_dev.sentrack.core.data.finance.RecurringEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.TxType
import `in`.senthilnathan_dev.sentrack.core.ui.ActionBar
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.DateField
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
import `in`.senthilnathan_dev.sentrack.core.ui.PickerField
import `in`.senthilnathan_dev.sentrack.core.ui.PrimaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import `in`.senthilnathan_dev.sentrack.core.ui.SwitchRow
import `in`.senthilnathan_dev.sentrack.core.ui.formatDate
import java.time.LocalDate
import java.util.UUID

private val KINDS = listOf(TxType.INCOME to "Income", TxType.EXPENSE to "Expense", TxType.INVEST to "Invest", TxType.TRANSFER to "Transfer")

private fun kindLabel(t: TxType) = KINDS.firstOrNull { it.first == t }?.second ?: t.name

// ---------------------------------------------------------------------------------------------------------------------
// Dashboard card: what is waiting for a decision

/** One recurring item and the due date waiting for an answer. */
internal class DueItem(val rule: RecurringEntity, val date: LocalDate)

/** Items that have come due and need an answer. Adding creates a normal entry; skipping just moves on. Nothing happens by itself. */
@Composable
internal fun ColumnScope.DueNowCard(due: List<DueItem>, onAdd: (DueItem) -> Unit, onSkip: (DueItem) -> Unit) {
    if (due.isEmpty()) return
    Section("Due now", "Repeating items that have come due. Add them as entries, or skip.") {
        due.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.rule.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${kindLabel(item.rule.type)} · ${Money.format(item.rule.amountPaise)} · due ${formatDate(item.date.toEpochDay())}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButtonSmall("Add entry", Icons.Filled.Check) { onAdd(item) }
                    SecondaryButton("Skip", icon = Icons.Filled.SkipNext, onClick = { onSkip(item) })
                }
            }
        }
    }
}

@Composable
private fun PrimaryButtonSmall(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    PrimaryButton(text, onClick = onClick, icon = icon)
}

// ---------------------------------------------------------------------------------------------------------------------
// Plan section: the list

@Composable
internal fun ColumnScope.RecurringSection(rules: List<RecurringEntity>, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val today = LocalDate.now()
    Section("Repeating entries", "Salary, rent, SIPs, EMIs. SenTrack reminds you, then you add each one with a tap.") {
        if (rules.isEmpty()) Text("None yet.", style = MaterialTheme.typography.bodyMedium)
        rules.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider()
            Column(modifier = Modifier.fillMaxWidth().clickable { onOpen(r.id) }.padding(vertical = 6.dp)) {
                Text(r.name, style = MaterialTheme.typography.bodyLarge)
                val next = RecurringMath.nextDue(r, today)
                Text(
                    listOfNotNull(
                        kindLabel(r.type), Money.format(r.amountPaise), RecurringMath.describe(r),
                        if (r.paused) "paused" else next?.let { "next ${formatDate(it.toEpochDay())}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SecondaryButton("Add a repeating entry", onClick = onAdd, icon = Icons.Filled.Add)
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Editor

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecurringEditorScreen(
    existing: RecurringEntity?,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onSave: (RecurringEntity) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val liquid = accounts.filter { !it.archived && !it.kind.isInvestment }
    val investments = accounts.filter { !it.archived && it.kind.isInvestment }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: TxType.EXPENSE) }
    var amountText by rememberSaveable { mutableStateOf(existing?.amountPaise?.let { Money.toInput(it) }.orEmpty()) }
    var accountId by rememberSaveable { mutableStateOf(existing?.accountId) }
    var toAccountId by rememberSaveable { mutableStateOf(existing?.toAccountId) }
    var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId) }
    var day by rememberSaveable { mutableStateOf(existing?.dayOfMonth ?: LocalDate.now().dayOfMonth.coerceIn(1, 28)) }
    var start by rememberSaveable { mutableStateOf(existing?.startEpochDay ?: LocalDate.now().toEpochDay()) }
    var hasEnd by rememberSaveable { mutableStateOf(existing?.endEpochDay != null) }
    var end by rememberSaveable { mutableStateOf(existing?.endEpochDay ?: LocalDate.now().plusYears(1).toEpochDay()) }
    var remind by rememberSaveable { mutableStateOf(existing?.remind ?: true) }
    var paused by rememberSaveable { mutableStateOf(existing?.paused ?: false) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun accountName(list: List<AccountEntity>, id: String?, none: String) = list.firstOrNull { it.id == id }?.name ?: none
    fun options(list: List<AccountEntity>) = list.map { Pair<String, String?>(it.name, it.id) }

    fun submit() {
        val amount = Money.parse(amountText)
        when {
            name.isBlank() -> error = "Give it a name, for example Rent or Salary."
            amount == null || amount <= 0 -> error = "Enter an amount greater than zero."
            type == TxType.INCOME && accountId == null -> error = "Choose the account it arrives in."
            type == TxType.EXPENSE && accountId == null -> error = "Choose the account it is paid from."
            type == TxType.INVEST && toAccountId == null -> error = "Choose the investment it goes into."
            type == TxType.TRANSFER && (accountId == null || toAccountId == null || accountId == toAccountId) -> error = "Choose two different accounts."
            hasEnd && end < start -> error = "The end date is before the start date."
            else -> {
                error = null
                onSave(
                    RecurringEntity(
                        id = existing?.id ?: UUID.randomUUID().toString(), name = name.trim(), type = type, amountPaise = amount,
                        accountId = accountId, toAccountId = if (type == TxType.INVEST || type == TxType.TRANSFER) toAccountId else null,
                        categoryId = if (type == TxType.INCOME || type == TxType.EXPENSE) categoryId else null,
                        dayOfMonth = day, startEpochDay = start, endEpochDay = if (hasEnd) end else null,
                        lastDoneEpochDay = existing?.lastDoneEpochDay, paused = paused, remind = remind,
                        note = note.trim().ifEmpty { null }, createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    AppScreen(
        if (existing == null) "New repeating entry" else "Repeating entry",
        onBack = onBack,
        actions = { if (existing != null) IconAction(Icons.Filled.Delete, "Delete this repeating entry", { confirmDelete = true }) },
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                PrimaryButton("Save", onClick = ::submit, icon = Icons.Filled.Check)
            }
        },
    ) {
        Section("What repeats") {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (Salary, Rent, SIP…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KINDS.forEach { (t, label) ->
                    FilterChip(selected = type == t, onClick = { if (t != type) { accountId = null; toAccountId = null; categoryId = null }; type = t }, label = { Text(label) })
                }
            }
            OutlinedTextField(
                value = amountText, onValueChange = { amountText = it }, label = { Text("Amount (₹)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
            when (type) {
                TxType.INCOME, TxType.EXPENSE -> {
                    PickerField(if (type == TxType.INCOME) "Received in" else "Paid from", accountName(liquid, accountId, "Choose an account"), options(liquid)) { accountId = it }
                    val cats = categories.filter { !it.archived && it.type == (if (type == TxType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE) }
                    PickerField("Category", cats.firstOrNull { it.id == categoryId }?.name ?: "No category", listOf(Pair<String, String?>("No category", null)) + cats.map { Pair<String, String?>(it.name, it.id) }) { categoryId = it }
                }
                TxType.INVEST -> {
                    PickerField("From (optional)", accountName(liquid, accountId, "Not from my accounts"), listOf(Pair<String, String?>("Not from my accounts", null)) + options(liquid)) { accountId = it }
                    PickerField("Into", accountName(investments, toAccountId, "Choose an investment"), options(investments)) { toAccountId = it }
                }
                else -> {
                    PickerField("From", accountName(liquid, accountId, "Choose an account"), options(liquid)) { accountId = it }
                    PickerField("To", accountName(liquid, toAccountId, "Choose an account"), options(liquid.filter { it.id != accountId })) { toAccountId = it }
                }
            }
        }
        Section("When") {
            PickerField("Day of the month", RecurringMath.ordinal(day), (1..28).map { RecurringMath.ordinal(it) to it }) { day = it }
            Text("Days 29 to 31 are not offered because not every month has them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DateField("Starting from", start) { start = it }
            SwitchRow(title = "Ends on a date", checked = hasEnd, onChange = { hasEnd = it })
            if (hasEnd) DateField("Last date", end) { end = it }
            SwitchRow(title = "Remind me on the day", subtitle = "A notification at 9 am, even when SenTrack is locked.", checked = remind, onChange = { remind = it })
            if (existing != null) SwitchRow(title = "Paused", subtitle = "Stops reminders and Due now until you turn it back on.", checked = paused, onChange = { paused = it })
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note added to each entry (optional)") }, modifier = Modifier.fillMaxWidth())
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this repeating entry?") },
            text = { Text("Entries it already created stay. Only the repeat is removed.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
