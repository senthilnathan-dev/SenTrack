package `in`.senthilnathan_dev.sentrack.feature.finance

import `in`.senthilnathan_dev.sentrack.core.data.finance.RecurringEntity
import `in`.senthilnathan_dev.sentrack.core.sync.ScheduleSpec
import java.time.LocalDate
import java.time.YearMonth

/** Due dates for monthly recurring entries. Nothing here changes balances: it only says what is waiting for a decision. */
internal object RecurringMath {
    private const val MAX_PENDING = 12

    /** The first date this rule could fall on: its start, or the day after the last one handled. */
    private fun earliest(r: RecurringEntity): LocalDate {
        val start = LocalDate.ofEpochDay(r.startEpochDay)
        val afterLast = r.lastDoneEpochDay?.let { LocalDate.ofEpochDay(it).plusDays(1) }
        return if (afterLast != null && afterLast.isAfter(start)) afterLast else start
    }

    private fun withinEnd(r: RecurringEntity, d: LocalDate): Boolean {
        val end = r.endEpochDay ?: return true   // copied to a local: a property from another module cannot be smart-cast
        return !d.isAfter(LocalDate.ofEpochDay(end))
    }

    /**
     * Every due date not yet added or skipped, up to and including [today], oldest first. At most the latest 12, so a
     * rule left alone for years does not bury the screen. A paused rule has none.
     */
    fun pendingDates(r: RecurringEntity, today: LocalDate): List<LocalDate> {
        if (r.paused) return emptyList()
        val from = earliest(r)
        val out = ArrayList<LocalDate>()
        var month = YearMonth.from(from)
        while (!month.atDay(1).isAfter(today)) {
            val date = month.atDay(r.dayOfMonth.coerceIn(1, month.lengthOfMonth()))
            if (!date.isBefore(from) && !date.isAfter(today) && withinEnd(r, date)) out += date
            month = month.plusMonths(1)
        }
        return out.takeLast(MAX_PENDING)
    }

    /** The next date on or after [today] that is still ahead of everything handled, or null when the rule has ended. */
    fun nextDue(r: RecurringEntity, today: LocalDate): LocalDate? {
        if (r.paused) return null
        val from = maxOf(earliest(r), today)
        var month = YearMonth.from(from)
        repeat(14) {
            val date = month.atDay(r.dayOfMonth.coerceIn(1, month.lengthOfMonth()))
            if (!date.isBefore(from)) return date.takeIf { withinEnd(r, it) }
            month = month.plusMonths(1)
        }
        return null
    }

    fun ordinal(day: Int): String {
        val suffix = if (day % 100 in 11..13) "th" else when (day % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$day$suffix"
    }

    /** Day-of-month choices: 1 to 30, then 31 meaning "the last day". Months too short use their last day. */
    const val LAST_DAY = 31

    fun dayLabel(day: Int) = if (day >= LAST_DAY) "Last day of the month" else ordinal(day)

    fun describe(r: RecurringEntity) = when {
        r.dayOfMonth >= LAST_DAY -> "Every month on the last day"
        r.dayOfMonth > 28 -> "Every month on the ${ordinal(r.dayOfMonth)} (the last day in shorter months)"
        else -> "Every month on the ${ordinal(r.dayOfMonth)}"
    }
}

/** The reminder schedule for a day of the month: exact for 1 to 28, last-day-aware for 29 to 31. */
internal fun reminderSpec(day: Int): ScheduleSpec {
    val nine = java.time.LocalTime.of(9, 0)
    return if (day <= 28) ScheduleSpec.Monthly(day, nine) else ScheduleSpec.MonthlyClamped(day.coerceAtMost(31), nine)
}
