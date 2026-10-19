package `in`.senthilnathan_dev.sentrack.feature.finance

import `in`.senthilnathan_dev.sentrack.core.data.finance.AccountEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.AccountKind
import `in`.senthilnathan_dev.sentrack.core.data.finance.TransactionEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.TxType
import java.time.LocalDate
import java.time.YearMonth

/** Totals for an arbitrary period (a financial year, a custom range). */
data class PeriodSummary(
    val incomePaise: Long,
    val expensePaise: Long,
    val investedPaise: Long,
    val withdrawnPaise: Long,
    val realisedGainPaise: Long,
)

/** One point of the net-worth trend: the net worth at the end of [month]. */
data class NetWorthPoint(val month: YearMonth, val netWorthPaise: Long)

/**
 * Derived numbers for the dashboard: trends, comparisons, allocation and financial-year totals.
 * Pure functions over the data, so they are unit-tested without a phone.
 */
object FinanceInsights {

    /** Indian financial year: 1 April to 31 March. Returns the starting calendar year (2026 for FY 2026-27). */
    fun financialYearOf(date: LocalDate): Int = if (date.monthValue >= 4) date.year else date.year - 1

    /** "FY 2026-27". */
    fun financialYearLabel(startYear: Int): String = "FY %d-%02d".format(startYear, (startYear + 1) % 100)

    fun periodSummary(transactions: List<TransactionEntity>, from: LocalDate, to: LocalDate): PeriodSummary {
        var income = 0L; var expense = 0L; var invested = 0L; var withdrawn = 0L; var gain = 0L
        val a = from.toEpochDay()
        val b = to.toEpochDay()
        for (tx in transactions) {
            if (tx.dateEpochDay < a || tx.dateEpochDay > b) continue
            when (tx.type) {
                TxType.INCOME -> income += tx.amountPaise
                TxType.EXPENSE -> expense += tx.amountPaise
                TxType.INVEST -> invested += tx.amountPaise
                TxType.WITHDRAW -> { withdrawn += tx.amountPaise; gain += tx.amountPaise - (tx.principalReturnedPaise ?: tx.amountPaise) }
                TxType.TRANSFER -> Unit
            }
        }
        return PeriodSummary(income, expense, invested, withdrawn, gain)
    }

    fun financialYearSummary(transactions: List<TransactionEntity>, startYear: Int): PeriodSummary =
        periodSummary(transactions, LocalDate.of(startYear, 4, 1), LocalDate.of(startYear + 1, 3, 31))

    /** Last [months] calendar months ending at [end], oldest first. */
    fun monthlySeries(transactions: List<TransactionEntity>, end: YearMonth, months: Int): List<MonthSummary> =
        (months - 1 downTo 0).map { back ->
            val ym = end.minusMonths(back.toLong())
            FinanceCalculator.monthSummary(transactions, ym.year, ym.monthValue)
        }

    /** Net worth at the end of each of the last [months] months, oldest first. */
    fun netWorthSeries(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        end: YearMonth,
        months: Int,
    ): List<NetWorthPoint> = (months - 1 downTo 0).map { back ->
        val ym = end.minusMonths(back.toLong())
        val limit = ym.atEndOfMonth().toEpochDay()
        val upTo = transactions.filter { it.dateEpochDay <= limit }
        NetWorthPoint(ym, FinanceCalculator.netWorthPaise(FinanceCalculator.summarize(accounts, upTo)))
    }

    /** Where the invested money sits, by kind of investment, largest first. Archived and empty accounts are skipped. */
    fun allocation(summaries: List<AccountSummary>): List<Pair<AccountKind, Long>> =
        summaries.filter { !it.account.archived && it.account.kind.isInvestment && it.balancePaise > 0 }
            .groupBy { it.account.kind }
            .map { (kind, list) -> kind to list.sumOf { it.balancePaise } }
            .sortedByDescending { it.second }

    /** Biggest expense categories in [month], largest first (key null = uncategorised). */
    fun topExpenseCategories(month: MonthSummary, limit: Int): List<Pair<String?, Long>> =
        month.expenseByCategory.entries.map { it.key to it.value }.sortedByDescending { it.second }.take(limit)

    /** Relative change from [previous] to [current] (0.25 = 25% up). Null when there is nothing to compare with. */
    fun change(current: Long, previous: Long): Double? =
        if (previous == 0L) null else (current - previous).toDouble() / kotlin.math.abs(previous)
}
