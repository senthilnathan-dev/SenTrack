package `in`.senthilnathan_dev.sentrack.feature.work

import `in`.senthilnathan_dev.sentrack.core.data.work.LeaveEntryEntity
import java.time.DayOfWeek
import java.time.LocalDate

/** Leave arithmetic: which leave year a date falls in, and how many working days a leave uses. */
internal object LeaveMath {
    /** First and last day of the leave year containing [date] when the year starts in [startMonth] (1 = Jan, 4 = Apr). */
    fun leaveYear(date: LocalDate, startMonth: Int): ClosedRange<LocalDate> {
        val startYear = if (date.monthValue >= startMonth) date.year else date.year - 1
        val start = LocalDate.of(startYear, startMonth, 1)
        return start..start.plusYears(1).minusDays(1)
    }

    /** "2026" for a January year, "2026-27" for an April one. */
    fun leaveYearLabel(range: ClosedRange<LocalDate>): String =
        if (range.start.monthValue == 1) "${range.start.year}" else "${range.start.year}-${(range.start.year + 1) % 100}"

    /** Working days (Monday to Friday) from [start] to [end] inclusive; a half day counts 0.5. */
    fun workingDays(start: LocalDate, end: LocalDate, halfDay: Boolean): Double {
        if (end.isBefore(start)) return 0.0
        var count = 0
        var d = start
        while (!d.isAfter(end)) {
            if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) count++
            d = d.plusDays(1)
        }
        return if (halfDay) count * 0.5 else count.toDouble()
    }

    /** Working days of [entry] that fall inside [range] (a leave can straddle two leave years). */
    fun daysWithin(entry: LeaveEntryEntity, range: ClosedRange<LocalDate>): Double {
        val start = maxOf(LocalDate.ofEpochDay(entry.startEpochDay), range.start)
        val end = minOf(LocalDate.ofEpochDay(entry.endEpochDay), range.endInclusive)
        return workingDays(start, end, entry.halfDay)
    }

    /** Every calendar day covered by the leave entries, for shading a calendar. */
    fun coveredDays(entries: List<LeaveEntryEntity>): Set<LocalDate> =
        entries.flatMap { e ->
            generateSequence(LocalDate.ofEpochDay(e.startEpochDay)) { it.plusDays(1) }
                .takeWhile { !it.isAfter(LocalDate.ofEpochDay(e.endEpochDay)) }.toList()
        }.toSet()

    fun format(days: Double): String = if (days == days.toLong().toDouble()) days.toLong().toString() else "%.1f".format(java.util.Locale.ENGLISH, days)
}
