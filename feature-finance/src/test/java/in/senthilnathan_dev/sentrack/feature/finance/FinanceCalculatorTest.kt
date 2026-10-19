package `in`.senthilnathan_dev.sentrack.feature.finance

import `in`.senthilnathan_dev.sentrack.core.data.finance.AccountEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.AccountKind
import `in`.senthilnathan_dev.sentrack.core.data.finance.BudgetEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.GoalEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.TransactionEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FinanceCalculatorTest {
    private fun rs(r: Long) = r * 100

    private fun account(id: String, kind: AccountKind, opening: Long = 0, archived: Boolean = false) =
        AccountEntity(id, id, kind, rs(opening), null, archived, 0, 0)

    private var seq = 0
    private fun tx(
        type: TxType, amount: Long, date: LocalDate = LocalDate.of(2026, 10, 15), from: String? = null, to: String? = null,
        category: String? = null, principal: Long? = null,
    ) = TransactionEntity("t${seq++}", date.toEpochDay(), type, rs(amount), from, to, category, principal?.let { rs(it) }, null, 0)

    private val bank = account("bank", AccountKind.BANK, opening = 100_000)
    private val wallet = account("wallet", AccountKind.WALLET)
    private val mf = account("mf", AccountKind.MUTUAL_FUND)
    private val pf = account("pf", AccountKind.PF)

    private fun summaries(txs: List<TransactionEntity>, vararg accounts: AccountEntity) =
        FinanceCalculator.summarize(accounts.toList(), txs).associateBy { it.account.id }

    @Test
    fun liquidBalance_followsIncomeExpenseAndTransfers() {
        val s = summaries(
            listOf(
                tx(TxType.INCOME, 50_000, to = null, from = "bank"),
                tx(TxType.EXPENSE, 10_000, from = "bank"),
                tx(TxType.TRANSFER, 2_000, from = "bank", to = "wallet"),
            ),
            bank, wallet,
        )
        assertEquals(rs(100_000 + 50_000 - 10_000 - 2_000), s["bank"]!!.balancePaise)
        assertEquals(rs(2_000), s["wallet"]!!.balancePaise)
    }

    @Test
    fun investAndFullWithdrawal_giveGainAndMoveMoneyBack() {
        val s = summaries(
            listOf(
                tx(TxType.INVEST, 5_000, from = "bank", to = "mf"),
                tx(TxType.INVEST, 5_000, from = "bank", to = "mf"),
                tx(TxType.WITHDRAW, 11_000, from = "mf", to = "bank", principal = 10_000),
            ),
            bank, mf,
        )
        assertEquals(rs(100_000 - 10_000 + 11_000), s["bank"]!!.balancePaise)
        val m = s["mf"]!!
        assertEquals(0L, m.balancePaise)                // all principal returned
        assertEquals(rs(10_000), m.investedPaise)
        assertEquals(rs(11_000), m.withdrawnPaise)
        assertEquals(rs(1_000), m.realisedGainPaise)
    }

    @Test
    fun partialWithdrawal_keepsRemainingPrincipal() {
        val s = summaries(
            listOf(
                tx(TxType.INVEST, 10_000, from = "bank", to = "mf"),
                tx(TxType.WITHDRAW, 4_500, from = "mf", to = "bank", principal = 4_000),
            ),
            bank, mf,
        )
        assertEquals(rs(6_000), s["mf"]!!.balancePaise)
        assertEquals(rs(500), s["mf"]!!.realisedGainPaise)
    }

    @Test
    fun withdrawalBelowPrincipal_isALoss() {
        val s = summaries(
            listOf(tx(TxType.INVEST, 10_000, from = "bank", to = "mf"), tx(TxType.WITHDRAW, 9_000, from = "mf", to = "bank", principal = 10_000)),
            bank, mf,
        )
        assertEquals(-rs(1_000), s["mf"]!!.realisedGainPaise)
    }

    @Test
    fun withdrawalWithoutPrincipal_meansNoGainNoLoss() {
        val s = summaries(listOf(tx(TxType.INVEST, 3_000, from = "bank", to = "mf"), tx(TxType.WITHDRAW, 3_000, from = "mf", to = "bank")), bank, mf)
        assertEquals(0L, s["mf"]!!.realisedGainPaise)
        assertEquals(0L, s["mf"]!!.balancePaise)
    }

    @Test
    fun externalPfContribution_doesNotTouchAnyBank() {
        val s = summaries(listOf(tx(TxType.INVEST, 12_000, from = null, to = "pf")), bank, pf)
        assertEquals(rs(100_000), s["bank"]!!.balancePaise)
        assertEquals(rs(12_000), s["pf"]!!.balancePaise)
    }

    @Test
    fun openingBalanceOfAnInvestment_countsAsPrincipal() {
        val old = account("gold", AccountKind.GOLD, opening = 50_000)
        val s = summaries(emptyList(), old)
        assertEquals(rs(50_000), s["gold"]!!.balancePaise)
        assertEquals(rs(50_000), s["gold"]!!.investedPaise)
    }

    @Test
    fun netWorth_addsLiquidAndInvestedAtCost_andSkipsArchived() {
        val oldAccount = account("old", AccountKind.BANK, opening = 999, archived = true)
        val list = FinanceCalculator.summarize(
            listOf(bank, mf, oldAccount),
            listOf(tx(TxType.INVEST, 20_000, from = "bank", to = "mf")),
        )
        assertEquals(rs(100_000), FinanceCalculator.netWorthPaise(list))      // 80,000 liquid + 20,000 invested
        assertEquals(rs(80_000), FinanceCalculator.liquidPaise(list))
        assertEquals(rs(20_000), FinanceCalculator.investedNowPaise(list))
    }

    @Test
    fun monthSummary_onlyCountsThatMonth() {
        val oct = LocalDate.of(2026, 10, 5)
        val sep = LocalDate.of(2026, 9, 28)
        val txs = listOf(
            tx(TxType.INCOME, 60_000, oct, to = null, from = "bank", category = "salary"),
            tx(TxType.EXPENSE, 8_000, oct, from = "bank", category = "rent"),
            tx(TxType.EXPENSE, 2_000, oct, from = "bank", category = "food"),
            tx(TxType.EXPENSE, 1_500, oct, from = "bank", category = "food"),
            tx(TxType.EXPENSE, 7_000, sep, from = "bank", category = "rent"),
            tx(TxType.INVEST, 5_000, oct, from = "bank", to = "mf"),
            tx(TxType.WITHDRAW, 1_100, oct, from = "mf", to = "bank", principal = 1_000),
            tx(TxType.TRANSFER, 500, oct, from = "bank", to = "wallet"),
        )
        val m = FinanceCalculator.monthSummary(txs, 2026, 10)
        assertEquals(rs(60_000), m.incomePaise)
        assertEquals(rs(11_500), m.expensePaise)
        assertEquals(rs(5_000), m.investedPaise)
        assertEquals(rs(1_100), m.withdrawnPaise)
        assertEquals(rs(100), m.realisedGainPaise)
        assertEquals(rs(8_000), m.expenseByCategory["rent"])
        assertEquals(rs(3_500), m.expenseByCategory["food"])
        assertEquals((60_000 - 11_500) / 60_000.0, m.savingsRate!!, 1e-9)
    }

    @Test
    fun savingsRate_isNullWithoutIncome() {
        assertNull(FinanceCalculator.monthSummary(emptyList(), 2026, 10).savingsRate)
    }

    @Test
    fun budgetStatus_showsSpentRemainingAndOver() {
        val month = FinanceCalculator.monthSummary(
            listOf(tx(TxType.EXPENSE, 3_500, from = "bank", category = "food"), tx(TxType.EXPENSE, 12_000, from = "bank", category = "rent")),
            2026, 10,
        )
        val statuses = FinanceCalculator.budgetStatuses(
            listOf(BudgetEntity("food", rs(5_000)), BudgetEntity("rent", rs(10_000)), BudgetEntity("travel", rs(2_000))), month,
        ).associateBy { it.categoryId }
        assertEquals(rs(1_500), statuses["food"]!!.remainingPaise)
        assertFalse(statuses["food"]!!.isOver)
        assertTrue(statuses["rent"]!!.isOver)
        assertEquals(0L, statuses["travel"]!!.spentPaise)
        assertEquals(0.7, statuses["food"]!!.fraction, 1e-9)
    }

    @Test
    fun goalProgress_countsLinkedAccountsAndMonthlyNeed() {
        val list = FinanceCalculator.summarize(
            listOf(bank, mf, account("rd", AccountKind.RECURRING_DEPOSIT)),
            listOf(tx(TxType.INVEST, 20_000, from = "bank", to = "rd")),
        )
        val goal = GoalEntity("g", "Trip", rs(60_000), LocalDate.of(2027, 4, 15).toEpochDay(), false, 0)
        val p = FinanceCalculator.goalProgress(goal, setOf("rd"), list, LocalDate.of(2026, 10, 19))
        assertEquals(rs(20_000), p.currentPaise)
        assertEquals(6L, p.monthsLeft)                       // Oct -> Apr
        assertEquals(rs(40_000), p.remainingPaise)
        assertEquals(666_667L, p.neededPerMonthPaise)        // 40,000 / 6 months, rounded up to the next paisa
        assertEquals(20_000.0 / 60_000.0, p.fraction, 1e-9)
        assertFalse(p.isReached)
    }

    @Test
    fun goalReached_hasNoMonthlyNeed_andPastDateHasNoMonths() {
        val list = FinanceCalculator.summarize(listOf(account("cash", AccountKind.CASH, opening = 5_000)), emptyList())
        val reached = FinanceCalculator.goalProgress(GoalEntity("g", "x", rs(4_000), null, false, 0), setOf("cash"), list, LocalDate.of(2026, 10, 19))
        assertTrue(reached.isReached)
        assertNull(reached.neededPerMonthPaise)
        assertEquals(1.0, reached.fraction, 0.0)
        val past = FinanceCalculator.goalProgress(GoalEntity("g", "x", rs(9_000), LocalDate.of(2026, 1, 1).toEpochDay(), false, 0), setOf("cash"), list, LocalDate.of(2026, 10, 19))
        assertNull(past.monthsLeft)
    }
}
