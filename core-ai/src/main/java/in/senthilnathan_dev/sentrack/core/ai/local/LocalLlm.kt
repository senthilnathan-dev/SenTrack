package `in`.senthilnathan_dev.sentrack.core.ai.local

import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * How the model should generate. [temperature] is how adventurous the word choice is (low = steady and exact, high =
 * varied); [topK] and [topP] limit the choices considered at each step; [maxChars] stops a long answer early, which is
 * the main lever for speed; [system] is the standing instruction.
 */
data class GenParams(
    val temperature: Double,
    val topK: Int,
    val topP: Double,
    val maxChars: Int,
    val system: String,
) {
    companion object {
        val DEFAULT = GenParams(temperature = 0.5, topK = 40, topP = 0.95, maxChars = 2_000, system = "")
    }
}

/**
 * A model running on this phone through LiteRT-LM. Nothing here touches the network.
 * Load it once, ask as often as needed, then [close] it to free the memory (a loaded model holds hundreds of MB).
 */
class LocalLlm(private val modelPath: String, private val cacheDir: String? = null) : AutoCloseable {
    private var engine: Engine? = null

    /** Loads the model. Can take several seconds. Returns how long it took in milliseconds. */
    suspend fun load(): Long = withContext(Dispatchers.Default) {
        val start = System.nanoTime()
        val e = Engine(if (cacheDir != null) EngineConfig(modelPath = modelPath, cacheDir = cacheDir) else EngineConfig(modelPath = modelPath))
        e.initialize()
        engine = e
        (System.nanoTime() - start) / 1_000_000
    }

    val isLoaded: Boolean get() = engine != null

    private fun config(p: GenParams): ConversationConfig =
        if (p.system.isBlank()) ConversationConfig(samplerConfig = SamplerConfig(topK = p.topK, topP = p.topP, temperature = p.temperature))
        else ConversationConfig(
            systemInstruction = Contents.of(p.system),
            samplerConfig = SamplerConfig(topK = p.topK, topP = p.topP, temperature = p.temperature),
        )

    /** Asks one question in a fresh conversation and returns the whole answer (cut at [GenParams.maxChars]). */
    suspend fun ask(prompt: String, params: GenParams = GenParams.DEFAULT): String = stream(prompt, params).text

    /**
     * Asks and streams the answer. [onPiece] receives the answer so far each time it grows. Returns the answer with the
     * time to the first piece (ms) and the total time (ms). Generation stops once [GenParams.maxChars] is reached.
     */
    suspend fun stream(prompt: String, params: GenParams = GenParams.DEFAULT, onPiece: (String) -> Unit = {}): StreamedAnswer =
        withContext(Dispatchers.Default) {
            val e = engine ?: error("The model is not loaded")
            val start = System.nanoTime()
            var first = -1L
            val acc = StreamAccumulator()
            e.createConversation(config(params)).use { conversation ->
                conversation.sendMessageAsync(prompt)
                    .takeWhile { piece ->
                        if (first < 0) first = (System.nanoTime() - start) / 1_000_000
                        acc.add(piece.toString())
                        onPiece(acc.value)
                        acc.length < params.maxChars
                    }
                    .collect { }
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

/**
 * Keeps the model loaded between requests, so only the first one pays the load time, and frees the memory after a
 * quiet spell. One request runs at a time. Call [release] when the app locks.
 */
class LocalLlmHolder(private val scope: CoroutineScope, private val cacheDir: String?, var idleMillis: Long = 2 * 60_000L) {
    private val mutex = Mutex()
    private var llm: LocalLlm? = null
    private var loadedPath: String? = null
    private var idleJob: Job? = null

    /** Runs [block] with the model for [path], loading it first if needed. */
    suspend fun <T> use(path: String, block: suspend (LocalLlm) -> T): T = mutex.withLock {
        idleJob?.cancel()
        try {
            if (llm == null || loadedPath != path) {
                llm?.close()
                llm = LocalLlm(path, cacheDir).also { it.load() }
                loadedPath = path
            }
            block(llm!!)
        } finally {
            idleJob = scope.launch {
                delay(idleMillis)
                release()
            }
        }
    }

    /** Frees the model now (on lock, or when it is deleted). */
    fun release() {
        scope.launch {
            mutex.withLock {
                idleJob?.cancel()
                llm?.close(); llm = null; loadedPath = null
            }
        }
    }
}
