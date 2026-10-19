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
import androidx.compose.material3.OutlinedTextField
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

/** The writing view: a row of formatting buttons above a plain text box. */
@Composable
internal fun BodyEditor(value: TextFieldValue, onChange: (TextFieldValue) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            FormatAction.entries.forEach { action ->
                IconAction(action.icon, action.description, { onChange(applyFormat(value, action)) })
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text("Write your note") },
            minLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
