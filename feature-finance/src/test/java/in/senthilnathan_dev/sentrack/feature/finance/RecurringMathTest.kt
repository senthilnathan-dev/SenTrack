package `in`.senthilnathan_dev.sentrack.feature.finance

import `in`.senthilnathan_dev.sentrack.core.data.finance.RecurringEntity
import `in`.senthilnathan_dev.sentrack.core.data.finance.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecurringMathTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private fun rule(
        day: Int = 5, start: LocalDate = d(2026, 1, 10), end: LocalDate? = null, last: LocalDate? = null, paused: Boolean = false,
    ) = RecurringEntity(
        "r", "Rent", TxType.EXPENSE, 1_500_000, "a", null, null, day, start.toEpochDay(), end?.toEpochDay(), last?.toEpochDay(), paused, true, null, 0,
    )

    @Test
    fun pending_startsAtTheStartDate_notBefore() {
        // Rule starts 10 Jan; the 5th of January is before it. Feb 5 and Mar 5 are due by 6 Mar.
        assertEquals(listOf(d(2026, 2, 5), d(2026, 3, 5)), RecurringMath.pendingDates(rule(), d(2026, 3, 6)))
    }

    @Test
    fun pending_resumesAfterTheLastHandledDate() {
        assertEquals(listOf(d(2026, 3, 5)), RecurringMath.pendingDates(rule(last = d(2026, 2, 5)), d(2026, 3, 6)))
        assertEquals(emptyList<LocalDate>(), RecurringMath.pendingDates(rule(last = d(2026, 3, 5)), d(2026, 3, 6)))
    }

    @Test
    fun pending_includesToday_andNotTomorrow() {
        assertEquals(listOf(d(2026, 2, 5)), RecurringMath.pendingDates(rule(), d(2026, 2, 5)))
        assertEquals(emptyList<LocalDate>(), RecurringMath.pendingDates(rule(), d(2026, 2, 4)))
    }

    @Test
    fun pending_respectsEndDateAndPause() {
        assertEquals(listOf(d(2026, 2, 5)), RecurringMath.pendingDates(rule(end = d(2026, 2, 20)), d(2026, 6, 1)))
        assertEquals(emptyList<LocalDate>(), RecurringMath.pendingDates(rule(paused = true), d(2026, 6, 1)))
    }

    @Test
    fun pending_isCappedAtTheLatestTwelve() {
        val list = RecurringMath.pendingDates(rule(start = d(2020, 1, 1)), d(2026, 6, 1))
        assertEquals(12, list.size)
        assertEquals(d(2026, 6, 1).let { d(2026, 5, 5) }, list.last())
    }

    @Test
    fun nextDue_looksForward() {
        assertEquals(d(2026, 4, 5), RecurringMath.nextDue(rule(), d(2026, 3, 6)))
        assertEquals(d(2026, 3, 5), RecurringMath.nextDue(rule(), d(2026, 3, 5)))
        assertNull(RecurringMath.nextDue(rule(end = d(2026, 3, 1)), d(2026, 3, 6)))
        assertNull(RecurringMath.nextDue(rule(paused = true), d(2026, 3, 6)))
    }

    @Test
    fun describe_usesOrdinals() {
        assertEquals("Every month on the 1st", RecurringMath.describe(rule(day = 1)))
        assertEquals("Every month on the 22nd", RecurringMath.describe(rule(day = 22)))
        assertEquals("Every month on the 11th", RecurringMath.describe(rule(day = 11)))
    }
}
