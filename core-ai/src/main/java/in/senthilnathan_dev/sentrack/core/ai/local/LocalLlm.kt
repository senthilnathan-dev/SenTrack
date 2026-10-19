package `in`.senthilnathan_dev.sentrack.core.ai.local

import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Joins the pieces of a streamed answer. Runtimes differ on whether each piece is only the new words or the whole
 * answer so far; this handles both so speed measurements are not inflated.
 */
class StreamAccumulator {
    private val text = StringBuilder()
    val value: String get() = text.toString()
    val length: Int get() = text.length

    fun add(piece: String) {
        if (piece.isEmpty()) return
        if (text.isNotEmpty() && piece.length >= text.length && piece.startsWith(text)) {
            text.setLength(0)   // cumulative: the piece already contains everything so far
        }
        text.append(piece)
    }
}

/**
 * A model running on this phone through LiteRT-LM. Nothing here touches the network.
 * Load it once, ask as often as needed, then [close] it to free the memory (a loaded model holds hundreds of MB).
 */
class LocalLlm(private val modelPath: String) : AutoCloseable {
    private var engine: Engine? = null

    /** Loads the model. Can take several seconds. Returns how long it took in milliseconds. */
    suspend fun load(): Long = withContext(Dispatchers.Default) {
        val start = System.nanoTime()
        val e = Engine(EngineConfig(modelPath = modelPath))
        e.initialize()
        engine = e
        (System.nanoTime() - start) / 1_000_000
    }

    /** Asks one question in a fresh conversation and returns the whole answer. */
    suspend fun ask(prompt: String): String = withContext(Dispatchers.Default) {
        val e = engine ?: error("The model is not loaded")
        e.createConversation().use { conversation -> conversation.sendMessage(prompt).toString().trim() }
    }

    /**
     * Asks and streams the answer. [onPiece] receives the answer so far each time it grows. Returns the full answer
     * with the time to the first piece (ms) and the total time (ms).
     */
    suspend fun stream(prompt: String, onPiece: (String) -> Unit = {}): StreamedAnswer = withContext(Dispatchers.Default) {
        val e = engine ?: error("The model is not loaded")
        val start = System.nanoTime()
        var first = -1L
        val acc = StreamAccumulator()
        e.createConversation().use { conversation ->
            conversation.sendMessageAsync(prompt).collect { piece ->
                if (first < 0) first = (System.nanoTime() - start) / 1_000_000
                acc.add(piece.toString())
                onPiece(acc.value)
            }
        }
        val total = (System.nanoTime() - start) / 1_000_000
        StreamedAnswer(acc.value.trim(), if (first < 0) total else first, total)
    }

    override fun close() {
        runCatching { engine?.close() }
        engine = null
    }

    /** Runs the standard test: load, a speed run, then the two accuracy prompts. */
    suspend fun benchmark(): BenchmarkResult {
        val loadMillis = load()
        val speed = stream("Write a short paragraph of about 60 words on why saving money regularly matters.")
        val grammar = ask(BenchmarkScoring.GRAMMAR_PROMPT)
        val summary = ask(BenchmarkScoring.SUMMARY_PROMPT)
        return BenchmarkResult(loadMillis, speed.firstPieceMillis, speed.text.length, speed.totalMillis, grammar, summary)
    }
}

data class StreamedAnswer(val text: String, val firstPieceMillis: Long, val totalMillis: Long)
