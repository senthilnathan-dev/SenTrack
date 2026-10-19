package `in`.senthilnathan_dev.sentrack.feature.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.data.finance.AccountEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.AccountKind
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.ChoiceRow
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
import `in`.senthilnathan_dev.sentrack.core.ui.PrimaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section

/** The Accounts section: your accounts grouped into spendable money and investments, with totals. */
@Composable
internal fun ColumnScope.AccountsContent(
    summaries: List<AccountSummary>,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val active = summaries.filterNot { it.account.archived }
    val archived = summaries.filter { it.account.archived }
    val liquid = active.filterNot { it.account.kind.isInvestment }
    val investments = active.filter { it.account.kind.isInvestment }

    if (active.isEmpty()) {
        Section("Get started", "Add the places your money sits: a bank account, cash, a wallet, PF, each mutual fund, gold, FD or RD.") {}
    } else {
        if (liquid.isNotEmpty()) AccountGroup("Spendable money", liquid, FinanceCalculator.liquidPaise(summaries), onOpen)
        if (investments.isNotEmpty()) AccountGroup("Investments", investments, FinanceCalculator.investedNowPaise(summaries), onOpen)
    }
    PrimaryButton("Add account", onClick = onAdd)
    if (archived.isNotEmpty()) AccountGroup("Archived", archived, null, onOpen)
}

@Composable
private fun AccountGroup(title: String, items: List<AccountSummary>, totalPaise: Long?, onOpen: (String) -> Unit) {
    Section(title, if (totalPaise != null) "Total ${Money.format(totalPaise)}" else null) {
        items.forEachIndexed { index, s ->
            if (index > 0) HorizontalDivider()
            AccountRow(s, onClick = { onOpen(s.account.id) })
        }
    }
}

@Composable
private fun AccountRow(s: AccountSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(s.account.name, style = MaterialTheme.typography.bodyLarge)
            Text(s.account.kind.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.format(s.balancePaise), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End)
            if (s.account.kind.isInvestment && s.realisedGainPaise != 0L) {
                Text(
                    "Realised ${Money.formatSigned(s.realisedGainPaise)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (s.realisedGainPaise > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Create or edit an account. The kind can only be chosen when creating: changing it later would change its meaning. */
@Composable
internal fun AccountEditor(
    existing: AccountEntity?,
    onSave: (name: String, kind: AccountKind, openingPaise: Long, note: String?) -> Unit,
    onToggleArchive: () -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var kind by remember { mutableStateOf(existing?.kind ?: AccountKind.BANK) }
    var amount by remember { mutableStateOf(existing?.let { Money.toInput(it.openingBalancePaise) }.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AppScreen(if (existing == null) "New account" else "Edit account", onBack = onBack) {
        Section("Details") {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (for example HDFC Savings, Axis Bluechip Fund)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (existing == null) {
            Section("Type") {
                AccountKind.entries.forEach { k ->
                    ChoiceRow(k.label, selected = kind == k) { kind = k }
                }
            }
        } else {
            Section("Type") { Text(existing.kind.label) }
        }

        Section(
            if (kind.isInvestment) "Already invested" else "Current balance",
            if (kind.isInvestment) {
                "How much you had put into this investment before you started using SenTrack. Leave empty if you are starting fresh."
            } else {
                "What is in this account today. Leave empty for zero."
            },
        ) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount in ₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Message(error, MessageKind.ERROR)
        PrimaryButton("Save", onClick = {
            val opening = if (amount.isBlank()) 0L else Money.parse(amount)
            when {
                name.isBlank() -> error = "Give the account a name."
                opening == null -> error = "Enter the amount as a number, for example 25000 or 1,250.50."
                else -> onSave(name.trim(), kind, opening, note.ifBlank { null })
            }
        })
        if (existing != null) {
            Section("Archive", "Archived accounts keep their history but are hidden from your totals and pickers. Nothing is deleted.") {
                SecondaryButton(if (existing.archived) "Restore account" else "Archive account", onClick = onToggleArchive)
            }
        }
    }
}
