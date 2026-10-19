package `in`.senthilnathan_dev.sentrack.feature.personal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DateMathTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun yearly_thisYearIfAhead_otherwiseNextYear() {
        val birthday = d(1990, 11, 3)
        assertEquals(d(2026, 11, 3), DateMath.nextOccurrence(birthday, true, d(2026, 10, 20)))
        assertEquals(d(2027, 11, 3), DateMath.nextOccurrence(birthday, true, d(2026, 11, 4)))
        assertEquals(d(2026, 11, 3), DateMath.nextOccurrence(birthday, true, d(2026, 11, 3)))   // today counts
    }

    @Test
    fun yearly_leapDayFallsOn28thInOtherYears() {
        val leap = d(2000, 2, 29)
        assertEquals(d(2027, 2, 28), DateMath.nextOccurrence(leap, true, d(2026, 10, 20)))
        assertEquals(d(2028, 2, 29), DateMath.nextOccurrence(leap, true, d(2027, 3, 1)))
    }

    @Test
    fun oneOff_isItselfUntilItHasPassed() {
        assertEquals(d(2026, 12, 1), DateMath.nextOccurrence(d(2026, 12, 1), false, d(2026, 10, 20)))
        assertNull(DateMath.nextOccurrence(d(2026, 1, 1), false, d(2026, 10, 20)))
    }

    @Test
    fun countdownWording() {
        assertEquals("Today", DateMath.countdown(0))
        assertEquals("Tomorrow", DateMath.countdown(1))
        assertEquals("In 6 days", DateMath.countdown(6))
        assertEquals("In 3 weeks", DateMath.countdown(21))
        assertEquals("In 4 months", DateMath.countdown(125))
        assertEquals("In 1 year", DateMath.countdown(400))
        assertEquals("In 2 years", DateMath.countdown(800))
    }

    @Test
    fun yearsAndOrdinals() {
        assertEquals(36, DateMath.yearsAt(d(1990, 11, 3), d(2026, 11, 3)))
        assertEquals(0, DateMath.yearsAt(d(2030, 1, 1), d(2026, 1, 1)))
        assertEquals("1st", DateMath.ordinal(1))
        assertEquals("2nd", DateMath.ordinal(2))
        assertEquals("22nd", DateMath.ordinal(22))
        assertEquals("11th", DateMath.ordinal(11))
        assertEquals("13th", DateMath.ordinal(13))
        assertEquals("30th", DateMath.ordinal(30))
    }

    @Test
    fun reminderDay_goesBackTheRightNumberOfDays() {
        assertEquals(d(2026, 12, 27), DateMath.reminderDay(d(2027, 1, 3), 7))
        assertEquals(d(2026, 11, 3), DateMath.reminderDay(d(2026, 11, 3), 0))
    }
}
