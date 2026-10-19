package `in`.senthilnathan_dev.sentrack.feature.personal

/**
 * Undo and redo for the note text. Typing in quick succession is merged into one step (so one Undo removes the
 * sentence you just typed, not a single letter); toolbar actions such as Bold always form their own step.
 */
internal class EditHistory(initialText: String, private val maxSteps: Int = 200) {
    data class Snapshot(val text: String, val selStart: Int, val selEnd: Int)

    private val undoStack = ArrayDeque<Snapshot>()   // states to go back to
    private val redoStack = ArrayDeque<Snapshot>()   // states undone, to go forward to
    private var current = Snapshot(initialText, initialText.length, initialText.length)
    private var lastChangeAt = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Tells the history about the text now in the box. A change in text starts a new undo step unless it follows
     * the previous change within [MERGE_WINDOW_MS] (continuous typing) and [forceNewStep] is false.
     * A selection-only change is remembered but never becomes a step.
     */
    fun record(snapshot: Snapshot, nowMillis: Long, forceNewStep: Boolean = false) {
        if (snapshot.text == current.text) {
            current = snapshot
            return
        }
        val continuesTyping = !forceNewStep && undoStack.isNotEmpty() && nowMillis - lastChangeAt < MERGE_WINDOW_MS
        if (!continuesTyping) {
            undoStack.addLast(current)
            if (undoStack.size > maxSteps) undoStack.removeFirst()
        }
        current = snapshot
        lastChangeAt = nowMillis
        redoStack.clear()
    }

    /** The state before the latest step, or null if there is nothing to undo. */
    fun undo(): Snapshot? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        current = previous
        lastChangeAt = 0L
        return previous
    }

    fun redo(): Snapshot? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        current = next
        lastChangeAt = 0L
        return next
    }

    companion object {
        const val MERGE_WINDOW_MS = 800L
    }
}

/** Small typing helpers: keep a list going on Enter, and finish words from the user's own notes. */
internal object EditAssist {
    private val checkItem = Regex("""^- \[[ xX]\] (.*)$""")
    private val bulletItem = Regex("""^- (.*)$""")
    private val numberedItem = Regex("""^(\d{1,3})\. (.*)$""")
    private val quoteItem = Regex("""^> (.*)$""")

    /**
     * Called after the user pressed Enter. If the previous line was a list item, starts the next item with the same
     * kind of marker (numbers count up); if that item was empty, ends the list instead by clearing the marker.
     * Returns the adjusted text and cursor, or null if nothing special should happen.
     */
    fun continueList(old: NoteEditing.Edit, new: NoteEditing.Edit): NoteEditing.Edit? {
        val at = new.selStart - 1
        if (new.selStart != new.selEnd || at < 0 || new.text.getOrNull(at) != '\n') return null
        // Only a plain newline typed at the old cursor counts (not a paste or other change).
        if (old.selStart != old.selEnd || old.selStart != at) return null
        if (old.text.substring(0, at) + "\n" + old.text.substring(at) != new.text) return null

        val lineStart = old.text.lastIndexOf('\n', at - 1) + 1
        val line = old.text.substring(lineStart, at)
        val (marker, nextMarker, body) = when {
            checkItem.matches(line) -> Triple("- [" + line[3] + "] ", "- [ ] ", checkItem.matchEntire(line)!!.groupValues[1])
            bulletItem.matches(line) -> Triple("- ", "- ", bulletItem.matchEntire(line)!!.groupValues[1])
            numberedItem.matches(line) -> {
                val m = numberedItem.matchEntire(line)!!
                Triple("${m.groupValues[1]}. ", "${m.groupValues[1].toInt() + 1}. ", m.groupValues[2])
            }
            quoteItem.matches(line) -> Triple("> ", "> ", quoteItem.matchEntire(line)!!.groupValues[1])
            else -> return null
        }
        if (body.isBlank()) {
            // An empty item: Enter ends the list. Remove the marker and the newline that was just typed.
            val text = old.text.substring(0, lineStart) + old.text.substring(at)
            return NoteEditing.Edit(text, lineStart, lineStart)
        }
        val text = new.text.substring(0, new.selStart) + nextMarker + new.text.substring(new.selStart)
        val cursor = new.selStart + nextMarker.length
        return NoteEditing.Edit(text, cursor, cursor)
    }

    private val word = Regex("""\p{L}[\p{L}'’-]{2,}""")

    /** How often each word (3+ letters, lower-cased) appears across [texts]. */
    fun vocabulary(texts: List<String>): Map<String, Int> {
        val counts = HashMap<String, Int>()
        for (t in texts) for (m in word.findAll(t)) counts.merge(m.value.lowercase(), 1, Int::plus)
        return counts
    }

    /** The letters of the word being typed, just before [cursor]. */
    fun prefixAt(text: String, cursor: Int): String {
        val end = cursor.coerceIn(0, text.length)
        var start = end
        while (start > 0 && (text[start - 1].isLetter() || text[start - 1] == '\'' || text[start - 1] == '’')) start--
        return text.substring(start, end)
    }

    /** Up to [limit] known words that continue [prefix] (at least 2 letters typed), most used first. */
    fun suggest(vocabulary: Map<String, Int>, prefix: String, limit: Int = 3): List<String> {
        if (prefix.length < 2) return emptyList()
        val p = prefix.lowercase()
        return vocabulary.entries
            .filter { it.key.startsWith(p) && it.key != p }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { entry -> if (prefix.first().isUpperCase()) entry.key.replaceFirstChar { it.uppercase() } else entry.key }
    }

    /** Replaces the word being typed with [suggestion] and a space. */
    fun complete(edit: NoteEditing.Edit, suggestion: String): NoteEditing.Edit {
        val cursor = edit.selEnd
        val prefix = prefixAt(edit.text, cursor)
        val start = cursor - prefix.length
        val text = edit.text.substring(0, start) + suggestion + " " + edit.text.substring(cursor)
        val newCursor = start + suggestion.length + 1
        return NoteEditing.Edit(text, newCursor, newCursor)
    }
}
