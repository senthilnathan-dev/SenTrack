package `in`.senthilnathan_dev.sentrack.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ScheduleSpecTest {
    private val at930pm = LocalTime.of(21, 30)

    @Test
    fun daily_cronAndSentence() {
        val s = ScheduleSpec.Daily(LocalTime.of(2, 0))
        assertEquals("0 2 * * *", s.toCron())
        assertEquals("Every day at 2:00 AM", s.describe())
        assertEquals("Every day at 9:30 PM", ScheduleSpec.Daily(at930pm).describe())
        assertEquals("Every day at 12:05 AM", ScheduleSpec.Daily(LocalTime.of(0, 5)).describe())
        assertEquals("Every day at 12:00 PM", ScheduleSpec.Daily(LocalTime.NOON).describe())
    }

    @Test
    fun everyHours() {
        assertEquals("0 * * * *", ScheduleSpec.EveryHours(1).toCron())
        assertEquals("0 */6 * * *", ScheduleSpec.EveryHours(6).toCron())
        assertEquals("Every hour", ScheduleSpec.EveryHours(1).describe())
        assertEquals("Every 6 hours", ScheduleSpec.EveryHours(6).describe())
        try {
            ScheduleSpec.EveryHours(5); fail()
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun weekly_mapsDaysAndSundayIsZero() {
        val s = ScheduleSpec.Weekly(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY, DayOfWeek.SUNDAY), at930pm)
        // Days are listed Monday-first, with Sunday written as 0 in cron.
        assertEquals("30 21 * * 1,3,0", s.toCron())
        assertEquals("Every Mon, Wed, Sun at 9:30 PM", s.describe())
        assertEquals("Every day at 9:30 PM", ScheduleSpec.Weekly(DayOfWeek.values().toSet(), at930pm).describe())
        try {
            ScheduleSpec.Weekly(emptySet(), at930pm); fail()
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun monthly() {
        val s = ScheduleSpec.Monthly(1, LocalTime.of(2, 0))
        assertEquals("0 2 1 * *", s.toCron())
        assertEquals("Monthly on the 1st at 2:00 AM", s.describe())
        assertEquals("Monthly on the 22nd at 2:00 AM", ScheduleSpec.Monthly(22, LocalTime.of(2, 0)).describe())
        assertEquals("Monthly on the 11th at 2:00 AM", ScheduleSpec.Monthly(11, LocalTime.of(2, 0)).describe())
        try {
            ScheduleSpec.Monthly(29, LocalTime.of(2, 0)); fail()
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun custom_validatesCron() {
        assertEquals("0 3 * * 0", ScheduleSpec.Custom(" 0 3 * * 0 ").toCron())
        try {
            ScheduleSpec.Custom("nonsense"); fail()
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun encodeDecode_roundTripsEveryKind() {
        val specs = listOf(
            ScheduleSpec.EveryHours(4),
            ScheduleSpec.Daily(LocalTime.of(7, 5)),
            ScheduleSpec.Weekly(setOf(DayOfWeek.FRIDAY, DayOfWeek.TUESDAY), at930pm),
            ScheduleSpec.Monthly(15, LocalTime.of(23, 59)),
            ScheduleSpec.Custom("*/30 * * * *"),
        )
        specs.forEach { assertEquals(it, ScheduleSpec.decode(it.encode())) }
    }

    @Test
    fun decode_rejectsGarbage() {
        listOf(null, "", "X|1", "H|5", "D|25:00", "W||1:00", "M|40|2:00", "C|nonsense", "D|a:b").forEach {
            assertNull("should reject: $it", ScheduleSpec.decode(it))
        }
    }

    @Test
    fun everyNonCustomKind_isValidCron_withMinimumGap() {
        val specs = ScheduleSpec.HOUR_CHOICES.map { ScheduleSpec.EveryHours(it) } + listOf(
            ScheduleSpec.Daily(LocalTime.MIDNIGHT),
            ScheduleSpec.Weekly(DayOfWeek.values().toSet(), LocalTime.of(23, 59)),
            ScheduleSpec.Monthly(28, LocalTime.of(12, 0)),
        )
        specs.forEach {
            val cron = CronSchedule.parse(it.toCron())
            assertTrue(it.describe(), cron.hasMinimumGap(SchedulePresets.MIN_GAP_MINUTES))
        }
    }

    @Test
    fun nextRun_matchesTheSentence() {
        val from = ZonedDateTime.of(2026, 10, 19, 10, 0, 0, 0, ZoneOffset.UTC) // a Monday
        val weekly = ScheduleSpec.Weekly(setOf(DayOfWeek.WEDNESDAY), at930pm)
        assertEquals(ZonedDateTime.of(2026, 10, 21, 21, 30, 0, 0, ZoneOffset.UTC), CronSchedule.parse(weekly.toCron()).nextAfter(from))
    }

    @Test
    fun yearly_cronSentenceAndRoundTrip() {
        val s = ScheduleSpec.Yearly(3, 14, LocalTime.of(9, 0))
        assertEquals("0 9 14 3 *", s.toCron())
        assertEquals("Every year on 14 Mar at 9:00 AM", s.describe())
        assertEquals(s, ScheduleSpec.decode(s.encode()))
        val from = ZonedDateTime.of(2026, 10, 19, 10, 0, 0, 0, ZoneOffset.UTC)
        assertEquals(ZonedDateTime.of(2027, 3, 14, 9, 0, 0, 0, ZoneOffset.UTC), CronSchedule.parse(s.toCron()).nextAfter(from))
    }

    @Test
    fun yearly_rejectsImpossibleDates_butAllowsLeapDay() {
        try {
            ScheduleSpec.Yearly(2, 30, LocalTime.NOON); fail()
        } catch (_: IllegalArgumentException) {
        }
        try {
            ScheduleSpec.Yearly(13, 1, LocalTime.NOON); fail()
        } catch (_: IllegalArgumentException) {
        }
        val leap = ScheduleSpec.Yearly(2, 29, LocalTime.of(8, 0))
        val from = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        assertEquals(ZonedDateTime.of(2028, 2, 29, 8, 0, 0, 0, ZoneOffset.UTC), CronSchedule.parse(leap.toCron()).nextAfter(from))
    }

    @Test
    fun monthlyClamped_usesTheLastDayOfShortMonths() {
        val s = ScheduleSpec.MonthlyClamped(31, LocalTime.of(9, 0))
        val zone = ZoneOffset.UTC
        fun next(y: Int, m: Int, d: Int) = s.nextAfter(ZonedDateTime.of(y, m, d, 10, 0, 0, 0, zone)).toLocalDate()
        assertEquals(java.time.LocalDate.of(2026, 2, 28), next(2026, 1, 31))   // after Jan 31 morning: the next is Feb 28
        assertEquals(java.time.LocalDate.of(2026, 4, 30), next(2026, 3, 31))
        assertEquals(java.time.LocalDate.of(2028, 2, 29), next(2028, 1, 31))   // leap year
        assertEquals(java.time.LocalDate.of(2026, 1, 31), s.nextAfter(ZonedDateTime.of(2026, 1, 15, 0, 0, 0, 0, zone)).toLocalDate())
    }

    @Test
    fun monthlyClamped_roundTripsAndDescribes() {
        val s = ScheduleSpec.MonthlyClamped(30, LocalTime.of(9, 0))
        assertEquals(s, ScheduleSpec.decode(s.encode()))
        assertEquals("Monthly on the last day at 9:00 AM", ScheduleSpec.MonthlyClamped(31, LocalTime.of(9, 0)).describe())
        assertEquals("Monthly on the 30th (the last day in shorter months) at 9:00 AM", s.describe())
        try {
            ScheduleSpec.MonthlyClamped(28, LocalTime.of(9, 0)); fail()
        } catch (e: IllegalArgumentException) { /* expected */ }
    }
}
