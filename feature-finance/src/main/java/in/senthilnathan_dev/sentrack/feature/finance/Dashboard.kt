package `in`.senthilnathan_dev.sentrack.feature.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.data.finance.BudgetEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.CategoryEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.TransactionEntity
import `in`.senthilnathan_dev.sentrack.core.ui.PrimaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private fun YearMonth.longName() = month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year
private fun YearMonth.shortName() = month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

/** "▲ 12% vs last month", or null when there is nothing to compare with. */
private fun deltaText(current: Long, previous: Long): String? {
    val change = FinanceInsights.change(current, previous) ?: return null
    val pct = kotlin.math.abs(change * 100).toInt()
    return when {
        pct == 0 -> "no change vs last month"
        change > 0 -> "▲ $pct% vs last month"
        else -> "▼ $pct% vs last month"
    }
}

@Composable
private fun RowScope.Kpi(label: String, value: String, delta: String?, deltaColor: Color?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.weight(1f),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
            if (delta != null) Text(delta, style = MaterialTheme.typography.labelSmall, color = deltaColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The Finance landing page: where you stand (net worth and its trend), how this month is going (income, spending,
 * investing, savings rate, compared with last month), where the money went, six-month cash flow, investment
 * allocation and realised gains, and the financial-year totals that matter at tax time.
 */
@Composable
internal fun ColumnScope.DashboardContent(
    summaries: List<AccountSummary>,
    transactions: List<TransactionEntity>,
    categories: Map<String, CategoryEntity>,
    budgets: List<BudgetEntity>,
    goals: List<GoalProgress>,
    onAddAccount: () -> Unit,
    onSplit: () -> Unit,
    onOpenPlan: () -> Unit,
) {
    val activeAccounts = summaries.filterNot { it.account.archived }
    if (activeAccounts.isEmpty()) {
        Section(
            "Welcome to Finance",
            "Start by adding the places your money sits: a bank account, cash, a wallet, PF, and each mutual fund, gold holding, FD or RD. Then record entries and this page fills in.",
        ) {
            PrimaryButton("Add your first account", onClick = onAddAccount, icon = Icons.Filled.Add)
        }
        return
    }
    if (transactions.isEmpty()) {
        Section("No entries yet", "Tap “Add entry” to record your salary, spending, investments and withdrawals. This page then shows your trends.") {}
    }

    var monthOffset by rememberSaveable { mutableStateOf(0) }
    val now = YearMonth.now()
    val month = now.minusMonths(monthOffset.toLong())
    val previousMonth = month.minusMonths(1)

    val thisMonth = remember(transactions, month) { FinanceCalculator.monthSummary(transactions, month.year, month.monthValue) }
    val lastMonth = remember(transactions, month) { FinanceCalculator.monthSummary(transactions, previousMonth.year, previousMonth.monthValue) }
    val netWorthSeries = remember(summaries, transactions) {
        FinanceInsights.netWorthSeries(summaries.map { it.account }, transactions, now, 6)
    }
    val cashFlow = remember(transactions, month) { FinanceInsights.monthlySeries(transactions, month, 6) }
    val fy = FinanceInsights.financialYearOf(LocalDate.now())
    val fySummary = remember(transactions) { FinanceInsights.financialYearSummary(transactions, fy) }

    val good = ChartColors.income
    val bad = MaterialTheme.colorScheme.error

    // ---- Net worth
    Section("Net worth") {
        val netWorth = FinanceCalculator.netWorthPaise(summaries)
        Text(Money.format(netWorth), style = MaterialTheme.typography.headlineLarge)
        Text(
            "Spendable ${Money.format(FinanceCalculator.liquidPaise(summaries))} · Invested ${Money.format(FinanceCalculator.investedNowPaise(summaries))}",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (netWorthSeries.size >= 2) {
            val change = netWorthSeries.last().netWorthPaise - netWorthSeries[netWorthSeries.size - 2].netWorthPaise
            Text(
                when {
                    change > 0 -> "▲ ${Money.format(change)} since last month end"
                    change < 0 -> "▼ ${Money.format(-change)} since last month end"
                    else -> "No change since last month end"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (change >= 0) good else bad,
            )
            LineChart(netWorthSeries.map { it.netWorthPaise }, netWorthSeries.map { it.month.shortName() }, ChartColors.invested)
        }
        Text(
            "Investments are counted at the amount you put in; SenTrack does not track live prices.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // ---- Month
    Section(month.longName()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { monthOffset += 1 }) { Text("‹ Earlier") }
            TextButton(onClick = { monthOffset -= 1 }, enabled = monthOffset > 0) { Text("Later ›") }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kpi("Income", Money.format(thisMonth.incomePaise), deltaText(thisMonth.incomePaise, lastMonth.incomePaise), good)
            Kpi("Spent", Money.format(thisMonth.expensePaise), deltaText(thisMonth.expensePaise, lastMonth.expensePaise), bad)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kpi("Invested", Money.format(thisMonth.investedPaise), deltaText(thisMonth.investedPaise, lastMonth.investedPaise), good)
            Kpi(
                "Left after spending",
                Money.format(thisMonth.incomePaise - thisMonth.expensePaise),
                null,
                null,
            )
        }
        val rate = thisMonth.savingsRate
        if (rate != null) {
            LinearProgressIndicator(progress = { rate.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text(
                if (rate >= 0) "You kept ${(rate * 100).toInt()}% of your income this month." else "You spent more than you earned this month.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (rate >= 0) MaterialTheme.colorScheme.onSurface else bad,
            )
        } else {
            Text("No income recorded this month, so no savings rate yet.", style = MaterialTheme.typography.bodySmall)
        }
        if (thisMonth.withdrawnPaise > 0) {
            Text(
                "Withdrawn from investments: ${Money.format(thisMonth.withdrawnPaise)} (gain or loss ${Money.formatSigned(thisMonth.realisedGainPaise)})",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    // ---- Where the money went
    Section("Where your money went", "Biggest spending categories in ${month.longName()}.") {
        val top = FinanceInsights.topExpenseCategories(thisMonth, 6)
        if (top.isEmpty()) {
            Text("No spending recorded this month.")
        } else {
            HorizontalBars(
                top.mapIndexed { i, (categoryId, value) ->
                    Triple(categoryId?.let { categories[it]?.name } ?: "No category", value, ChartColors.palette[i % ChartColors.palette.size])
                },
            )
            HorizontalDivider()
            StatRow("Total spent", Money.format(thisMonth.expensePaise))
        }
    }

    // ---- Budgets
    Section("Budgets", "Closest to the limit first, for ${month.longName()}.") {
        val statuses = FinanceCalculator.budgetStatuses(budgets, thisMonth).sortedByDescending { it.fraction }
        if (statuses.isEmpty()) {
            Text("No budgets yet. Set a monthly limit for the categories you want to watch.")
        } else {
            statuses.take(3).forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider()
                BudgetRow(categories[s.categoryId]?.name ?: "Category", s)
            }
            val over = statuses.count { it.isOver }
            if (over > 0) {
                Text(
                    if (over == 1) "1 budget is over its limit." else "$over budgets are over their limits.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = bad,
                )
            }
        }
        SecondaryButton(if (statuses.isEmpty()) "Set up budgets" else "See all budgets", onClick = onOpenPlan, icon = Icons.Filled.Savings)
    }

    // ---- Goals
    Section("Goals") {
        if (goals.isEmpty()) {
            Text("No goals yet. Choose something to save for and which accounts count towards it.")
        } else {
            goals.take(3).forEachIndexed { i, g ->
                if (i > 0) HorizontalDivider()
                GoalRow(g)
            }
        }
        SecondaryButton(if (goals.isEmpty()) "Set up a goal" else "See all goals", onClick = onOpenPlan, icon = Icons.Filled.Savings)
    }

    // ---- Cash flow
    Section("Cash flow, last 6 months") {
        GroupedBarChart(
            labels = cashFlow.map { YearMonth.of(it.year, it.month).shortName() },
            series = listOf(
                ChartColors.income to cashFlow.map { it.incomePaise },
                ChartColors.expense to cashFlow.map { it.expensePaise },
                ChartColors.invested to cashFlow.map { it.investedPaise },
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(ChartColors.income, "Income")
            LegendDot(ChartColors.expense, "Spent")
            LegendDot(ChartColors.invested, "Invested")
        }
    }

    // ---- Investments
    Section("Investments") {
        val allocation = FinanceInsights.allocation(summaries)
        if (allocation.isEmpty()) {
            Text("No money invested yet. Add an investment account and use “Invest” entries.")
        } else {
            StatRow("Invested now (at cost)", Money.format(FinanceCalculator.investedNowPaise(summaries)))
            StackedBar(allocation.map { it.first.label to it.second })
        }
        val allTimeGain = summaries.sumOf { it.realisedGainPaise }
        HorizontalDivider()
        StatRow(
            "Realised gain or loss, all time",
            Money.formatSigned(allTimeGain),
            if (allTimeGain > 0) good else if (allTimeGain < 0) bad else null,
        )
        StatRow(
            "Realised gain or loss, ${FinanceInsights.financialYearLabel(fy)}",
            Money.formatSigned(fySummary.realisedGainPaise),
            if (fySummary.realisedGainPaise > 0) good else if (fySummary.realisedGainPaise < 0) bad else null,
        )
        SecondaryButton("Split money across investments", onClick = onSplit, icon = Icons.Filled.CallSplit)
    }

    // ---- Financial year
    Section("${FinanceInsights.financialYearLabel(fy)} so far", "April to March. Handy when you file your taxes.") {
        StatRow("Income", Money.format(fySummary.incomePaise))
        StatRow("Spent", Money.format(fySummary.expensePaise))
        StatRow("Invested", Money.format(fySummary.investedPaise))
        StatRow("Withdrawn from investments", Money.format(fySummary.withdrawnPaise))
    }
}
