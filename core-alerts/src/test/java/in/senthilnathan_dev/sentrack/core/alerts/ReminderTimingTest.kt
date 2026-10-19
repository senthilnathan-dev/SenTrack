package `in`.senthilnathan_dev.sentrack.core.alerts

import `in`.senthilnathan_dev.sentrack.core.sync.ScheduleSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ReminderTimingTest {
    private val utc = ZoneOffset.UTC
    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, utc).toInstant().toEpochMilli()

    private fun def(oneOff: Long? = null, repeat: ScheduleSpec? = null) =
        ReminderDef("r1", "standalone", null, "Pay rent", AlertStyle.NOTIFICATION, oneOff, repeat)

    @Test
    fun oneOff_inTheFuture_firesAtItsTime() {
        val at = millis(2026, 10, 25, 9, 0)
        assertEquals(at, ReminderTiming.nextFire(def(oneOff = at), millis(2026, 10, 20, 0, 0), utc))
    }

    @Test
    fun oneOff_inThePast_hasNoNextFire() {
        val at = millis(2026, 10, 1, 9, 0)
        assertNull(ReminderTiming.nextFire(def(oneOff = at), millis(2026, 10, 20, 0, 0), utc))
        assertNull(ReminderTiming.nextFire(def(oneOff = at), at, utc))   // strictly after
    }

    @Test
    fun noTimeAtAll_hasNoNextFire() {
        assertNull(ReminderTiming.nextFire(def(), 0, utc))
    }

    @Test
    fun daily_firesTodayIfAhead_otherwiseTomorrow() {
        val repeat = ScheduleSpec.Daily(LocalTime.of(21, 30))
        assertEquals(millis(2026, 10, 20, 21, 30), ReminderTiming.nextFire(def(repeat = repeat), millis(2026, 10, 20, 8, 0), utc))
        assertEquals(millis(2026, 10, 21, 21, 30), ReminderTiming.nextFire(def(repeat = repeat), millis(2026, 10, 20, 22, 0), utc))
    }

    @Test
    fun weekly_picksTheNextChosenDay() {
        // 20 Oct 2026 is a Tuesday; Mon and Fri at 7:00 -> next is Friday 23 Oct.
        val repeat = ScheduleSpec.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(7, 0))
        assertEquals(millis(2026, 10, 23, 7, 0), ReminderTiming.nextFire(def(repeat = repeat), millis(2026, 10, 20, 12, 0), utc))
    }

    @Test
    fun yearly_birthday() {
        val repeat = ScheduleSpec.Yearly(11, 3, LocalTime.of(8, 0))
        assertEquals(millis(2026, 11, 3, 8, 0), ReminderTiming.nextFire(def(repeat = repeat), millis(2026, 10, 20, 0, 0), utc))
        assertEquals(millis(2027, 11, 3, 8, 0), ReminderTiming.nextFire(def(repeat = repeat), millis(2026, 11, 3, 9, 0), utc))
    }

    @Test
    fun repeating_followsTheGivenTimeZone() {
        val kolkata = java.time.ZoneId.of("Asia/Kolkata")
        val repeat = ScheduleSpec.Daily(LocalTime.of(9, 0))
        val after = ZonedDateTime.of(2026, 10, 20, 10, 0, 0, 0, kolkata).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 10, 21, 9, 0, 0, 0, kolkata).toInstant().toEpochMilli()
        assertEquals(expected, ReminderTiming.nextFire(def(repeat = repeat), after, kolkata))
    }
}
