package `in`.senthilnathan_dev.sentrack.feature.personal

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters

/** How often a habit is measured. */
internal enum class HabitPeriod(val label: String, val noun: String, val thisPeriod: String) {
    DAY("Daily", "day", "today"),
    WEEK("Weekly", "week", "this week"),
    MONTH("Monthly", "month", "this month"),
    QUARTER("Quarterly", "quarter", "this quarter"),
    YEAR("Yearly", "year", "this year"),
}

/**
 * What it takes to meet a habit: [target] (times, or an amount for number habits) within each [period].
 * [scheduledDays] limits daily habits to certain weekdays (empty = every day). [start] is when tracking began.
 */
internal data class HabitRule(
    val period: HabitPeriod,
    val target: Double,
    val scheduledDays: Set<DayOfWeek>,
    val start: LocalDate,
)

/** Pure habit maths: periods, progress, streaks and completion rates. Unit-tested without a phone. */
internal object HabitEngine {
    private const val SAFETY_LIMIT = 20_000

    fun periodStart(period: HabitPeriod, date: LocalDate): LocalDate = when (period) {
        HabitPeriod.DAY -> date
        HabitPeriod.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        HabitPeriod.MONTH -> date.withDayOfMonth(1)
        HabitPeriod.QUARTER -> LocalDate.of(date.year, Month.of((date.monthValue - 1) / 3 * 3 + 1), 1)
        HabitPeriod.YEAR -> date.withDayOfYear(1)
    }

    fun nextPeriodStart(period: HabitPeriod, start: LocalDate): LocalDate = when (period) {
        HabitPeriod.DAY -> start.plusDays(1)
        HabitPeriod.WEEK -> start.plusWeeks(1)
        HabitPeriod.MONTH -> start.plusMonths(1)
        HabitPeriod.QUARTER -> start.plusMonths(3)
        HabitPeriod.YEAR -> start.plusYears(1)
    }

    private fun previousPeriodStart(period: HabitPeriod, start: LocalDate): LocalDate = when (period) {
        HabitPeriod.DAY -> start.minusDays(1)
        HabitPeriod.WEEK -> start.minusWeeks(1)
        HabitPeriod.MONTH -> start.minusMonths(1)
        HabitPeriod.QUARTER -> start.minusMonths(3)
        HabitPeriod.YEAR -> start.minusYears(1)
    }

    /** Whether a daily habit applies on [date]. Habits with longer periods apply on every day. */
    fun isScheduled(rule: HabitRule, date: LocalDate): Boolean =
        rule.period != HabitPeriod.DAY || rule.scheduledDays.isEmpty() || date.dayOfWeek in rule.scheduledDays

    /** Total done within the period that starts at [periodStart]. */
    fun progress(rule: HabitRule, logs: Map<LocalDate, Double>, periodStart: LocalDate): Double {
        val end = nextPeriodStart(rule.period, periodStart)
        var total = 0.0
        var d = periodStart
        while (d.isBefore(end)) {
            total += logs[d] ?: 0.0
            d = d.plusDays(1)
        }
        return total
    }

    fun isMet(rule: HabitRule, logs: Map<LocalDate, Double>, periodStart: LocalDate): Boolean =
        progress(rule, logs, periodStart) >= rule.target - 1e-9

    /** Period starts from the one containing [today] back to the habit's first period (daily: only scheduled days). */
    private fun periodsBackwards(rule: HabitRule, today: LocalDate): List<LocalDate> {
        val first = periodStart(rule.period, rule.start)
        val out = ArrayList<LocalDate>()
        var p = periodStart(rule.period, today)
        var guard = 0
        while (!p.isBefore(first) && guard++ < SAFETY_LIMIT) {
            if (isScheduled(rule, p)) out += p
            p = previousPeriodStart(rule.period, p)
        }
        return out
    }

    /**
     * Consecutive periods met, counting back from now. The period still in progress counts if it is already met and
     * otherwise does not break the streak.
     */
    fun currentStreak(rule: HabitRule, logs: Map<LocalDate, Double>, today: LocalDate): Int {
        val currentStart = periodStart(rule.period, today)
        var streak = 0
        for (p in periodsBackwards(rule, today)) {
            val met = isMet(rule, logs, p)
            when {
                met -> streak++
                p == currentStart -> Unit   // still in progress
                else -> return streak
            }
        }
        return streak
    }

    /** The longest run of consecutive periods met, ever. */
    fun bestStreak(rule: HabitRule, logs: Map<LocalDate, Double>, today: LocalDate): Int {
        val currentStart = periodStart(rule.period, today)
        var best = 0
        var run = 0
        for (p in periodsBackwards(rule, today).asReversed()) {
            val met = isMet(rule, logs, p)
            when {
                met -> { run++; if (run > best) best = run }
                p == currentStart -> Unit
                else -> run = 0
            }
        }
        return best
    }

    /** Share of the last [lastN] finished periods that were met (0..1), or null if none have finished yet. */
    fun completionRate(rule: HabitRule, logs: Map<LocalDate, Double>, today: LocalDate, lastN: Int): Double? {
        val currentStart = periodStart(rule.period, today)
        val finished = periodsBackwards(rule, today).filter { it != currentStart }.take(lastN)
        if (finished.isEmpty()) return null
        return finished.count { isMet(rule, logs, it) }.toDouble() / finished.size
    }

    /** How many periods were met in total. */
    fun periodsMet(rule: HabitRule, logs: Map<LocalDate, Double>, today: LocalDate): Int =
        periodsBackwards(rule, today).count { isMet(rule, logs, it) }

    /** "2 of 3 this week", "5 of 8 glasses today". */
    fun progressText(rule: HabitRule, unit: String?, progress: Double): String {
        fun n(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(java.util.Locale.ENGLISH, v)
        val what = unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
        return "${n(progress)} of ${n(rule.target)}$what ${rule.period.thisPeriod}"
    }
}
