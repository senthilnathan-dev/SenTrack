package `in`.senthilnathan_dev.sentrack.core.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProtocolTest {
    @Test
    fun prompt_containsInstructionAndText() {
        val p = AiProtocol.prompt(AiTask.SUMMARIZE, "hello world")
        assertTrue(p.startsWith("Summarize"))
        assertTrue(p.endsWith("hello world"))
        assertTrue(AiProtocol.prompt(AiTask.CUSTOM, "x", "Translate to Tamil").startsWith("Translate to Tamil"))
    }

    @Test
    fun requests_keepTheKeyOutOfTheAddress() {
        AiProvider.entries.forEach { provider ->
            val spec = AiProtocol.request(provider, provider.defaultModel, "SECRET-KEY", "hi")
            assertFalse("key leaked into the URL for $provider", spec.url.contains("SECRET-KEY"))
            assertTrue(spec.url.startsWith("https://"))
            assertTrue(spec.headers.values.any { it.contains("SECRET-KEY") })
        }
    }

    @Test
    fun requestBodies_carryModelAndPrompt() {
        val openai = JSONObject(AiProtocol.request(AiProvider.OPENAI, "gpt-x", "k", "hi there").body)
        assertEquals("gpt-x", openai.getString("model"))
        assertEquals("hi there", openai.getJSONArray("messages").getJSONObject(0).getString("content"))
        val claude = JSONObject(AiProtocol.request(AiProvider.CLAUDE, "claude-x", "k", "yo").body)
        assertEquals("claude-x", claude.getString("model"))
        assertTrue(claude.has("max_tokens"))
        val gemini = AiProtocol.request(AiProvider.GEMINI, "gem-x ", "k", "yo")
        assertTrue(gemini.url.contains("/models/gem-x:generateContent"))
    }

    @Test
    fun parse_readsEachProvidersShape() {
        assertEquals("a", AiProtocol.parse(AiProvider.GEMINI, """{"candidates":[{"content":{"parts":[{"text":" a "}]}}]}"""))
        assertEquals("b", AiProtocol.parse(AiProvider.OPENAI, """{"choices":[{"message":{"content":"b"}}]}"""))
        assertEquals("c", AiProtocol.parse(AiProvider.CLAUDE, """{"content":[{"type":"text","text":"c"}]}"""))
        assertNull(AiProtocol.parse(AiProvider.OPENAI, """{"nope":1}"""))
        assertNull(AiProtocol.parse(AiProvider.CLAUDE, "not json"))
    }

    @Test
    fun parseError_findsTheMessage() {
        assertEquals("bad key", AiProtocol.parseError("""{"error":{"message":"bad key"}}"""))
        assertNull(AiProtocol.parseError("junk"))
    }
}
