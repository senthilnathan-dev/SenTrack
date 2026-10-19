package `in`.senthilnathan_dev.sentrack.feature.work

import `in`.senthilnathan_dev.sentrack.core.data.work.LeaveEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LeaveMathTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun leave(from: LocalDate, to: LocalDate, half: Boolean = false) =
        LeaveEntryEntity("x", "c", "t", from.toEpochDay(), to.toEpochDay(), half, null, 0)

    @Test
    fun leaveYear_aprilStart() {
        val r = LeaveMath.leaveYear(d(2026, 2, 10), 4)
        assertEquals(d(2025, 4, 1), r.start)
        assertEquals(d(2026, 3, 31), r.endInclusive)
        assertEquals("2025-26", LeaveMath.leaveYearLabel(r))
        assertEquals(d(2026, 4, 1), LeaveMath.leaveYear(d(2026, 4, 1), 4).start)
    }

    @Test
    fun leaveYear_januaryStart() {
        val r = LeaveMath.leaveYear(d(2026, 10, 20), 1)
        assertEquals(d(2026, 1, 1), r.start)
        assertEquals(d(2026, 12, 31), r.endInclusive)
        assertEquals("2026", LeaveMath.leaveYearLabel(r))
    }

    @Test
    fun workingDays_skipWeekends() {
        // Fri 16 Oct to Tue 20 Oct 2026: Fri, Mon, Tue.
        assertEquals(3.0, LeaveMath.workingDays(d(2026, 10, 16), d(2026, 10, 20), false), 0.0)
        assertEquals(0.5, LeaveMath.workingDays(d(2026, 10, 19), d(2026, 10, 19), true), 0.0)
        assertEquals(0.0, LeaveMath.workingDays(d(2026, 10, 17), d(2026, 10, 18), false), 0.0)   // a weekend
    }

    @Test
    fun daysWithin_clipsToTheLeaveYear() {
        val year = LeaveMath.leaveYear(d(2026, 5, 1), 4)   // 1 Apr 2026 - 31 Mar 2027
        // Thu 26 Mar to Wed 1 Apr 2026: only Wed 1 Apr (and not the weekend) is inside.
        assertEquals(1.0, LeaveMath.daysWithin(leave(d(2026, 3, 26), d(2026, 4, 1)), year), 0.0)
    }

    @Test
    fun coveredDays_listsEveryCalendarDay() {
        val days = LeaveMath.coveredDays(listOf(leave(d(2026, 10, 16), d(2026, 10, 18))))
        assertEquals(setOf(d(2026, 10, 16), d(2026, 10, 17), d(2026, 10, 18)), days)
    }
}
