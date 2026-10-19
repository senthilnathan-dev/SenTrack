package `in`.senthilnathan_dev.sentrack.feature.finance

import java.math.BigDecimal

/**
 * Rupee formatting and parsing. Amounts are always `Long` paise; this is the only place that converts to and from
 * text. Formatting uses the Indian digit grouping (₹12,34,567.50).
 */
object Money {
    private const val SYMBOL = "₹"
    private val ACCEPTED = Regex("""^\d{1,13}(\.\d{1,2})?$""")

    /** "₹1,23,456" or "₹1,234.50": paise are shown only when non-zero. */
    fun format(paise: Long, withSymbol: Boolean = true): String {
        val negative = paise < 0
        val abs = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(paise)
        val rupees = abs / 100
        val rest = (abs % 100).toInt()
        val grouped = groupIndian(rupees.toString())
        val body = if (rest == 0) grouped else "%s.%02d".format(grouped, rest)
        return (if (negative) "-" else "") + (if (withSymbol) SYMBOL else "") + body
    }

    /** Like [format] but with an explicit "+" for positive amounts (for gains). */
    fun formatSigned(paise: Long): String = (if (paise > 0) "+" else "") + format(paise)

    /**
     * Parses what a person types ("1500", "1,500.50", "₹ 1,500"). Returns null for anything that is not a plain
     * non-negative amount with at most two decimals.
     */
    fun parse(text: String): Long? {
        val clean = text.replace(",", "").replace(SYMBOL, "").replace(" ", "")
        if (!ACCEPTED.matches(clean)) return null
        return BigDecimal(clean).movePointRight(2).toLong()
    }

    /** Plain number for an input box, without symbol or grouping ("1500" or "1500.5"). */
    fun toInput(paise: Long): String {
        if (paise == 0L) return ""
        val rupees = paise / 100
        val rest = (paise % 100).toInt()
        return when {
            rest == 0 -> rupees.toString()
            rest % 10 == 0 -> "%d.%d".format(rupees, rest / 10)
            else -> "%d.%02d".format(rupees, rest)
        }
    }

    private fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val head = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return "$head,$last3"
    }
}
