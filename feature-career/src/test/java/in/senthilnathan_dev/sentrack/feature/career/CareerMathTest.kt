package `in`.senthilnathan_dev.sentrack.feature.career

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CareerMathTest {
    @Test
    fun expiryText_readsNaturally() {
        assertEquals("No expiry", CareerMath.expiryText(null, 100))
        assertEquals("Expired 3 days ago", CareerMath.expiryText(97, 100))
        assertEquals("Expired 1 day ago", CareerMath.expiryText(99, 100))
        assertEquals("Expires today", CareerMath.expiryText(100, 100))
        assertEquals("Expires in 1 day", CareerMath.expiryText(101, 100))
        assertEquals("Expires in 12 days", CareerMath.expiryText(112, 100))
    }

    @Test
    fun needsAttention_flagsExpiredAndSoon() {
        assertTrue(CareerMath.needsAttention(90, 100))
        assertTrue(CareerMath.needsAttention(150, 100))
        assertFalse(CareerMath.needsAttention(300, 100))
        assertFalse(CareerMath.needsAttention(null, 100))
    }

    @Test
    fun reminderTimes() {
        assertEquals(1_000_000L - 3_600_000L, CareerMath.interviewReminderAt(1_000_000L))
        assertEquals(70L, CareerMath.certReminderDay(100))
    }

    @Test
    fun responseRate_countsRepliesAmongSentApplications() {
        assertNull(CareerMath.responseRate(listOf(JobStatus.WISHLIST)))
        // 4 sent (wishlist ignored): applied, rejected, interview, withdrawn -> 2 replied of 4.
        val rate = CareerMath.responseRate(listOf(JobStatus.WISHLIST, JobStatus.APPLIED, JobStatus.REJECTED, JobStatus.INTERVIEW, JobStatus.WITHDRAWN))
        assertEquals(50, rate)
    }

    @Test
    fun jobStatus_unknownFallsBackToWishlist() {
        assertEquals(JobStatus.WISHLIST, JobStatus.of("???"))
        assertEquals(JobStatus.OFFER, JobStatus.of("OFFER"))
    }
}
