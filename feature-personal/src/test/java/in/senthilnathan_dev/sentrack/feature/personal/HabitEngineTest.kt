package `in`.senthilnathan_dev.sentrack.feature.personal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitEngineTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun logs(vararg dates: LocalDate, amount: Double = 1.0) = dates.associateWith { amount }
    private fun daily(start: LocalDate, target: Double = 1.0, days: Set<DayOfWeek> = emptySet()) = HabitRule(HabitPeriod.DAY, target, days, start)

    @Test
    fun periodBoundaries() {
        // 21 Oct 2026 is a Wednesday.
        assertEquals(d(2026, 10, 19), HabitEngine.periodStart(HabitPeriod.WEEK, d(2026, 10, 21)))
        assertEquals(d(2026, 10, 1), HabitEngine.periodStart(HabitPeriod.MONTH, d(2026, 10, 21)))
        assertEquals(d(2026, 10, 1), HabitEngine.periodStart(HabitPeriod.QUARTER, d(2026, 12, 31)))
        assertEquals(d(2026, 7, 1), HabitEngine.periodStart(HabitPeriod.QUARTER, d(2026, 9, 30)))
        assertEquals(d(2026, 1, 1), HabitEngine.periodStart(HabitPeriod.YEAR, d(2026, 10, 21)))
        assertEquals(d(2026, 10, 26), HabitEngine.nextPeriodStart(HabitPeriod.WEEK, d(2026, 10, 19)))
        assertEquals(d(2027, 1, 1), HabitEngine.nextPeriodStart(HabitPeriod.QUARTER, d(2026, 10, 1)))
    }

    @Test
    fun dailyStreak_countsBackThroughConsecutiveDays() {
        val rule = daily(d(2026, 10, 1))
        val l = logs(d(2026, 10, 17), d(2026, 10, 18), d(2026, 10, 19), d(2026, 10, 20))
        assertEquals(4, HabitEngine.currentStreak(rule, l, d(2026, 10, 20)))
    }

    @Test
    fun todayNotDoneYet_doesNotBreakTheStreak() {
        val rule = daily(d(2026, 10, 1))
        val l = logs(d(2026, 10, 18), d(2026, 10, 19))
        assertEquals(2, HabitEngine.currentStreak(rule, l, d(2026, 10, 20)))
    }

    @Test
    fun aMissedDay_breaksTheStreak() {
        val rule = daily(d(2026, 10, 1))
        val l = logs(d(2026, 10, 15), d(2026, 10, 16), d(2026, 10, 18), d(2026, 10, 19), d(2026, 10, 20))
        assertEquals(3, HabitEngine.currentStreak(rule, l, d(2026, 10, 20)))
        assertEquals(3, HabitEngine.bestStreak(rule, l, d(2026, 10, 20)))
    }

    @Test
    fun bestStreak_canBeInThePast() {
        val rule = daily(d(2026, 10, 1))
        val l = logs(d(2026, 10, 2), d(2026, 10, 3), d(2026, 10, 4), d(2026, 10, 5), d(2026, 10, 19))
        assertEquals(4, HabitEngine.bestStreak(rule, l, d(2026, 10, 20)))
        assertEquals(1, HabitEngine.currentStreak(rule, l, d(2026, 10, 20)))
    }

    @Test
    fun scheduledWeekdays_areSkippedWithoutBreakingTheStreak() {
        // Weekdays only. Fri 16 Oct, Mon 19 Oct, Tue 20 Oct done; the weekend is skipped.
        val rule = daily(d(2026, 10, 1), days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
        val l = logs(d(2026, 10, 15), d(2026, 10, 16), d(2026, 10, 19), d(2026, 10, 20))
        assertEquals(4, HabitEngine.currentStreak(rule, l, d(2026, 10, 20)))
        // Checked on Sunday 18 Oct (not scheduled): the weekend is skipped; Fri and Thu count, Wed was missed.
        assertEquals(2, HabitEngine.currentStreak(rule, l, d(2026, 10, 18)))
    }

    @Test
    fun weeklyTarget_needsEnoughCompletionsInTheWeek() {
        val rule = HabitRule(HabitPeriod.WEEK, 3.0, emptySet(), d(2026, 9, 1))
        val thisWeek = logs(d(2026, 10, 19), d(2026, 10, 21))              // 2 of 3 so far
        val lastWeek = logs(d(2026, 10, 12), d(2026, 10, 14), d(2026, 10, 16)) // 3: met
        val l = thisWeek + lastWeek
        assertEquals(2.0, HabitEngine.progress(rule, l, d(2026, 10, 19)), 1e-9)
        assertEquals(1, HabitEngine.currentStreak(rule, l, d(2026, 10, 21)))     // this week in progress, last week met
        val l2 = l + logs(d(2026, 10, 23))
        assertEquals(2, HabitEngine.currentStreak(rule, l2, d(2026, 10, 23)))   // this week now met too
    }

    @Test
    fun numericHabit_sumsTheAmounts() {
        val rule = daily(d(2026, 10, 1), target = 8.0)
        val l = mapOf(d(2026, 10, 20) to 8.0, d(2026, 10, 19) to 5.0, d(2026, 10, 18) to 9.0)
        assertEquals(1, HabitEngine.currentStreak(rule, l, d(2026, 10, 20)))     // 19th missed the target
        assertEquals("5 of 8 glasses today", HabitEngine.progressText(rule, "glasses", 5.0))
        assertEquals("2.5 of 8 today", HabitEngine.progressText(rule, null, 2.5))
    }

    @Test
    fun monthlyAndQuarterlyAndYearly_streaks() {
        val monthly = HabitRule(HabitPeriod.MONTH, 1.0, emptySet(), d(2026, 6, 1))
        val ml = logs(d(2026, 8, 10), d(2026, 9, 5), d(2026, 10, 2))
        assertEquals(3, HabitEngine.currentStreak(monthly, ml, d(2026, 10, 20)))

        val quarterly = HabitRule(HabitPeriod.QUARTER, 1.0, emptySet(), d(2025, 1, 1))
        val ql = logs(d(2026, 1, 15), d(2026, 5, 1), d(2026, 8, 1))
        assertEquals(3, HabitEngine.currentStreak(quarterly, ql, d(2026, 10, 20)).coerceAtMost(3))
        assertEquals(3, HabitEngine.bestStreak(quarterly, ql, d(2026, 10, 20)))

        val yearly = HabitRule(HabitPeriod.YEAR, 1.0, emptySet(), d(2023, 1, 1))
        val yl = logs(d(2024, 3, 1), d(2025, 3, 1), d(2026, 3, 1))
        assertEquals(3, HabitEngine.currentStreak(yearly, yl, d(2026, 10, 20)))
    }

    @Test
    fun completionRate_overFinishedPeriods() {
        val rule = daily(d(2026, 10, 1))
        val l = logs(d(2026, 10, 15), d(2026, 10, 17), d(2026, 10, 19))
        // Last 5 finished days: 15..19 -> done on 15, 17, 19 = 3 of 5.
        assertEquals(0.6, HabitEngine.completionRate(rule, l, d(2026, 10, 20), 5)!!, 1e-9)
    }

    @Test
    fun completionRate_isNullBeforeAnyPeriodHasFinished() {
        assertNull(HabitEngine.completionRate(daily(d(2026, 10, 20)), emptyMap(), d(2026, 10, 20), 7))
    }

    @Test
    fun trackingStartsAtTheStartDate() {
        val rule = daily(d(2026, 10, 18))
        val l = logs(d(2026, 10, 18), d(2026, 10, 19), d(2026, 10, 20))
        assertEquals(3, HabitEngine.currentStreak(rule, l, d(2026, 10, 20)))
        assertEquals(3, HabitEngine.periodsMet(rule, l, d(2026, 10, 20)))
    }
}
