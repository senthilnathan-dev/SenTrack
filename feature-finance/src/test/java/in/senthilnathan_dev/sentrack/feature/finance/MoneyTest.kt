package `in`.senthilnathan_dev.sentrack.feature.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun format_usesIndianGrouping() {
        assertEquals("₹0", Money.format(0))
        assertEquals("₹999", Money.format(99_900))
        assertEquals("₹1,000", Money.format(100_000))
        assertEquals("₹12,345", Money.format(1_234_500))
        assertEquals("₹1,23,456", Money.format(12_345_600))
        assertEquals("₹12,34,567", Money.format(123_456_700))
        assertEquals("₹1,23,45,678", Money.format(1_234_567_800))
    }

    @Test
    fun format_showsPaiseOnlyWhenNeeded() {
        assertEquals("₹1,234.50", Money.format(123_450))
        assertEquals("₹0.05", Money.format(5))
        assertEquals("1,234", Money.format(123_400, withSymbol = false))
    }

    @Test
    fun format_negativeAndSigned() {
        assertEquals("-₹1,500", Money.format(-150_000))
        assertEquals("+₹1,000", Money.formatSigned(100_000))
        assertEquals("-₹1,000", Money.formatSigned(-100_000))
        assertEquals("₹0", Money.formatSigned(0))
    }

    @Test
    fun short_isCompactForCharts() {
        assertEquals("₹0", Money.short(0))
        assertEquals("₹950", Money.short(95_000))
        assertEquals("₹1.5K", Money.short(150_000))
        assertEquals("₹1L", Money.short(10_000_000))
        assertEquals("₹2.5L", Money.short(25_000_000))
        assertEquals("₹1.25Cr", Money.short(1_250_000_000))
        assertEquals("-₹1.5K", Money.short(-150_000))
    }

    @Test
    fun parse_acceptsWhatPeopleType() {
        assertEquals(150_000L, Money.parse("1500"))
        assertEquals(150_050L, Money.parse("1,500.50"))
        assertEquals(150_050L, Money.parse("₹ 1,500.5"))
        assertEquals(150_000L, Money.parse("₹1,500"))
        assertEquals(5L, Money.parse("0.05"))
        assertEquals(1_250L, Money.parse("12.5"))
    }

    @Test
    fun parse_rejectsBadInput() {
        listOf("", "abc", "-5", "1.234", "1..2", "12e3", "1,5,0.0.0", ".5", "99999999999999").forEach {
            assertNull("should reject: $it", Money.parse(it))
        }
    }

    @Test
    fun toInput_roundTripsWithParse() {
        listOf(100_000L, 123_450L, 150_050L, 5L, 1_250L, 100L).forEach {
            assertEquals(it, Money.parse(Money.toInput(it)))
        }
        assertEquals("", Money.toInput(0))
        assertEquals("12.5", Money.toInput(1_250))
    }
}
