package `in`.senthilnathan_dev.sentrack.feature.finance

import `in`.senthilnathan_dev.sentrack.core.data.finance.AccountEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.BudgetEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.GoalEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.TransactionEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.TxType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The state of one account after applying every transaction.
 *
 * For a liquid account (bank, cash, wallet) [balancePaise] is the money in it.
 * For an investment it is the principal still invested (at cost, because SenTrack does not track live prices);
 * [investedPaise], [withdrawnPaise] and [realisedGainPaise] describe its history.
 */
data class AccountSummary(
    val account: AccountEntity,
    val balancePaise: Long,
    val investedPaise: Long,
    val withdrawnPaise: Long,
    val realisedGainPaise: Long,
)

/** Totals for one calendar month. */
data class MonthSummary(
    val year: Int,
    val month: Int,
    val incomePaise: Long,
    val expensePaise: Long,
    val investedPaise: Long,
    val withdrawnPaise: Long,
    val realisedGainPaise: Long,
    /** Expense totals per category id (null key = uncategorised). */
    val expenseByCategory: Map<String?, Long>,
) {
    /** Share of income not spent, 0..1 (null when there is no income). */
    val savingsRate: Double?
        get() = if (incomePaise > 0) ((incomePaise - expensePaise).toDouble() / incomePaise).coerceIn(-1.0, 1.0) else null
}

data class BudgetStatus(val categoryId: String, val limitPaise: Long, val spentPaise: Long) {
    val remainingPaise: Long get() = limitPaise - spentPaise
    val fraction: Double get() = if (limitPaise > 0) spentPaise.toDouble() / limitPaise else 0.0
    val isOver: Boolean get() = spentPaise > limitPaise
}

data class GoalProgress(
    val goal: GoalEntity,
    val currentPaise: Long,
    /** Months left until the target date (null if no date or already past). */
    val monthsLeft: Long?,
) {
    val fraction: Double get() = if (goal.targetPaise > 0) (currentPaise.toDouble() / goal.targetPaise).coerceIn(0.0, 1.0) else 0.0
    val remainingPaise: Long get() = (goal.targetPaise - currentPaise).coerceAtLeast(0)
    val isReached: Boolean get() = currentPaise >= goal.targetPaise

    /** What to set aside each month to reach the target on time (null without a future date or once reached). */
    val neededPerMonthPaise: Long?
        get() = if (isReached || monthsLeft == null || monthsLeft <= 0) null else (remainingPaise + monthsLeft - 1) / monthsLeft
}

/** Pure functions that turn raw accounts and transactions into the numbers the screens show. */
object FinanceCalculator {

    fun summarize(accounts: List<AccountEntity>, transactions: List<TransactionEntity>): List<AccountSummary> {
        val liquid = HashMap<String, Long>()          // flows into/out of liquid accounts
        val principalIn = HashMap<String, Long>()     // INVEST into an investment
        val principalOut = HashMap<String, Long>()    // principal returned by withdrawals
        val received = HashMap<String, Long>()        // cash received from withdrawals
        fun add(map: MutableMap<String, Long>, id: String?, delta: Long) {
            if (id != null) map[id] = (map[id] ?: 0L) + delta
        }

        for (tx in transactions) {
            when (tx.type) {
                TxType.INCOME -> add(liquid, tx.accountId, tx.amountPaise)
                TxType.EXPENSE -> add(liquid, tx.accountId, -tx.amountPaise)
                TxType.TRANSFER -> { add(liquid, tx.accountId, -tx.amountPaise); add(liquid, tx.toAccountId, tx.amountPaise) }
                TxType.INVEST -> { add(liquid, tx.accountId, -tx.amountPaise); add(principalIn, tx.toAccountId, tx.amountPaise) }
                TxType.WITHDRAW -> {
                    // Without an explicit principal the whole amount is treated as principal: no gain, no loss.
                    add(principalOut, tx.accountId, tx.principalReturnedPaise ?: tx.amountPaise)
                    add(received, tx.accountId, tx.amountPaise)
                    add(liquid, tx.toAccountId, tx.amountPaise)
                }
            }
        }

        return accounts.map { a ->
            if (a.kind.isInvestment) {
                val invested = a.openingBalancePaise + (principalIn[a.id] ?: 0L)
                val out = principalOut[a.id] ?: 0L
                val got = received[a.id] ?: 0L
                AccountSummary(a, balancePaise = invested - out, investedPaise = invested, withdrawnPaise = got, realisedGainPaise = got - out)
            } else {
                AccountSummary(a, balancePaise = a.openingBalancePaise + (liquid[a.id] ?: 0L), 0L, 0L, 0L)
            }
        }
    }

    /** Everything you own at cost: liquid balances plus principal still invested. Archived accounts are excluded. */
    fun netWorthPaise(summaries: List<AccountSummary>): Long = summaries.filterNot { it.account.archived }.sumOf { it.balancePaise }

    fun liquidPaise(summaries: List<AccountSummary>): Long =
        summaries.filter { !it.account.archived && !it.account.kind.isInvestment }.sumOf { it.balancePaise }

    fun investedNowPaise(summaries: List<AccountSummary>): Long =
        summaries.filter { !it.account.archived && it.account.kind.isInvestment }.sumOf { it.balancePaise }

    fun monthSummary(transactions: List<TransactionEntity>, year: Int, month: Int): MonthSummary {
        var income = 0L; var expense = 0L; var invested = 0L; var withdrawn = 0L; var gain = 0L
        val byCategory = HashMap<String?, Long>()
        for (tx in transactions) {
            val d = LocalDate.ofEpochDay(tx.dateEpochDay)
            if (d.year != year || d.monthValue != month) continue
            when (tx.type) {
                TxType.INCOME -> income += tx.amountPaise
                TxType.EXPENSE -> { expense += tx.amountPaise; byCategory[tx.categoryId] = (byCategory[tx.categoryId] ?: 0L) + tx.amountPaise }
                TxType.INVEST -> invested += tx.amountPaise
                TxType.WITHDRAW -> { withdrawn += tx.amountPaise; gain += tx.amountPaise - (tx.principalReturnedPaise ?: tx.amountPaise) }
                TxType.TRANSFER -> Unit
            }
        }
        return MonthSummary(year, month, income, expense, invested, withdrawn, gain, byCategory)
    }

    fun budgetStatuses(budgets: List<BudgetEntity>, month: MonthSummary): List<BudgetStatus> =
        budgets.map { BudgetStatus(it.categoryId, it.monthlyLimitPaise, month.expenseByCategory[it.categoryId] ?: 0L) }

    /** Progress counts the current balance (liquid) or principal still invested of every linked account. */
    fun goalProgress(
        goal: GoalEntity,
        linkedAccountIds: Set<String>,
        summaries: List<AccountSummary>,
        today: LocalDate,
    ): GoalProgress {
        val current = summaries.filter { it.account.id in linkedAccountIds }.sumOf { it.balancePaise }
        val months = goal.targetDateEpochDay?.let {
            val target = LocalDate.ofEpochDay(it)
            if (target.isAfter(today)) ChronoUnit.MONTHS.between(today.withDayOfMonth(1), target.withDayOfMonth(1)).coerceAtLeast(1) else null
        }
        return GoalProgress(goal, current, months)
    }
}
