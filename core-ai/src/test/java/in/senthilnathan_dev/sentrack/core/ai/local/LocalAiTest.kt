package `in`.senthilnathan_dev.sentrack.core.ai.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAiTest {
    private fun phone(ram: Long = 8000, avail: Long = 4000, storage: Long = 20000, abis: List<String> = listOf("arm64-v8a"), lowRam: Boolean = false, thermal: Int? = 0) =
        DeviceProfile(ram, avail, storage, abis, 36, lowRam, thermal)

    private fun level(checks: List<Check>, label: String) = checks.first { it.label == label }.level

    @Test
    fun healthyPhone_passesEverything() {
        val checks = DeviceCheck.evaluate(phone(), 584)
        assertTrue(DeviceCheck.canProceed(checks))
        assertTrue(checks.all { it.level == Level.OK })
    }

    @Test
    fun hardFailures_blockTheTest() {
        assertFalse(DeviceCheck.canProceed(DeviceCheck.evaluate(phone(abis = listOf("armeabi-v7a")), 584)))
        assertEquals(Level.FAIL, level(DeviceCheck.evaluate(phone(ram = 3000), 584), "Memory"))
        assertEquals(Level.FAIL, level(DeviceCheck.evaluate(phone(storage = 1000), 584), "Storage"))
        assertEquals(Level.FAIL, level(DeviceCheck.evaluate(phone(thermal = 4), 584), "Heat"))
    }

    @Test
    fun softProblems_warnButAllowTheTest() {
        val checks = DeviceCheck.evaluate(phone(avail = 900, lowRam = true, thermal = 2), 584)
        assertEquals(Level.WARN, level(checks, "Memory"))
        assertEquals(Level.WARN, level(checks, "Phone class"))
        assertEquals(Level.WARN, level(checks, "Heat"))
        assertTrue(DeviceCheck.canProceed(checks))
    }

    @Test
    fun largerModel_wantsMoreMemory() {
        val small = level(DeviceCheck.evaluate(phone(avail = 2000), 584), "Memory")
        val large = level(DeviceCheck.evaluate(phone(avail = 2000), 1600), "Memory")
        assertEquals(Level.OK, small)
        assertEquals(Level.WARN, large)
    }

    private fun result(chars: Int, ms: Long, firstMs: Long = 1500, grammar: String = "I went to the market yesterday and bought two apples.", summary: String = "Ravi paid ₹4,250 for the bill.") =
        BenchmarkResult(5000, firstMs, chars, ms, grammar, summary)

    @Test
    fun verdict_followsSpeed() {
        assertEquals(Verdict.GOOD, BenchmarkScoring.verdict(result(chars = 480, ms = 10_000)))      // 12 tokens/s
        assertEquals(Verdict.USABLE, BenchmarkScoring.verdict(result(chars = 240, ms = 10_000)))    // 6 tokens/s
        assertEquals(Verdict.TOO_SLOW, BenchmarkScoring.verdict(result(chars = 80, ms = 10_000)))   // 2 tokens/s
        assertEquals(Verdict.USABLE, BenchmarkScoring.verdict(result(chars = 480, ms = 10_000, firstMs = 9000)))
    }

    @Test
    fun verdict_poorQualityBeatsSpeed() {
        val r = result(chars = 480, ms = 10_000, grammar = "banana", summary = "He paid some money.")
        assertEquals(Verdict.POOR_QUALITY, BenchmarkScoring.verdict(r))
    }

    @Test
    fun qualityChecks() {
        assertTrue(BenchmarkScoring.grammarPasses("I went to the market yesterday and bought two apples."))
        assertFalse(BenchmarkScoring.grammarPasses("i has went to the market yesterday and buyed two apple."))
        assertTrue(BenchmarkScoring.summaryPasses("He paid ₹4 250 for the bill".replace("4 250", "4,250")))
        assertTrue(BenchmarkScoring.summaryPasses("Paid 4250 rupees"))
        assertFalse(BenchmarkScoring.summaryPasses("Paid about four thousand"))
    }

    @Test
    fun zeroTimeDoesNotDivideByZero() {
        assertEquals(0.0, result(chars = 100, ms = 0).tokensPerSecond, 0.0)
    }

}

class StreamAccumulatorTest {
    @Test
    fun incrementalPieces_areJoined() {
        val a = StreamAccumulator()
        listOf("Hel", "lo ", "world").forEach(a::add)
        assertEquals("Hello world", a.value)
    }

    @Test
    fun cumulativePieces_doNotDoubleCount() {
        val a = StreamAccumulator()
        listOf("Hel", "Hello", "Hello world").forEach(a::add)
        assertEquals("Hello world", a.value)
        assertEquals(11, a.length)
    }
}

class SetupPlannerTest {
    private fun phone(ram: Long, storage: Long, lowRam: Boolean = false) =
        DeviceProfile(ram, ram / 2, storage, listOf("arm64-v8a"), 36, lowRam, 0)

    @Test
    fun recommend_bigModelOnlyWhenThePhoneCanHoldIt() {
        assertEquals(LocalModels.BEST, SetupPlanner.recommend(phone(8000, 30_000)))
        assertEquals(LocalModels.FAST, SetupPlanner.recommend(phone(4500, 30_000)))      // too little RAM
        assertEquals(LocalModels.FAST, SetupPlanner.recommend(phone(8000, 3_000)))       // too little storage
        assertEquals(LocalModels.FAST, SetupPlanner.recommend(phone(8000, 30_000, lowRam = true)))
    }

    @Test
    fun fallback_walksDownToTheSmallest() {
        assertEquals(LocalModels.FAST, SetupPlanner.fallbackFor(LocalModels.BEST))
        assertNull(SetupPlanner.fallbackFor(LocalModels.FAST))
    }

    @Test
    fun catalog_entriesAreComplete() {
        LocalModels.catalog.forEach {
            assertTrue(it.url.startsWith("https://"))
            assertTrue(it.fileName.endsWith(".litertlm"))
            assertEquals(64, it.sha256.length)
            assertTrue(it.sizeBytes > 100_000_000L)
        }
        assertEquals(LocalModels.BEST, LocalModels.byId("gemma4-e2b"))
        assertEquals(LocalModels.FAST, LocalModels.byFileName("QWEN3_0.6B_NOTHINK_Q4_BLOCK32_EKV1280.LITERTLM"))
    }

    @Test
    fun downloadMath() {
        assertNull(DownloadMath.rangeHeader(0))
        assertEquals("bytes=500-", DownloadMath.rangeHeader(500))
        assertEquals(1000L, DownloadMath.totalFromContentRange("bytes 100-999/1000"))
        assertNull(DownloadMath.totalFromContentRange("bytes 100-999/*"))
        assertNull(DownloadMath.totalFromContentRange(null))
        assertEquals(0.5f, DownloadMath.fraction(50, 100)!!, 0.0001f)
        assertNull(DownloadMath.fraction(5, 0))
    }
}
