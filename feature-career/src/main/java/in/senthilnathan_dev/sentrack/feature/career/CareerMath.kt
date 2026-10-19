package `in`.senthilnathan_dev.sentrack.feature.career

import java.time.LocalDate

/** The stages of a job application, in order, then how it ended. */
internal enum class JobStatus(val label: String, val open: Boolean) {
    WISHLIST("Wishlist", true), APPLIED("Applied", true), SCREENING("Screening", true), INTERVIEW("Interview", true), OFFER("Offer", true),
    ACCEPTED("Accepted", false), REJECTED("Rejected", false), WITHDRAWN("Withdrawn", false);

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: WISHLIST
    }
}

internal object CareerMath {
    const val INTERVIEW_LEAD_MILLIS = 60 * 60 * 1000L
    const val CERT_LEAD_DAYS = 30L

    /** When the reminder for an interview at [atMillis] should ring: an hour before. */
    fun interviewReminderAt(atMillis: Long): Long = atMillis - INTERVIEW_LEAD_MILLIS

    /** The day to remind about a certification expiring on [expiryEpochDay]: a month before. */
    fun certReminderDay(expiryEpochDay: Long): Long = expiryEpochDay - CERT_LEAD_DAYS

    /** How a certification's expiry reads: "No expiry", "Expired 3 days ago", "Expires today", "Expires in 12 days". */
    fun expiryText(expiryEpochDay: Long?, today: Long): String {
        if (expiryEpochDay == null) return "No expiry"
        val days = expiryEpochDay - today
        return when {
            days < 0 -> "Expired ${-days} day${if (days == -1L) "" else "s"} ago"
            days == 0L -> "Expires today"
            days < 90 -> "Expires in $days day${if (days == 1L) "" else "s"}"
            else -> "Expires ${LocalDate.ofEpochDay(expiryEpochDay)}"
        }
    }

    /** True when a certification has expired or expires within [withinDays]. */
    fun needsAttention(expiryEpochDay: Long?, today: Long, withinDays: Long = 60): Boolean =
        expiryEpochDay != null && expiryEpochDay - today <= withinDays

    /** Share of applications that got past the first screen, as a whole percent; null with no applications sent. */
    fun responseRate(statuses: List<JobStatus>): Int? {
        val sent = statuses.filter { it != JobStatus.WISHLIST }
        if (sent.isEmpty()) return null
        val replied = sent.count { it != JobStatus.APPLIED && it != JobStatus.WITHDRAWN }
        return replied * 100 / sent.size
    }
}
