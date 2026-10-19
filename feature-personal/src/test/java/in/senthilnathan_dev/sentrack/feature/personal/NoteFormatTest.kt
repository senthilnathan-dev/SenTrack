package `in`.senthilnathan_dev.sentrack.feature.personal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFormatTest {
    @Test
    fun parse_recognisesEachLineKind() {
        val blocks = NoteFormat.parse(
            "# Title\n## Sub\n### Small\nplain\n- bullet\n1. first\n- [ ] todo\n- [x] done\n> quote\n---\n\nend",
        )
        assertEquals(Block.Heading(0, 1, "Title"), blocks[0])
        assertEquals(Block.Heading(1, 2, "Sub"), blocks[1])
        assertEquals(Block.Heading(2, 3, "Small"), blocks[2])
        assertEquals(Block.Paragraph(3, "plain"), blocks[3])
        assertEquals(Block.Bullet(4, "bullet"), blocks[4])
        assertEquals(Block.Numbered(5, 1, "first"), blocks[5])
        assertEquals(Block.Check(6, false, "todo"), blocks[6])
        assertEquals(Block.Check(7, true, "done"), blocks[7])
        assertEquals(Block.Quote(8, "quote"), blocks[8])
        assertEquals(Block.Rule(9), blocks[9])
        assertEquals(Block.Blank(10), blocks[10])
        assertEquals(Block.Paragraph(11, "end"), blocks[11])
    }

    @Test
    fun inline_boldItalicStrikeCode() {
        val spans = NoteFormat.parseInline("a **b** *c* ~~d~~ `e` f")
        assertEquals(
            listOf(
                Span("a "), Span("b", bold = true), Span(" "), Span("c", italic = true), Span(" "),
                Span("d", strike = true), Span(" "), Span("e", code = true), Span(" f"),
            ),
            spans,
        )
    }

    @Test
    fun inline_unmatchedMarkersStayLiteral() {
        assertEquals(listOf(Span("2 * 3 = 6")), NoteFormat.parseInline("2 * 3 = 6"))
        assertEquals(listOf(Span("**not closed")), NoteFormat.parseInline("**not closed"))
        assertEquals(listOf(Span("a`b")), NoteFormat.parseInline("a`b"))
    }

    @Test
    fun inline_boldAndItalicNest_andCodeIgnoresMarkers() {
        assertEquals(listOf(Span("x", bold = true, italic = true)), NoteFormat.parseInline("***x***"))
        assertEquals(listOf(Span("**no**", code = true)), NoteFormat.parseInline("`**no**`"))
    }

    @Test
    fun inline_backslashEscapes() {
        assertEquals(listOf(Span("*star*")), NoteFormat.parseInline("\\*star\\*"))
    }

    @Test
    fun toggleCheck_flipsOnlyThatLine() {
        val src = "- [ ] a\n- [x] b\ntext"
        assertEquals("- [x] a\n- [x] b\ntext", NoteFormat.toggleCheck(src, 0))
        assertEquals("- [ ] a\n- [ ] b\ntext", NoteFormat.toggleCheck(src, 1))
        assertEquals(src, NoteFormat.toggleCheck(src, 2))
        assertEquals(src, NoteFormat.toggleCheck(src, 9))
    }

    @Test
    fun plainText_andSnippet_stripFormatting() {
        val src = "# Trip plan\n- book **flights**\n- [ ] pack *light*"
        assertEquals("Trip plan\nbook flights\npack light", NoteFormat.plainText(src))
        assertEquals("Trip plan  book flights  pack light", NoteFormat.snippet(src))
        assertTrue(NoteFormat.snippet("x".repeat(300), 50).endsWith("…"))
    }

    // ---- editing

    private fun edit(text: String, s: Int, e: Int = s) = NoteEditing.Edit(text, s, e)

    @Test
    fun wrap_selection_thenUnwrap() {
        val bold = NoteEditing.wrap(edit("hello world", 6, 11), "**")
        assertEquals("hello **world**", bold.text)
        assertEquals(8, bold.selStart); assertEquals(13, bold.selEnd)
        val back = NoteEditing.wrap(bold, "**")
        assertEquals("hello world", back.text)
        assertEquals(6, back.selStart); assertEquals(11, back.selEnd)
    }

    @Test
    fun wrap_withoutSelection_insertsPairAndPlacesCursorBetween() {
        val r = NoteEditing.wrap(edit("ab", 1), "*")
        assertEquals("a**b", r.text)
        assertEquals(2, r.selStart); assertEquals(2, r.selEnd)
    }

    @Test
    fun linePrefix_addsToEveryTouchedLine_andRemovesWhenAllHaveIt() {
        val added = NoteEditing.toggleLinePrefix(edit("one\ntwo\nthree", 2, 6), "- ")
        assertEquals("- one\n- two\nthree", added.text)
        val removed = NoteEditing.toggleLinePrefix(NoteEditing.Edit(added.text, added.selStart, added.selEnd), "- ")
        assertEquals("one\ntwo\nthree", removed.text)
    }

    @Test
    fun linePrefix_replacesAnotherBlockPrefix() {
        assertEquals("# title", NoteEditing.toggleLinePrefix(edit("- title", 3), "# ").text)
        assertEquals("- [ ] task", NoteEditing.toggleLinePrefix(edit("1. task", 0), "- [ ] ").text)
        assertEquals("## h", NoteEditing.toggleLinePrefix(edit("# h", 0), "## ").text)
    }

    // ---- live highlighting

    @Test
    fun highlights_findMarksAndStyledStretches() {
        val h = NoteFormat.highlights("a **b** c")
        assertTrue(h.contains(Highlight(2, 4, HighlightKind.MARKER)))
        assertTrue(h.contains(Highlight(5, 7, HighlightKind.MARKER)))
        assertTrue(h.contains(Highlight(4, 5, HighlightKind.BOLD)))
    }

    @Test
    fun highlights_italicStrikeCode_andIgnoreUnmatched() {
        val h = NoteFormat.highlights("*i* ~~s~~ `c`")
        assertTrue(h.contains(Highlight(1, 2, HighlightKind.ITALIC)))
        assertTrue(h.contains(Highlight(6, 7, HighlightKind.STRIKE)))
        assertTrue(h.contains(Highlight(11, 12, HighlightKind.CODE)))
        assertEquals(emptyList<Highlight>(), NoteFormat.highlights("2 * 3 and **open"))
    }
}
