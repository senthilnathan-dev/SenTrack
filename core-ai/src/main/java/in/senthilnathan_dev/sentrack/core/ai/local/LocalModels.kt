package `in`.senthilnathan_dev.sentrack.core.ai.local

/**
 * A model SenTrack can fetch and run on the phone (LiteRT-LM, `.litertlm` files).
 *
 * Every entry is openly licensed and downloadable without an account, so setup is one tap inside the app. The
 * [sha256] is the file's published hash: a download that does not match is thrown away, so a damaged or tampered
 * file is never loaded. Models are not bundled in the app because they are far too large for a Play Store install.
 */
data class ModelOption(
    val id: String,
    val title: String,
    val tagline: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val license: String,
) {
    val sizeMb: Int get() = (sizeBytes / (1024 * 1024)).toInt()
}

object LocalModels {
    /** Smallest and fastest: fits almost any phone, but its answers are basic. */
    val FAST = ModelOption(
        id = "qwen3-0.6b",
        title = "Qwen 3 (0.6B), fast",
        tagline = "Small and quick. Fine for fixing and tidying short text; basic at anything harder.",
        fileName = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
        url = "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
        sizeBytes = 347_251_840L,
        sha256 = "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139",
        license = "Apache 2.0",
    )

    /** The most capable model that is practical on a phone. Large download, needs a recent phone with plenty of free memory. */
    val BEST = ModelOption(
        id = "gemma4-e2b",
        title = "Gemma 4 (E2B), best",
        tagline = "Google's small on-device model. Much better answers, but a big download and slower on mid-range phones.",
        fileName = "gemma-4-E2B-it.litertlm",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712L,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        license = "Apache 2.0",
    )

    /** From the best fit down to the smallest. */
    val catalog = listOf(BEST, FAST)

    fun byId(id: String?): ModelOption? = catalog.firstOrNull { it.id == id }
    fun byFileName(name: String): ModelOption? = catalog.firstOrNull { it.fileName.equals(name, ignoreCase = true) }
}

/** Chooses which model to try first on a phone, and what to fall back to. */
object SetupPlanner {
    /**
     * The best model this phone can plausibly hold. It is only a starting point: the speed test still has the last word.
     * The large model wants 6 GB of RAM and room for the download plus working space.
     */
    fun recommend(p: DeviceProfile): ModelOption {
        val bigFits = p.totalRamMb >= 5_500 && p.freeStorageMb >= LocalModels.BEST.sizeMb * 2L + 500 && !p.lowRamDevice
        return if (bigFits) LocalModels.BEST else LocalModels.FAST
    }

    /** What to try next when [tried] turned out too slow or poor; null when nothing smaller is left. */
    fun fallbackFor(tried: ModelOption): ModelOption? {
        val i = LocalModels.catalog.indexOfFirst { it.id == tried.id }
        return if (i in 0 until LocalModels.catalog.lastIndex) LocalModels.catalog[i + 1] else null
    }
}

/** Helpers for resumable HTTP downloads. */
object DownloadMath {
    /** The Range header value to continue a partial file, or null when starting fresh. */
    fun rangeHeader(bytesAlready: Long): String? = if (bytesAlready > 0) "bytes=$bytesAlready-" else null

    /** Total size from a `Content-Range: bytes 100-199/1000` header; null if absent or unknown (`*`). */
    fun totalFromContentRange(header: String?): Long? = header?.substringAfter('/', "")?.trim()?.toLongOrNull()

    /** Download progress 0..1, or null when the total is unknown. */
    fun fraction(done: Long, total: Long): Float? = if (total <= 0) null else (done.toDouble() / total).toFloat().coerceIn(0f, 1f)
}
