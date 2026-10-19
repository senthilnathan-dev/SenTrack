package `in`.senthilnathan_dev.sentrack.feature.personal

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Calendar maths for important dates: when something next happens, how far away it is, how many years it has been. */
internal object DateMath {
    /**
     * The next time [date] comes round on or after [today]. A yearly date repeats on the same day every year
     * (29 February falls on 28 February in years without one); a one-off returns itself, or null once it has passed.
     */
    fun nextOccurrence(date: LocalDate, yearly: Boolean, today: LocalDate): LocalDate? {
        if (!yearly) return date.takeIf { !it.isBefore(today) }
        val thisYear = onYear(date, today.year)
        return if (!thisYear.isBefore(today)) thisYear else onYear(date, today.year + 1)
    }

    private fun onYear(date: LocalDate, year: Int): LocalDate =
        if (date.monthValue == 2 && date.dayOfMonth == 29 && !java.time.Year.isLeap(year.toLong())) LocalDate.of(year, 2, 28)
        else LocalDate.of(year, date.monthValue, date.dayOfMonth)

    fun daysUntil(target: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, target)

    /** "Today", "Tomorrow", "In 5 days", "In 3 weeks", "In 2 months". */
    fun countdown(days: Long): String = when {
        days <= 0L -> "Today"
        days == 1L -> "Tomorrow"
        days < 14 -> "In $days days"
        days < 60 -> "In ${days / 7} weeks"
        days < 365 -> "In ${days / 30} months"
        else -> "In ${days / 365} year${if (days / 365 == 1L) "" else "s"}"
    }

    /** Like [countdown] for a target date that may already have passed: "Overdue by 3 days". */
    fun countdownFrom(targetEpochDay: Long, todayEpochDay: Long): String {
        val days = targetEpochDay - todayEpochDay
        return if (days < 0) "Overdue by ${-days} day${if (days == -1L) "" else "s"}" else countdown(days)
    }

    /** Whole years from [origin] to the occurrence on [on] (the age a person turns, the anniversary number). */
    fun yearsAt(origin: LocalDate, on: LocalDate): Int = (on.year - origin.year).coerceAtLeast(0)

    /** 1st, 2nd, 3rd, 4th... */
    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }

    /** The day a reminder should fire: [daysBefore] days before [occurrence]. */
    fun reminderDay(occurrence: LocalDate, daysBefore: Int): LocalDate = occurrence.minusDays(daysBefore.toLong())
}
