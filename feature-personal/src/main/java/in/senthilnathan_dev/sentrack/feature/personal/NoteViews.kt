package `in`.senthilnathan_dev.sentrack.feature.personal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction

/** Turns one line of note text into styled text (bold, italic, strikethrough, code). */
@Composable
private fun styled(text: String, forceStrike: Boolean = false): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    return remember(text, forceStrike, codeBackground) {
        buildAnnotatedString {
            NoteFormat.parseInline(text).forEach { s ->
                withStyle(
                    SpanStyle(
                        fontWeight = if (s.bold) FontWeight.Bold else null,
                        fontStyle = if (s.italic) FontStyle.Italic else null,
                        textDecoration = if (s.strike || forceStrike) TextDecoration.LineThrough else null,
                        fontFamily = if (s.code) FontFamily.Monospace else null,
                        background = if (s.code) codeBackground else androidx.compose.ui.graphics.Color.Unspecified,
                    ),
                ) { append(s.text) }
            }
        }
    }
}

/**
 * The reading view of a note: headings, lists, quotes and styled text. Check boxes can be ticked right here
 * ([onToggleCheck] receives the source line); pass null to make it read-only.
 */
@Composable
internal fun MarkdownView(markdown: String, onToggleCheck: ((Int) -> Unit)?, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { NoteFormat.parse(markdown) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Block.Heading -> Text(
                    styled(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    modifier = Modifier.padding(top = 6.dp),
                )
                is Block.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyLarge)
                    Text(styled(block.text), style = MaterialTheme.typography.bodyLarge)
                }
                is Block.Numbered -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${block.number}.", style = MaterialTheme.typography.bodyLarge)
                    Text(styled(block.text), style = MaterialTheme.typography.bodyLarge)
                }
                is Block.Check -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = block.checked,
                        onCheckedChange = onToggleCheck?.let { toggle -> { _: Boolean -> toggle(block.line) } },
                        enabled = onToggleCheck != null,
                    )
                    Text(
                        styled(block.text, forceStrike = block.checked),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (block.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
                is Block.Quote -> Row(modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(styled(block.text), style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
                }
                is Block.Rule -> HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                is Block.Paragraph -> Text(styled(block.text), style = MaterialTheme.typography.bodyLarge)
                is Block.Blank -> Spacer(Modifier.height(6.dp))
            }
        }
    }
}

internal enum class FormatAction(val icon: ImageVector, val description: String) {
    BOLD(Icons.Filled.FormatBold, "Bold"),
    ITALIC(Icons.Filled.FormatItalic, "Italic"),
    STRIKE(Icons.Filled.FormatStrikethrough, "Strikethrough"),
    HEADING(Icons.Filled.Title, "Heading"),
    BULLET(Icons.Filled.FormatListBulleted, "Bullet list"),
    CHECK(Icons.Filled.Checklist, "Check list"),
    NUMBERED(Icons.Filled.FormatListNumbered, "Numbered list"),
    QUOTE(Icons.Filled.FormatQuote, "Quote"),
}

/** Applies a toolbar action to the text and selection. */
internal fun applyFormat(value: TextFieldValue, action: FormatAction): TextFieldValue {
    val edit = NoteEditing.Edit(value.text, value.selection.start, value.selection.end)
    val result = when (action) {
        FormatAction.BOLD -> NoteEditing.wrap(edit, "**")
        FormatAction.ITALIC -> NoteEditing.wrap(edit, "*")
        FormatAction.STRIKE -> NoteEditing.wrap(edit, "~~")
        FormatAction.HEADING -> NoteEditing.toggleLinePrefix(edit, "## ")
        FormatAction.BULLET -> NoteEditing.toggleLinePrefix(edit, "- ")
        FormatAction.CHECK -> NoteEditing.toggleLinePrefix(edit, "- [ ] ")
        FormatAction.NUMBERED -> NoteEditing.toggleLinePrefix(edit, "1. ")
        FormatAction.QUOTE -> NoteEditing.toggleLinePrefix(edit, "> ")
    }
    return TextFieldValue(result.text, TextRange(result.selStart, result.selEnd))
}

/**
 * Styles the text as you type: marks fade into the background, bold looks bold, headings are large, quotes are
 * italic. The text itself is unchanged (same characters, same cursor positions), so editing stays predictable.
 */
internal class MarkdownTransformation(
    private val markerColor: Color,
    private val accent: Color,
    private val codeBackground: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val styled = buildAnnotatedString {
            append(source)
            var offset = 0
            for (line in source.split('\n')) {
                styleLine(line, offset)
                offset += line.length + 1
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    private fun AnnotatedString.Builder.styleLine(line: String, at: Int) {
        val dim = SpanStyle(color = markerColor)
        // Line-level: block marks and sizes.
        when {
            line.startsWith("### ") -> { addStyle(dim, at, at + 4); addStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold), at + 4, at + line.length) }
            line.startsWith("## ") -> { addStyle(dim, at, at + 3); addStyle(SpanStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold), at + 3, at + line.length) }
            line.startsWith("# ") -> { addStyle(dim, at, at + 2); addStyle(SpanStyle(fontSize = 25.sp, fontWeight = FontWeight.Bold), at + 2, at + line.length) }
            line.startsWith("- [ ] ") -> addStyle(dim, at, at + 6)
            line.startsWith("- [x] ") || line.startsWith("- [X] ") -> {
                addStyle(dim, at, at + 6)
                addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = markerColor), at + 6, at + line.length)
            }
            line.startsWith("- ") -> addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), at, at + 2)
            line.startsWith("> ") -> { addStyle(dim, at, at + 2); addStyle(SpanStyle(fontStyle = FontStyle.Italic), at + 2, at + line.length) }
            line.trim() == "---" -> addStyle(dim, at, at + line.length)
            else -> Regex("""^\d{1,3}\. """).find(line)?.let { addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), at, at + it.value.length) }
        }
        // Inline: marks dim, styled stretches styled.
        for (h in NoteFormat.highlights(line)) {
            val style = when (h.kind) {
                HighlightKind.MARKER -> dim
                HighlightKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                HighlightKind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                HighlightKind.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                HighlightKind.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
            }
            addStyle(style, at + h.start, at + h.end)
        }
    }
}

/** A borderless writing surface with live formatting, like modern notes apps. */
@Composable
internal fun LiveBodyField(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Start writing…",
) {
    val colors = MaterialTheme.colorScheme
    val transformation = remember(colors.outline, colors.primary, colors.surfaceVariant) {
        MarkdownTransformation(colors.outline.copy(alpha = 0.7f), colors.primary, colors.surfaceVariant)
    }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        visualTransformation = transformation,
        // Normal typing help: capital letters at sentence starts, spelling correction, and word suggestions.
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            autoCorrectEnabled = true,
            keyboardType = KeyboardType.Text,
        ),
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp)) {
                if (value.text.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                inner()
            }
        },
    )
}

/**
 * The formatting bar, docked above the keyboard while writing: undo, redo, then the text formats, then attach.
 */
@Composable
internal fun FormatToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAction: (FormatAction) -> Unit,
    onAttach: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        IconAction(Icons.AutoMirrored.Filled.Undo, "Undo", onUndo, enabled = canUndo)
        IconAction(Icons.AutoMirrored.Filled.Redo, "Redo", onRedo, enabled = canRedo)
        FormatAction.entries.forEach { action ->
            IconAction(action.icon, action.description, { onAction(action) })
        }
        IconAction(Icons.Filled.AttachFile, "Attach files", onAttach)
    }
}
