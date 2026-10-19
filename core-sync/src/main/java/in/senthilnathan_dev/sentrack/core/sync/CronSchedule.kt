package `in`.senthilnathan_dev.sentrack.core.sync

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * A standard 5-field cron expression: `minute hour day-of-month month day-of-week`.
 *
 * Supports `*`, lists (`1,15`), ranges (`1-5`), steps (every 15 minutes is a star, slash, 15 in the minute field;
 * `10-30/5` also works), month names (`JAN`) and day names (`MON-FRI`). Day-of-week is 0-6 with 0 and 7 both meaning Sunday. As in classic cron, when both day-of-month and
 * day-of-week are restricted, a day matches if **either** matches.
 *
 * This class only does the maths: it says when the next run is due. Scheduling the actual work is the caller's job.
 */
class CronSchedule private constructor(
    val expression: String,
    private val minutes: Set<Int>,
    private val hours: Set<Int>,
    private val daysOfMonth: Set<Int>,
    private val months: Set<Int>,
    private val daysOfWeek: Set<Int>,
    private val domRestricted: Boolean,
    private val dowRestricted: Boolean,
) {
    /** First run strictly after [after], in [after]'s time zone. */
    fun nextAfter(after: ZonedDateTime): ZonedDateTime {
        var t = after.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        val zone = t.zone
        repeat(MAX_SEARCH_DAYS) { dayIndex ->
            val date = t.toLocalDate()
            if (date.monthValue in months && dayMatches(date)) {
                val from = if (dayIndex == 0) t.hour * 60 + t.minute else 0
                val slot = firstSlotAtOrAfter(from)
                if (slot != null) return ZonedDateTime.of(date, LocalTime.of(slot / 60, slot % 60), zone)
            }
            t = date.plusDays(1).atStartOfDay(zone)
        }
        throw IllegalArgumentException("This schedule never runs")
    }

    /** The next [count] runs after [after]. Handy for showing a preview. */
    fun nextRuns(after: ZonedDateTime, count: Int): List<ZonedDateTime> {
        val out = ArrayList<ZonedDateTime>(count)
        var t = after
        repeat(count) { t = nextAfter(t); out += t }
        return out
    }

    /** True if no two consecutive runs (checked over a long stretch) are closer than [minutes] minutes. */
    fun hasMinimumGap(minutes: Int): Boolean {
        val runs = nextRuns(REFERENCE, 400)
        return runs.zipWithNext().all { (a, b) -> ChronoUnit.MINUTES.between(a, b) >= minutes }
    }

    private fun dayMatches(date: LocalDate): Boolean {
        val dom = date.dayOfMonth in daysOfMonth
        val dow = (date.dayOfWeek.value % 7) in daysOfWeek
        return if (domRestricted && dowRestricted) dom || dow else dom && dow
    }

    private fun firstSlotAtOrAfter(minuteOfDay: Int): Int? {
        for (h in hours.sorted()) for (m in minutes.sorted()) {
            val slot = h * 60 + m
            if (slot >= minuteOfDay) return slot
        }
        return null
    }

    override fun toString() = expression

    companion object {
        private const val MAX_SEARCH_DAYS = 366 * 8   // enough to find a 29 February
        private val REFERENCE: ZonedDateTime = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

        private val MONTH_NAMES = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        private val DAY_NAMES = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

        /** @throws IllegalArgumentException with a readable message if the expression is invalid or never fires. */
        fun parse(expression: String): CronSchedule {
            val fields = expression.trim().split(Regex("\\s+"))
            require(fields.size == 5) { "A schedule needs 5 fields: minute hour day month weekday" }
            val minutes = parseField(fields[0], 0, 59, "minute")
            val hours = parseField(fields[1], 0, 23, "hour")
            val dom = parseField(fields[2], 1, 31, "day of month")
            val months = parseField(fields[3], 1, 12, "month", MONTH_NAMES, nameBase = 1)
            val dowRaw = parseField(fields[4], 0, 7, "weekday", DAY_NAMES, nameBase = 0)
            val dow = dowRaw.map { it % 7 }.toSet()   // 7 = Sunday = 0
            val schedule = CronSchedule(
                expression = fields.joinToString(" "),
                minutes = minutes, hours = hours, daysOfMonth = dom, months = months, daysOfWeek = dow,
                domRestricted = fields[2] != "*", dowRestricted = fields[4] != "*",
            )
            schedule.nextAfter(REFERENCE)   // throws if it can never run (e.g. 30 February)
            return schedule
        }

        fun parseOrNull(expression: String): CronSchedule? = runCatching { parse(expression) }.getOrNull()

        private fun parseField(
            text: String, min: Int, max: Int, label: String,
            names: List<String>? = null, nameBase: Int = 0,
        ): Set<Int> {
            val result = sortedSetOf<Int>()
            for (part in text.split(',')) {
                require(part.isNotEmpty()) { "Empty value in $label" }
                val (rangePart, stepText) = part.split('/', limit = 2).let { it[0] to it.getOrNull(1) }
                val step = stepText?.let { it.toIntOrNull()?.takeIf { s -> s > 0 } ?: throw IllegalArgumentException("Bad step in $label") } ?: 1
                val (from, to) = when {
                    rangePart == "*" -> min to max
                    '-' in rangePart -> {
                        val (a, b) = rangePart.split('-', limit = 2)
                        value(a, label, names, nameBase) to value(b, label, names, nameBase)
                    }
                    else -> {
                        val v = value(rangePart, label, names, nameBase)
                        v to (if (stepText != null) max else v)   // "5/10" means 5 up to max, every 10
                    }
                }
                require(from in min..max && to in min..max) { "$label must be between $min and $max" }
                require(from <= to) { "Backwards range in $label" }
                var v = from
                while (v <= to) { result += v; v += step }
            }
            return result
        }

        private fun value(token: String, label: String, names: List<String>?, nameBase: Int): Int {
            token.toIntOrNull()?.let { return it }
            val idx = names?.indexOf(token.uppercase()) ?: -1
            require(idx >= 0) { "Unknown value '$token' in $label" }
            return idx + nameBase
        }
    }
}

/** Which network the backup may use. */
enum class NetworkPolicy {
    /** Unmetered networks only (normally Wi-Fi). */
    WIFI_ONLY,

    /** Metered networks only (mobile data). */
    MOBILE_ONLY,

    /** Any connected network. */
    ANY,
}

/** Ready-made schedules shown as quick choices; the user can still type any cron expression. */
object SchedulePresets {
    data class Preset(val label: String, val cron: String)

    val ALL = listOf(
        Preset("Every hour", "0 * * * *"),
        Preset("Every 6 hours", "0 */6 * * *"),
        Preset("Daily at 2:00 AM", "0 2 * * *"),
        Preset("Weekly (Sunday 2:00 AM)", "0 2 * * 0"),
        Preset("Monthly (1st, 2:00 AM)", "0 2 1 * *"),
    )

    /** Smallest allowed gap between runs: Android cannot honour more frequent background work reliably. */
    const val MIN_GAP_MINUTES = 15
}
