package `in`.senthilnathan_dev.sentrack.core.sync

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * A backup schedule expressed the way people think about it ("every day at 9:30 PM"), not as cron syntax.
 * Each kind converts to a cron expression ([toCron]) for [CronSchedule] to compute run times, and to a
 * sentence ([describe]) for the screen. [Custom] is the escape hatch for power users.
 *
 * Every non-custom kind is guaranteed to respect [SchedulePresets.MIN_GAP_MINUTES].
 */
sealed interface ScheduleSpec {
    fun toCron(): String

    /** The first run strictly after [from]. Exact for every kind; the default follows [toCron]. */
    fun nextAfter(from: ZonedDateTime): ZonedDateTime = CronSchedule.parse(toCron()).nextAfter(from)

    /** A sentence such as "Every day at 2:00 AM". */
    fun describe(): String

    /** Compact text form for storage; reversed by [decode]. */
    fun encode(): String

    /** Every [hours] hours, on the hour (12:00 AM, then every [hours] hours). */
    data class EveryHours(val hours: Int) : ScheduleSpec {
        init {
            require(hours in HOUR_CHOICES) { "Unsupported interval" }
        }

        override fun toCron() = if (hours == 1) "0 * * * *" else "0 */$hours * * *"
        override fun describe() = if (hours == 1) "Every hour" else "Every $hours hours"
        override fun encode() = "H|$hours"
    }

    data class Daily(val time: LocalTime) : ScheduleSpec {
        override fun toCron() = "${time.minute} ${time.hour} * * *"
        override fun describe() = "Every day at ${formatTime(time)}"
        override fun encode() = "D|${time.hour}:${time.minute}"
    }

    data class Weekly(val days: Set<DayOfWeek>, val time: LocalTime) : ScheduleSpec {
        init {
            require(days.isNotEmpty()) { "Choose at least one day" }
        }

        override fun toCron() = "${time.minute} ${time.hour} * * ${days.sortedBy { it.value }.joinToString(",") { (it.value % 7).toString() }}"
        override fun describe(): String {
            val names = days.sortedBy { it.value }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
            return if (days.size == 7) "Every day at ${formatTime(time)}" else "Every $names at ${formatTime(time)}"
        }

        override fun encode() = "W|${days.sortedBy { it.value }.joinToString(",") { it.value.toString() }}|${time.hour}:${time.minute}"
    }

    /** Once a month. Days 29 to 31 are not offered because not every month has them. */
    data class Monthly(val dayOfMonth: Int, val time: LocalTime) : ScheduleSpec {
        init {
            require(dayOfMonth in 1..28) { "Day must be 1 to 28" }
        }

        override fun toCron() = "${time.minute} ${time.hour} $dayOfMonth * *"
        override fun describe() = "Monthly on the ${ordinal(dayOfMonth)} at ${formatTime(time)}"
        override fun encode() = "M|$dayOfMonth|${time.hour}:${time.minute}"
    }


    /**
     * Once a month on the 29th, 30th or 31st. Months too short for that day use their last day instead (a 31st becomes
     * 30 April, 28 or 29 February...), which is how bills and salaries behave. [toCron] is only an approximation (the 28th)
     * because cron cannot say "last day"; use [nextAfter], which is exact.
     */
    data class MonthlyClamped(val dayOfMonth: Int, val time: LocalTime) : ScheduleSpec {
        init {
            require(dayOfMonth in 29..31) { "Day must be 29 to 31; use Monthly for 1 to 28" }
        }

        override fun toCron() = "${time.minute} ${time.hour} 28 * *"
        override fun describe() =
            if (dayOfMonth == 31) "Monthly on the last day at ${formatTime(time)}"
            else "Monthly on the ${ordinal(dayOfMonth)} (the last day in shorter months) at ${formatTime(time)}"

        override fun encode() = "N|$dayOfMonth|${time.hour}:${time.minute}"

        override fun nextAfter(from: ZonedDateTime): ZonedDateTime {
            var month = YearMonth.from(from)
            repeat(14) {
                val date = month.atDay(minOf(dayOfMonth, month.lengthOfMonth()))
                val at = ZonedDateTime.of(date, time, from.zone)
                if (at.isAfter(from)) return at
                month = month.plusMonths(1)
            }
            throw IllegalStateException("No next date found")
        }
    }

    /** Once a year on a fixed date (birthdays, anniversaries, renewals). 29 February is allowed and then only runs in leap years. */
    data class Yearly(val month: Int, val day: Int, val time: LocalTime) : ScheduleSpec {
        init {
            require(month in 1..12) { "Month must be 1 to 12" }
            require(day in 1..YearMonth.of(2024, month).lengthOfMonth()) { "No such day in that month" }
        }

        override fun toCron() = "${time.minute} ${time.hour} $day $month *"
        override fun describe() =
            "Every year on $day ${Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} at ${formatTime(time)}"
        override fun encode() = "Y|$month|$day|${time.hour}:${time.minute}"
    }

    /** A raw cron expression, for people who know what they want. */
    data class Custom(val cron: String) : ScheduleSpec {
        init {
            CronSchedule.parse(cron)
        }

        override fun toCron() = cron.trim()
        override fun describe() = "Custom schedule (${cron.trim()})"
        override fun encode() = "C|${cron.trim()}"
    }

    companion object {
        /** Intervals that divide the day evenly, so runs stay evenly spaced. */
        val HOUR_CHOICES = listOf(1, 2, 3, 4, 6, 8, 12)

        val DEFAULT: ScheduleSpec = Daily(LocalTime.of(2, 0))

        /** @return the spec, or null if [text] is not a valid stored schedule. */
        fun decode(text: String?): ScheduleSpec? = runCatching {
            val parts = text!!.split('|')
            when (parts[0]) {
                "H" -> EveryHours(parts[1].toInt())
                "D" -> Daily(time(parts[1]))
                "W" -> Weekly(parts[1].split(',').map { DayOfWeek.of(it.toInt()) }.toSet(), time(parts[2]))
                "M" -> Monthly(parts[1].toInt(), time(parts[2]))
                "N" -> MonthlyClamped(parts[1].toInt(), time(parts[2]))
                "Y" -> Yearly(parts[1].toInt(), parts[2].toInt(), time(parts[3]))
                "C" -> Custom(parts.drop(1).joinToString("|"))
                else -> null
            }
        }.getOrNull()

        private fun time(text: String): LocalTime {
            val (h, m) = text.split(':').map { it.toInt() }
            return LocalTime.of(h, m)
        }

        /** 12-hour clock text such as "9:30 PM". Written by hand: JDK date formatters vary by version and locale. */
        fun formatTime(time: LocalTime): String {
            val hour12 = if (time.hour % 12 == 0) 12 else time.hour % 12
            return "%d:%02d %s".format(Locale.ENGLISH, hour12, time.minute, if (time.hour < 12) "AM" else "PM")
        }

        fun ordinal(n: Int): String {
            val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
            return "$n$suffix"
        }
    }
}
