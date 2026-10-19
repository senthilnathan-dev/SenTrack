package `in`.senthilnathan_dev.sentrack.feature.personal

/** One line of a note, understood. [line] is its position in the source, so a check box can be toggled in place. */
internal sealed interface Block {
    val line: Int

    data class Heading(override val line: Int, val level: Int, val text: String) : Block
    data class Bullet(override val line: Int, val text: String) : Block
    data class Numbered(override val line: Int, val number: Int, val text: String) : Block
    data class Check(override val line: Int, val checked: Boolean, val text: String) : Block
    data class Quote(override val line: Int, val text: String) : Block
    data class Rule(override val line: Int) : Block
    data class Paragraph(override val line: Int, val text: String) : Block
    data class Blank(override val line: Int) : Block
}

/** A piece of text with its inline style. */
internal data class Span(val text: String, val bold: Boolean = false, val italic: Boolean = false, val strike: Boolean = false, val code: Boolean = false)

/**
 * The note format: a small, readable Markdown subset, so notes stay plain text under the hood (portable, searchable,
 * safe to back up) while the editor toolbar and the reading view make them look formatted.
 *
 *     # Heading   ## Smaller heading   ### Smallest
 *     **bold**   *italic*   ~~strikethrough~~   `code`
 *     - bullet     1. numbered     - [ ] to do     - [x] done     > quote     ---
 */
internal object NoteFormat {
    private val numbered = Regex("""^(\d{1,3})\. (.*)$""")

    /** Reads [markdown] line by line. */
    fun parse(markdown: String): List<Block> = markdown.split('\n').mapIndexed { i, raw ->
        val line = raw.trimEnd('\r')
        when {
            line.isBlank() -> Block.Blank(i)
            line.trim() == "---" -> Block.Rule(i)
            line.startsWith("### ") -> Block.Heading(i, 3, line.drop(4))
            line.startsWith("## ") -> Block.Heading(i, 2, line.drop(3))
            line.startsWith("# ") -> Block.Heading(i, 1, line.drop(2))
            line.startsWith("- [ ] ") -> Block.Check(i, false, line.drop(6))
            line.startsWith("- [x] ") || line.startsWith("- [X] ") -> Block.Check(i, true, line.drop(6))
            line.startsWith("- ") -> Block.Bullet(i, line.drop(2))
            line.startsWith("> ") -> Block.Quote(i, line.drop(2))
            else -> numbered.matchEntire(line)?.let { Block.Numbered(i, it.groupValues[1].toInt(), it.groupValues[2]) }
                ?: Block.Paragraph(i, line)
        }
    }

    /**
     * Splits one line into styled pieces. A marker only counts when it has a partner later in the line; otherwise
     * it is shown as typed. A backslash makes the next character literal.
     */
    fun parseInline(text: String): List<Span> {
        val out = mutableListOf<Span>()
        val buffer = StringBuilder()
        var bold = false
        var italic = false
        var strike = false
        var code = false

        fun flush() {
            if (buffer.isNotEmpty()) {
                out += Span(buffer.toString(), bold, italic, strike, code)
                buffer.clear()
            }
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length -> { buffer.append(text[i + 1]); i += 2 }
                c == '`' && (code || text.indexOf('`', i + 1) > 0) -> { flush(); code = !code; i++ }
                code -> { buffer.append(c); i++ }
                text.startsWith("**", i) -> {
                    if (bold || text.indexOf("**", i + 2) > 0) { flush(); bold = !bold } else buffer.append("**")
                    i += 2
                }
                text.startsWith("~~", i) -> {
                    if (strike || text.indexOf("~~", i + 2) > 0) { flush(); strike = !strike } else buffer.append("~~")
                    i += 2
                }
                c == '*' && (italic || (text.getOrNull(i + 1)?.isWhitespace() == false && text.indexOf('*', i + 1) > 0)) -> {
                    flush(); italic = !italic; i++
                }
                else -> { buffer.append(c); i++ }
            }
        }
        flush()
        return out
    }

    /** Flips the check box on source line [line] (`- [ ]` <-> `- [x]`). Other lines are left alone. */
    fun toggleCheck(markdown: String, line: Int): String {
        val lines = markdown.split('\n').toMutableList()
        val current = lines.getOrNull(line) ?: return markdown
        lines[line] = when {
            current.startsWith("- [ ] ") -> "- [x] " + current.drop(6)
            current.startsWith("- [x] ") || current.startsWith("- [X] ") -> "- [ ] " + current.drop(6)
            else -> current
        }
        return lines.joinToString("\n")
    }

    /** The text without formatting marks, for search and list previews. */
    fun plainText(markdown: String): String = parse(markdown).joinToString("\n") { block ->
        when (block) {
            is Block.Heading -> block.text
            is Block.Bullet -> block.text
            is Block.Numbered -> block.text
            is Block.Check -> block.text
            is Block.Quote -> block.text
            is Block.Paragraph -> block.text
            is Block.Rule, is Block.Blank -> ""
        }.let { parseInline(it).joinToString("") { s -> s.text } }
    }

    /** A short single-line preview. */
    fun snippet(markdown: String, maxChars: Int = 120): String {
        val flat = plainText(markdown).lines().filter { it.isNotBlank() }.joinToString("  ")
        return if (flat.length <= maxChars) flat else flat.take(maxChars).trimEnd() + "…"
    }
}

/** Editor actions on the text and the selection. Pure functions, so the toolbar behaviour is unit-tested. */
internal object NoteEditing {
    data class Edit(val text: String, val selStart: Int, val selEnd: Int)

    /**
     * Wraps the selection in [marker] (bold, italic...), or removes the marker if the selection is already wrapped.
     * With nothing selected, inserts an empty pair and puts the cursor between.
     */
    fun wrap(edit: Edit, marker: String): Edit {
        val (text, s, e) = Triple(edit.text, minOf(edit.selStart, edit.selEnd), maxOf(edit.selStart, edit.selEnd))
        if (s == e) {
            return Edit(text.substring(0, s) + marker + marker + text.substring(e), s + marker.length, s + marker.length)
        }
        val wrapped = s >= marker.length && text.startsWith(marker, s - marker.length) && text.startsWith(marker, e)
        return if (wrapped) {
            Edit(
                text.substring(0, s - marker.length) + text.substring(s, e) + text.substring(e + marker.length),
                s - marker.length, e - marker.length,
            )
        } else {
            Edit(text.substring(0, s) + marker + text.substring(s, e) + marker + text.substring(e), s + marker.length, e + marker.length)
        }
    }

    private val blockPrefix = Regex("""^(#{1,3} |- \[[ xX]\] |- |> |\d{1,3}\. )""")

    /**
     * Adds [prefix] ("# ", "- ", "- [ ] ", "1. ", "> ") to every line the selection touches, replacing any other
     * block prefix; if all those lines already have it, removes it instead.
     */
    fun toggleLinePrefix(edit: Edit, prefix: String): Edit {
        val s = minOf(edit.selStart, edit.selEnd)
        val e = maxOf(edit.selStart, edit.selEnd)
        val text = edit.text
        val start = text.lastIndexOf('\n', s - 1) + 1
        val endIdx = text.indexOf('\n', e).let { if (it < 0) text.length else it }
        val lines = text.substring(start, endIdx).split('\n')
        val allHave = lines.all { it.startsWith(prefix) }
        val changed = lines.map { line ->
            if (allHave) line.removePrefix(prefix) else prefix + line.replaceFirst(blockPrefix, "")
        }.joinToString("\n")
        val newText = text.substring(0, start) + changed + text.substring(endIdx)
        return Edit(newText, start, start + changed.length)
    }
}
