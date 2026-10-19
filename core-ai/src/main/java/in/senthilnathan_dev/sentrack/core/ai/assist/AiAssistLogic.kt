package `in`.senthilnathan_dev.sentrack.core.ai.assist

import `in`.senthilnathan_dev.sentrack.core.ai.local.GenParams

/**
 * The three answer styles. They trade time for care by changing how the model samples words, how long it may talk,
 * and whether it gets a second pass to check its own work.
 */
enum class Style(val id: String, val label: String, val blurb: String, val params: GenParams, val selfCheck: Boolean) {
    FAST(
        "fast", "Fast", "Short answers, quickest.",
        GenParams(temperature = 0.3, topK = 20, topP = 0.9, maxChars = 450, system = "Be brief. Use as few words as possible."),
        selfCheck = false,
    ),
    NORMAL(
        "normal", "Normal", "A good balance.",
        GenParams(temperature = 0.5, topK = 40, topP = 0.95, maxChars = 1_400, system = ""),
        selfCheck = false,
    ),
    ACCURATE(
        "accurate", "Accurate", "Most careful. Checks its own answer, so it takes about twice as long.",
        GenParams(temperature = 0.1, topK = 10, topP = 0.85, maxChars = 2_600, system = "Be careful and exact. Double-check every number, name and date against the text."),
        selfCheck = true,
    );

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

/** The prompt for one request, ready to send. */
data class Prompt(val system: String, val user: String)

/**
 * The wording behind each assist action. Keyed by a plain string id so the UI layer (which only knows the ids) and
 * this layer stay independent. Every prompt tells the model to stay inside the text it was given.
 */
object AiPrompts {
    private const val BASE =
        "You are a careful assistant inside a private personal tracker app. Reply in the same language as the user's text. " +
            "Never invent facts, names, numbers or dates that are not in the text. Reply with only the result and no introduction."

    private val instructions: Map<String, String> = mapOf(
        "fix" to "Fix spelling and grammar. Change nothing else. Return only the corrected text.",
        "rewrite" to "Rewrite the text to be clearer and better organised. Keep the meaning, the facts and the length about the same.",
        "shorten" to "Make the text shorter while keeping every important fact, number and date.",
        "summarize" to "Summarize the text in a few short sentences. Keep names, numbers and dates exact.",
        "action_items" to "List the action items in the text, one per line, each starting with '- '. If there are none, reply 'No action items.'",
        "title" to "Suggest one short title (at most 6 words) for the text. Return only the title.",
        "steps" to "Break the goal into 4 to 8 concrete steps, one per line, each starting with '- '. No numbering, no extra text.",
        "polish" to "Write this up as a clear, modest, professional description in 2 to 4 sentences. Use only the facts given.",
        "follow_up" to "Write a short, polite follow-up email about this job application (3 to 5 sentences). Use only the facts given. Leave the sender's name as [Your name].",
        "interview_prep" to "List 8 questions likely to be asked in an interview for this role, one per line, each starting with '- '.",
        "study_plan" to "Make a simple study plan of 4 to 8 steps for this topic, one per line, each starting with '- '.",
        "day_plan" to "Turn these tasks into a sensible plan for today: most urgent first, with one short line per task. Do not add tasks.",
        "explain_month" to "Explain these money facts in 3 to 5 plain sentences for the account holder. Use only the figures given, exactly as written. Point out the one thing most worth attention.",
        "category" to "Choose the single best category for this entry from the list given after 'Categories:'. Reply with only the category name exactly as written in the list.",
    )

    val ids: Set<String> get() = instructions.keys

    /** Uses where dropping a number from the text would be a mistake (as opposed to summaries, which drop on purpose). */
    val keepsAllNumbers: Set<String> = setOf("fix", "rewrite", "polish")

    fun build(id: String, text: String, extra: String, style: Style): Prompt {
        val instruction = instructions[id] ?: error("Unknown assist action: $id")
        val system = listOf(BASE, style.params.system).filter { it.isNotBlank() }.joinToString(" ")
        val user = buildString {
            append(instruction)
            if (extra.isNotBlank()) append("\n\n").append(extra.trim())
            append("\n\n---\n").append(text.trim())
        }
        return Prompt(system, user)
    }

    /** The second pass for [Style.ACCURATE]: the model checks its draft against the source. */
    fun verify(source: String, draft: String): String =
        "Check the draft against the source text. If the draft has any mistake, invented detail, or wrong number, " +
            "reply with a corrected version only. If it is correct, reply with the draft exactly as it is.\n\n" +
            "SOURCE:\n${source.trim()}\n\nDRAFT:\n${draft.trim()}"
}

/** Cheap, certain checks on what the model returned. They do not need the model, so they cannot be fooled by it. */
object NumberGuard {
    private val number = Regex("""\d[\d,]*(?:\.\d+)?""")
    private val listMarker = Regex("""(?m)^\s*(?:\d+[.)]|[-*•])\s+""")

    /** The numbers in [text] with commas removed, ignoring list numbering. */
    fun numbersIn(text: String): Set<String> =
        number.findAll(text.replace(listMarker, "")).map { it.value.replace(",", "").trimEnd('.') }.filter { it.isNotEmpty() }.toSet()

    /** Numbers the answer contains that the source never mentioned (small whole numbers are ignored). */
    fun invented(source: String, output: String): List<String> {
        val known = numbersIn(source)
        return numbersIn(output).filter { it !in known && !isSmall(it) }.sorted()
    }

    /** Numbers from the source that vanished from the answer (small whole numbers are ignored). */
    fun dropped(source: String, output: String): List<String> {
        val kept = numbersIn(output)
        return numbersIn(source).filter { it !in kept && !isSmall(it) }.sorted()
    }

    private fun isSmall(n: String) = n.toDoubleOrNull()?.let { it < 32 && '.' !in n } ?: false

    /** Plain-language warnings for the screen; empty when the answer looks consistent with the source. */
    fun warnings(id: String, source: String, output: String): List<String> = buildList {
        val extra = invented(source, output)
        if (extra.isNotEmpty()) add("The answer contains numbers that are not in your text: ${extra.joinToString(", ")}. Check them before using it.")
        if (id in AiPrompts.keepsAllNumbers) {
            val missing = dropped(source, output)
            if (missing.isNotEmpty()) add("These numbers from your text are missing in the answer: ${missing.joinToString(", ")}.")
        }
    }
}

/** Tidies a model's raw reply: removes any "thinking" block some models print, and stray wrapping quotes. */
object Cleanup {
    private val thinking = Regex("""(?s)<think>.*?</think>""")

    fun clean(raw: String): String {
        var t = raw.replace(thinking, "").trim()
        if (t.length >= 2 && ((t.first() == '"' && t.last() == '"') || (t.first() == '“' && t.last() == '”')) && t.count { it == '"' || it == '“' || it == '”' } == 2) {
            t = t.substring(1, t.length - 1).trim()
        }
        return t
    }
}
