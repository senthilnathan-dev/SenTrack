package `in`.senthilnathan_dev.sentrack.feature.personal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditAssistTest {
    private fun snap(text: String) = EditHistory.Snapshot(text, text.length, text.length)
    private fun edit(text: String, s: Int, e: Int = s) = NoteEditing.Edit(text, s, e)

    // ---- history

    @Test
    fun typingInQuickSuccession_isOneUndoStep() {
        val h = EditHistory("")
        h.record(snap("h"), 0)
        h.record(snap("he"), 100)
        h.record(snap("hel"), 200)
        assertTrue(h.canUndo)
        assertEquals("", h.undo()!!.text)
        assertFalse(h.canUndo)
    }

    @Test
    fun aPause_startsANewStep() {
        val h = EditHistory("")
        h.record(snap("one"), 0)
        h.record(snap("one two"), 5_000)
        assertEquals("one", h.undo()!!.text)
        assertEquals("", h.undo()!!.text)
        assertNull(h.undo())
    }

    @Test
    fun forcedStep_neverMerges() {
        val h = EditHistory("")
        h.record(snap("word"), 0)
        h.record(snap("**word**"), 10, forceNewStep = true)
        assertEquals("word", h.undo()!!.text)
    }

    @Test
    fun redoGoesForward_andNewTypingClearsIt() {
        val h = EditHistory("")
        h.record(snap("a"), 0)
        h.record(snap("ab"), 5_000)
        h.undo()
        assertTrue(h.canRedo)
        assertEquals("ab", h.redo()!!.text)
        h.undo()
        h.record(snap("ax"), 20_000)
        assertFalse(h.canRedo)
    }

    @Test
    fun selectionOnlyChange_isNotAStep() {
        val h = EditHistory("abc")
        h.record(EditHistory.Snapshot("abc", 0, 1), 10)
        assertFalse(h.canUndo)
    }

    @Test
    fun historyIsCapped() {
        val h = EditHistory("", maxSteps = 3)
        for (i in 1..10) h.record(snap("x".repeat(i)), i * 10_000L)
        var steps = 0
        while (h.undo() != null) steps++
        assertEquals(3, steps)
    }

    // ---- list continuation

    private fun enter(text: String, at: Int): Pair<NoteEditing.Edit, NoteEditing.Edit> {
        val old = edit(text, at)
        val new = edit(text.substring(0, at) + "\n" + text.substring(at), at + 1)
        return old to new
    }

    @Test
    fun enterOnBullet_startsNextBullet() {
        val (old, new) = enter("- milk", 6)
        val r = EditAssist.continueList(old, new)!!
        assertEquals("- milk\n- ", r.text)
        assertEquals(9, r.selStart)
    }

    @Test
    fun enterOnCheckItem_startsUncheckedItem() {
        val (old, new) = enter("- [x] done", 10)
        assertEquals("- [x] done\n- [ ] ", EditAssist.continueList(old, new)!!.text)
    }

    @Test
    fun enterOnNumbered_countsUp() {
        val (old, new) = enter("1. first\n2. second", 18)
        assertEquals("1. first\n2. second\n3. ", EditAssist.continueList(old, new)!!.text)
    }

    @Test
    fun enterOnQuote_continuesQuote() {
        val (old, new) = enter("> wise words", 12)
        assertEquals("> wise words\n> ", EditAssist.continueList(old, new)!!.text)
    }

    @Test
    fun enterOnEmptyItem_endsTheList() {
        val (old, new) = enter("- one\n- ", 8)
        val r = EditAssist.continueList(old, new)!!
        assertEquals("- one\n", r.text)
        assertEquals(6, r.selStart)
    }

    @Test
    fun enterMidLine_carriesTheRestToTheNewItem() {
        val (old, new) = enter("- buy eggs", 5)
        assertEquals("- buy\n-  eggs", EditAssist.continueList(old, new)!!.text)
    }

    @Test
    fun enterOnPlainText_orOtherChanges_doNothing() {
        val (old, new) = enter("just text", 9)
        assertNull(EditAssist.continueList(old, new))
        assertNull(EditAssist.continueList(edit("- a", 3), edit("- ab", 4)))                 // ordinary typing
        assertNull(EditAssist.continueList(edit("- a", 3), edit("- a\n- b\n", 8)))           // a paste
    }

    // ---- word suggestions

    @Test
    fun vocabulary_countsWordsCaseInsensitively() {
        val v = EditAssist.vocabulary(listOf("Budget budget BUDGET plan", "of to a"))
        assertEquals(3, v["budget"])
        assertEquals(1, v["plan"])
        assertNull(v["of"])        // too short
    }

    @Test
    fun prefixAt_findsTheWordBeingTyped() {
        assertEquals("bud", EditAssist.prefixAt("my bud", 6))
        assertEquals("", EditAssist.prefixAt("my ", 3))
        assertEquals("pl", EditAssist.prefixAt("a plan", 4))
    }

    @Test
    fun suggest_ranksByUseAndKeepsCapitalisation() {
        val v = mapOf("budget" to 5, "budgie" to 1, "bud" to 9, "plan" to 2)
        assertEquals(listOf("budget", "budgie"), EditAssist.suggest(v, "bud"))   // "bud" itself excluded
        assertEquals(listOf("Budget", "Budgie"), EditAssist.suggest(v, "Bud"))
        assertEquals(emptyList<String>(), EditAssist.suggest(v, "b"))            // too short
        assertEquals(emptyList<String>(), EditAssist.suggest(v, "zz"))
    }

    @Test
    fun complete_replacesThePrefixAndAddsASpace() {
        val r = EditAssist.complete(edit("my budg here", 7), "budget")
        assertEquals("my budget  here", r.text)
        assertEquals(10, r.selStart)
    }
}
