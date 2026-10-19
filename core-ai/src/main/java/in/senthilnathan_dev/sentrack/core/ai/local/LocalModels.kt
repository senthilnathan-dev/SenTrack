package `in`.senthilnathan_dev.sentrack.core.ai.local

/**
 * A model SenTrack knows works with its on-device runtime (LiteRT-LM, `.litertlm` files).
 *
 * Models are **not** bundled in the app (too large for a Play Store install) and are not downloaded by SenTrack:
 * Gemma's files sit behind a licence the user must accept on Hugging Face, so the user gets the file themselves and
 * imports it. That keeps the licence acceptance with the person it binds, and SenTrack never holds a login or token.
 */
data class ModelOption(
    val id: String,
    val title: String,
    val fileName: String,
    val approxSizeMb: Int,
    val license: String,
    val pageUrl: String,
    val about: String,
)

object LocalModels {
    val catalog = listOf(
        ModelOption(
            id = "gemma3-1b-int4",
            title = "Gemma 3 1B (small, recommended first)",
            fileName = "gemma3-1b-it-int4.litertlm",
            approxSizeMb = 584,
            license = "Gemma terms of use (accept on Hugging Face)",
            pageUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT",
            about = "Smallest and fastest. Good for tidying, fixing and summarizing short text. Weak at reasoning.",
        ),
        ModelOption(
            id = "qwen25-1.5b-q8",
            title = "Qwen 2.5 1.5B (larger, open licence)",
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            approxSizeMb = 1600,
            license = "Apache 2.0",
            pageUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct",
            about = "Bigger and a little more capable, but slower and heavier on memory. Try it only if the small one passes easily.",
        ),
    )

    /** What to tell the user when they pick a file: the size we expect for it, if we recognise the name. */
    fun byFileName(name: String): ModelOption? = catalog.firstOrNull { it.fileName.equals(name, ignoreCase = true) }
}
