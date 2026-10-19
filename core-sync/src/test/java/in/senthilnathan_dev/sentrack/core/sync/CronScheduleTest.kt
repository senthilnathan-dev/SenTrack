package `in`.senthilnathan_dev.sentrack.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CronScheduleTest {
    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneOffset.UTC)

    private fun next(expr: String, from: ZonedDateTime) = CronSchedule.parse(expr).nextAfter(from)

    @Test
    fun daily_runsNextDay_whenTimePassed() {
        assertEquals(utc(2026, 10, 20, 2, 0), next("0 2 * * *", utc(2026, 10, 19, 10, 0)))
    }

    @Test
    fun daily_runsSameDay_whenTimeAhead() {
        assertEquals(utc(2026, 10, 19, 2, 0), next("0 2 * * *", utc(2026, 10, 19, 1, 0)))
    }

    @Test
    fun result_isStrictlyAfter() {
        assertEquals(utc(2026, 10, 19, 10, 15), next("*/15 * * * *", utc(2026, 10, 19, 10, 7)))
        assertEquals(utc(2026, 10, 19, 10, 30), next("*/15 * * * *", utc(2026, 10, 19, 10, 15)))
    }

    @Test
    fun hourlyAndStepsAcrossHours() {
        assertEquals(utc(2026, 10, 19, 12, 0), next("0 */6 * * *", utc(2026, 10, 19, 7, 0)))
        assertEquals(utc(2026, 10, 20, 0, 0), next("0 */6 * * *", utc(2026, 10, 19, 18, 0)))
    }

    @Test
    fun yearly_and_leapDay() {
        assertEquals(utc(2027, 1, 1, 0, 0), next("0 0 1 1 *", utc(2026, 10, 19, 0, 0)))
        assertEquals(utc(2028, 2, 29, 0, 0), next("0 0 29 2 *", utc(2026, 1, 1, 0, 0)))
    }

    @Test
    fun weekday_monday_isMondayNotSunday() {
        // 19 Oct 2026 is a Monday.
        assertEquals(utc(2026, 10, 26, 9, 30), next("30 9 * * 1", utc(2026, 10, 19, 10, 0)))
        assertEquals(utc(2026, 10, 19, 9, 30), next("30 9 * * MON", utc(2026, 10, 19, 8, 0)))
    }

    @Test
    fun sunday_isZeroOrSeven() {
        val from = utc(2026, 10, 19, 0, 0)
        assertEquals(utc(2026, 10, 25, 2, 0), next("0 2 * * 0", from))
        assertEquals(utc(2026, 10, 25, 2, 0), next("0 2 * * 7", from))
        assertEquals(utc(2026, 10, 25, 2, 0), next("0 2 * * SUN", from))
    }

    @Test
    fun dayRange_names() {
        // Saturday 24 Oct 2026 08:00 -> next weekday morning is Monday 26th.
        assertEquals(utc(2026, 10, 26, 8, 0), next("0 8 * * MON-FRI", utc(2026, 10, 24, 8, 0)))
    }

    @Test
    fun domAndDow_bothRestricted_useOr() {
        // 13th of the month OR any Friday. After Monday 19 Oct 2026 the first match is Friday 23 Oct.
        assertEquals(utc(2026, 10, 23, 0, 0), next("0 0 13 * 5", utc(2026, 10, 19, 12, 0)))
    }

    @Test
    fun listsAndRanges() {
        assertEquals(utc(2026, 10, 19, 9, 10), next("10,40 9-10 * * *", utc(2026, 10, 19, 8, 0)))
        assertEquals(utc(2026, 10, 19, 9, 40), next("10,40 9-10 * * *", utc(2026, 10, 19, 9, 10)))
    }

    @Test
    fun monthNames() {
        assertEquals(utc(2026, 12, 1, 6, 0), next("0 6 1 DEC *", utc(2026, 10, 19, 0, 0)))
    }

    @Test
    fun usesTheCallersTimeZone() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val from = ZonedDateTime.of(2026, 10, 19, 10, 0, 0, 0, kolkata)
        val n = CronSchedule.parse("0 2 * * *").nextAfter(from)
        assertEquals(ZonedDateTime.of(2026, 10, 20, 2, 0, 0, 0, kolkata), n)
    }

    @Test
    fun nextRuns_returnsIncreasingTimes() {
        val runs = CronSchedule.parse("0 * * * *").nextRuns(utc(2026, 10, 19, 10, 30), 3)
        assertEquals(listOf(utc(2026, 10, 19, 11, 0), utc(2026, 10, 19, 12, 0), utc(2026, 10, 19, 13, 0)), runs)
    }

    @Test
    fun invalidExpressions_areRejected() {
        listOf("61 * * * *", "* * * *", "*/0 * * * *", "0 0 30 2 *", "a b c d e", "0 24 * * *", "5-1 * * * *", "0 0 0 * *", "* * * 13 *")
            .forEach { assertNull("should reject: $it", CronSchedule.parseOrNull(it)) }
        assertNotNull(CronSchedule.parseOrNull("0 0 * * *"))
    }

    @Test
    fun minimumGap_enforced() {
        assertFalse(CronSchedule.parse("*/5 * * * *").hasMinimumGap(15))
        assertFalse(CronSchedule.parse("0,10 2 * * *").hasMinimumGap(15))
        assertTrue(CronSchedule.parse("*/15 * * * *").hasMinimumGap(15))
        assertTrue(CronSchedule.parse("0 * * * *").hasMinimumGap(15))
    }

    @Test
    fun allPresets_areValidAndRespectMinimumGap() {
        SchedulePresets.ALL.forEach {
            val s = CronSchedule.parse(it.cron)
            assertTrue(it.label, s.hasMinimumGap(SchedulePresets.MIN_GAP_MINUTES))
        }
    }
}
