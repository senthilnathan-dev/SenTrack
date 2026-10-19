package `in`.senthilnathan_dev.sentrack.core.ai.local

/** What one benchmark run measured. Times in milliseconds. */
data class BenchmarkResult(
    val loadMillis: Long,
    val firstTokenMillis: Long,
    val generatedChars: Int,
    val generateMillis: Long,
    val grammarOutput: String,
    val summaryOutput: String,
) {
    /** Rough speed: about four characters per token for English text. */
    val tokensPerSecond: Double
        get() = if (generateMillis <= 0) 0.0 else (generatedChars / 4.0) / (generateMillis / 1000.0)
}

enum class Verdict(val label: String, val advice: String) {
    GOOD("Good", "Fast enough to use comfortably."),
    USABLE("Usable", "Works, but expect to wait a little for each answer."),
    TOO_SLOW("Too slow", "Not worth using on this phone. Try the smaller model, or use a cloud provider instead."),
    POOR_QUALITY("Poor answers", "It runs, but failed the basic accuracy checks. Do not rely on it."),
}

/**
 * The fixed prompts and the pass marks. The thresholds are a first estimate meant to be tuned on real phones: they
 * are deliberately in one place.
 */
object BenchmarkScoring {
    const val GRAMMAR_INPUT = "i has went to the market yesterday and buyed two apple."
    const val SUMMARY_INPUT = "On 12 March Ravi paid ₹4,250 for the electricity bill of the flat in Chennai. " +
        "The bill was higher than February because the air conditioner ran every day. He plans to service it before April."

    val GRAMMAR_PROMPT = "Fix the spelling and grammar. Reply with only the corrected sentence.\n\n$GRAMMAR_INPUT"
    val SUMMARY_PROMPT = "Summarize in one short sentence. Keep numbers exact.\n\n$SUMMARY_INPUT"

    const val GOOD_TOKENS_PER_SECOND = 8.0
    const val USABLE_TOKENS_PER_SECOND = 3.0
    const val GOOD_FIRST_TOKEN_MILLIS = 4_000L

    /** The corrected sentence keeps the facts and no longer contains the original mistakes. */
    fun grammarPasses(output: String): Boolean {
        val t = output.lowercase()
        return "market" in t && "yesterday" in t && "buyed" !in t && "i has" !in t && ("went" in t || "gone" in t)
    }

    /** The summary keeps the amount exactly. */
    fun summaryPasses(output: String): Boolean {
        val t = output.replace(" ", "")
        return "4,250" in t || "4250" in t
    }

    fun verdict(r: BenchmarkResult): Verdict {
        if (!grammarPasses(r.grammarOutput) && !summaryPasses(r.summaryOutput)) return Verdict.POOR_QUALITY
        val speed = r.tokensPerSecond
        return when {
            speed < USABLE_TOKENS_PER_SECOND -> Verdict.TOO_SLOW
            speed >= GOOD_TOKENS_PER_SECOND && r.firstTokenMillis <= GOOD_FIRST_TOKEN_MILLIS -> Verdict.GOOD
            else -> Verdict.USABLE
        }
    }

    /** A short human summary of a result, for the screen. */
    fun describe(r: BenchmarkResult): List<String> = listOf(
        "Loaded in %.1f s".format(java.util.Locale.ENGLISH, r.loadMillis / 1000.0),
        "First word after %.1f s".format(java.util.Locale.ENGLISH, r.firstTokenMillis / 1000.0),
        "About %.1f words per second".format(java.util.Locale.ENGLISH, r.tokensPerSecond * 0.75),
        "Grammar check: " + if (grammarPasses(r.grammarOutput)) "passed" else "failed",
        "Number kept in summary: " + if (summaryPasses(r.summaryOutput)) "passed" else "failed",
    )
}
