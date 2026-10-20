package `in`.senthilnathan_dev.sentrack.ui.about

import android.content.Context

/** A heading with its text. [heading] is null for text that comes before the first heading. */
internal data class DocBlock(val heading: String?, val text: String)

/** One topic of a document: a page of the manual, a release in the changelog, or the whole privacy policy. */
internal data class DocTopic(val title: String, val blocks: List<DocBlock>)

/**
 * Reads the plain-text documents in `docs/legal` (privacy policy, terms, user guide, changelog). The same files are
 * packaged into the app and turned into the published web pages, so the words can never differ.
 *
 * Format: `# Title` starts a topic, `## Heading` starts a block inside it, blank lines separate paragraphs, and lines
 * starting with `- ` are bullets.
 */
internal object LegalText {
    fun parse(text: String): List<DocTopic> {
        val topics = ArrayList<DocTopic>()
        var title: String? = null
        val blocks = ArrayList<DocBlock>()
        var heading: String? = null
        val paragraphs = ArrayList<String>()
        val lines = ArrayList<String>()

        fun endParagraph() {
            if (lines.isNotEmpty()) paragraphs += lines.joinToString("\n") { if (it.startsWith("- ")) "• " + it.removePrefix("- ") else it }
            lines.clear()
        }
        fun endBlock() {
            endParagraph()
            if (paragraphs.isNotEmpty() || heading != null) blocks += DocBlock(heading, paragraphs.joinToString("\n\n"))
            paragraphs.clear(); heading = null
        }
        fun endTopic() {
            endBlock()
            title?.let { topics += DocTopic(it, blocks.toList()) }
            blocks.clear(); title = null
        }

        for (raw in text.lines()) {
            val line = raw.trimEnd()
            when {
                line.startsWith("## ") -> { endBlock(); heading = line.removePrefix("## ").trim() }
                line.startsWith("# ") -> { endTopic(); title = line.removePrefix("# ").trim() }
                line.isBlank() -> endParagraph()
                else -> lines += line
            }
        }
        endTopic()
        return topics
    }

    /** Loads one of the packaged documents: `privacy.txt`, `terms.txt`, `manual.txt` or `changelog.txt`. */
    fun load(context: Context, name: String): List<DocTopic> =
        runCatching { parse(context.assets.open(name).bufferedReader().use { it.readText() }) }.getOrDefault(emptyList())
}

/** One release in the changelog, read from a topic titled `0.5.0 | 2026-10-20 | note`. */
internal data class Release(val version: String, val date: String, val label: String, val sections: List<DocBlock>) {
    companion object {
        fun from(topic: DocTopic): Release {
            val parts = topic.title.split("|").map { it.trim() }
            return Release(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" }, parts.getOrElse(2) { "" }, topic.blocks)
        }
    }
}
