package `in`.senthilnathan_dev.sentrack.feature.finance

import `in`.senthilnathan_dev.sentrack.core.data.finance.AccountEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.AccountKind
import `in`.senthilnathan_dev.sentrack.core.data.finance.TransactionEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinanceInsightsTest {
    private fun rs(r: Long) = r * 100
    private fun account(id: String, kind: AccountKind, opening: Long = 0) = AccountEntity(id, id, kind, rs(opening), null, false, 0, 0)
    private var seq = 0
    private fun tx(type: TxType, amount: Long, date: LocalDate, acc: String? = null, to: String? = null, cat: String? = null, principal: Long? = null) =
        TransactionEntity("t${seq++}", date.toEpochDay(), type, rs(amount), acc, to, cat, principal?.let { rs(it) }, null, 0)

    @Test
    fun financialYear_runsAprilToMarch() {
        assertEquals(2026, FinanceInsights.financialYearOf(LocalDate.of(2026, 4, 1)))
        assertEquals(2026, FinanceInsights.financialYearOf(LocalDate.of(2027, 3, 31)))
        assertEquals(2025, FinanceInsights.financialYearOf(LocalDate.of(2026, 3, 31)))
        assertEquals("FY 2026-27", FinanceInsights.financialYearLabel(2026))
        assertEquals("FY 2099-00", FinanceInsights.financialYearLabel(2099))
    }

    @Test
    fun financialYearSummary_coversOnlyThatYear() {
        val txs = listOf(
            tx(TxType.INCOME, 1_000, LocalDate.of(2026, 3, 31), "b"),     // previous FY
            tx(TxType.INCOME, 2_000, LocalDate.of(2026, 4, 1), "b"),
            tx(TxType.EXPENSE, 500, LocalDate.of(2027, 3, 31), "b"),
            tx(TxType.INCOME, 9_000, LocalDate.of(2027, 4, 1), "b"),     // next FY
            tx(TxType.WITHDRAW, 1_200, LocalDate.of(2026, 8, 1), "mf", "b", principal = 1_000),
        )
        val s = FinanceInsights.financialYearSummary(txs, 2026)
        assertEquals(rs(2_000), s.incomePaise)
        assertEquals(rs(500), s.expensePaise)
        assertEquals(rs(1_200), s.withdrawnPaise)
        assertEquals(rs(200), s.realisedGainPaise)
    }

    @Test
    fun monthlySeries_isOldestFirstAndIncludesEmptyMonths() {
        val txs = listOf(tx(TxType.EXPENSE, 100, LocalDate.of(2026, 10, 3), "b"), tx(TxType.EXPENSE, 300, LocalDate.of(2026, 8, 9), "b"))
        val s = FinanceInsights.monthlySeries(txs, YearMonth.of(2026, 10), 3)
        assertEquals(listOf(8, 9, 10), s.map { it.month })
        assertEquals(listOf(rs(300), 0L, rs(100)), s.map { it.expensePaise })
    }

    @Test
    fun netWorthSeries_followsCumulativeEntries() {
        val accounts = listOf(account("b", AccountKind.BANK, opening = 1_000), account("mf", AccountKind.MUTUAL_FUND))
        val txs = listOf(
            tx(TxType.INCOME, 500, LocalDate.of(2026, 9, 10), "b"),
            tx(TxType.EXPENSE, 200, LocalDate.of(2026, 10, 2), "b"),
            tx(TxType.INVEST, 300, LocalDate.of(2026, 10, 5), "b", "mf"),   // moves money, net worth unchanged
        )
        val series = FinanceInsights.netWorthSeries(accounts, txs, YearMonth.of(2026, 10), 3)
        assertEquals(listOf(rs(1_000), rs(1_500), rs(1_300)), series.map { it.netWorthPaise })
    }

    @Test
    fun allocation_groupsByKind_largestFirst_skippingEmptyAndLiquid() {
        val accounts = listOf(
            account("b", AccountKind.BANK, 100),
            account("mf1", AccountKind.MUTUAL_FUND, 300),
            account("mf2", AccountKind.MUTUAL_FUND, 200),
            account("g", AccountKind.GOLD, 700),
            account("e", AccountKind.STOCK, 0),
        )
        val alloc = FinanceInsights.allocation(FinanceCalculator.summarize(accounts, emptyList()))
        assertEquals(listOf(AccountKind.GOLD to rs(700), AccountKind.MUTUAL_FUND to rs(500)), alloc)
    }

    @Test
    fun topCategories_sortedAndLimited() {
        val m = FinanceCalculator.monthSummary(
            listOf(
                tx(TxType.EXPENSE, 100, LocalDate.of(2026, 10, 1), "b", cat = "a"),
                tx(TxType.EXPENSE, 500, LocalDate.of(2026, 10, 1), "b", cat = "b"),
                tx(TxType.EXPENSE, 300, LocalDate.of(2026, 10, 1), "b", cat = "c"),
            ),
            2026, 10,
        )
        assertEquals(listOf("b" to rs(500), "c" to rs(300)), FinanceInsights.topExpenseCategories(m, 2))
    }

    @Test
    fun change_isRelativeAndNullWithoutBaseline() {
        assertEquals(0.5, FinanceInsights.change(150, 100)!!, 1e-9)
        assertEquals(-0.25, FinanceInsights.change(75, 100)!!, 1e-9)
        assertNull(FinanceInsights.change(10, 0))
    }
}
