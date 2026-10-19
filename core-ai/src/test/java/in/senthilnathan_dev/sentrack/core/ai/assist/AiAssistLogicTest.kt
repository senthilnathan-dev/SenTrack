package `in`.senthilnathan_dev.sentrack.core.ai.assist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistLogicTest {
    @Test
    fun styles_trade_care_for_speed() {
        assertTrue(Style.FAST.params.temperature > Style.ACCURATE.params.temperature)
        assertTrue(Style.FAST.params.maxChars < Style.NORMAL.params.maxChars)
        assertTrue(Style.NORMAL.params.maxChars < Style.ACCURATE.params.maxChars)
        assertTrue(Style.FAST.params.topK > Style.ACCURATE.params.topK)
        assertTrue(Style.ACCURATE.selfCheck)
        assertFalse(Style.FAST.selfCheck)
        assertEquals(Style.NORMAL, Style.of("unknown"))
        assertEquals(Style.ACCURATE, Style.of("accurate"))
    }

    @Test
    fun prompts_includeTextExtraAndStyle() {
        val p = AiPrompts.build("category", "Swiggy dinner", "Categories: Food, Travel", Style.ACCURATE)
        assertTrue(p.user.contains("Categories: Food, Travel"))
        assertTrue(p.user.endsWith("Swiggy dinner"))
        assertTrue(p.system.contains("Never invent"))
        assertTrue(p.system.contains("Double-check"))
        assertFalse(AiPrompts.build("fix", "x", "", Style.NORMAL).system.contains("Double-check"))
    }

    @Test
    fun everyIdUsedByTheAppHasATemplate() {
        val appIds = listOf(
            "fix", "rewrite", "shorten", "summarize", "action_items", "title", "steps", "polish",
            "follow_up", "interview_prep", "study_plan", "day_plan", "explain_month", "category",
        )
        appIds.forEach { assertTrue("missing prompt for $it", it in AiPrompts.ids) }
    }

    @Test
    fun numberGuard_findsInventedNumbers() {
        val source = "Ravi paid ₹4,250 on 12 March."
        assertEquals(emptyList<String>(), NumberGuard.invented(source, "Ravi paid 4250 on 12 March."))
        assertEquals(listOf("5000"), NumberGuard.invented(source, "Ravi paid ₹5,000."))
    }

    @Test
    fun numberGuard_ignoresListNumberingAndSmallNumbers() {
        assertEquals(emptyList<String>(), NumberGuard.invented("Plan the trip", "1. Book tickets\n2. Pack bags\n3. Leave at 9"))
    }

    @Test
    fun numberGuard_findsDroppedNumbersOnlyWhereTheyMustSurvive() {
        val source = "Pay ₹4,250 by 2026-11-03."
        assertEquals(listOf("2026", "4250"), NumberGuard.dropped(source, "Pay by November."))
        assertTrue(NumberGuard.warnings("summarize", source, "Pay the bill.").isEmpty())
        assertTrue(NumberGuard.warnings("fix", source, "Pay by November.").isNotEmpty())
        assertTrue(NumberGuard.warnings("fix", source, "Pay ₹4,250 by 2026-11-03.").isEmpty())
    }

    @Test
    fun verifyPrompt_containsBothSourceAndDraft() {
        val v = AiPrompts.verify("the source", "the draft")
        assertTrue(v.contains("SOURCE:\nthe source"))
        assertTrue(v.contains("DRAFT:\nthe draft"))
    }
}

class CleanupTest {
    @Test
    fun removesThinkingAndWrappingQuotes() {
        assertEquals("Hello", Cleanup.clean("<think>hmm\nlet me see</think>\n\"Hello\""))
        assertEquals("Say \"hi\" now", Cleanup.clean("Say \"hi\" now"))
        assertEquals("Plain", Cleanup.clean("  Plain \n"))
    }
}
