package `in`.senthilnathan_dev.sentrack.feature.finance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/*
 * Small hand-drawn charts (no chart library): fewer dependencies, full control, readable in light and dark.
 * Every chart is accompanied by text values so it is also usable with a screen reader.
 */

internal object ChartColors {
    val income = Color(0xFF3E9B6C)
    val expense = Color(0xFFD1626A)
    val invested = Color(0xFF4F7CAC)

    /** Distinct, colour-blind-friendly-ish colours for categories and investment kinds. */
    val palette = listOf(
        Color(0xFF4F7CAC), Color(0xFFE08E45), Color(0xFF5BAE8A), Color(0xFFB36AC4),
        Color(0xFFD1626A), Color(0xFF8C9B3F), Color(0xFF6C7A89),
    )
}

/** A thin horizontal bar chart: label and amount on one line, the share drawn underneath. */
@Composable
internal fun HorizontalBars(items: List<Triple<String, Long, Color>>) {
    val max = items.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (label, value, color) ->
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Money.format(value), style = MaterialTheme.typography.bodyMedium)
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((value.toFloat() / max).coerceIn(0.02f, 1f))
                            .height(8.dp)
                            .background(color, RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}

/** One bar split into coloured parts, with a legend underneath showing amount and share. */
@Composable
internal fun StackedBar(parts: List<Pair<String, Long>>) {
    val total = parts.sumOf { it.second }.takeIf { it > 0 } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(14.dp)) {
            parts.forEachIndexed { i, (_, value) ->
                Box(
                    modifier = Modifier
                        .weight(value.toFloat().coerceAtLeast(1f))
                        .height(14.dp)
                        .background(ChartColors.palette[i % ChartColors.palette.size]),
                )
            }
        }
        parts.forEachIndexed { i, (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(10.dp).background(ChartColors.palette[i % ChartColors.palette.size], CircleShape))
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "${Money.format(value)} · ${(value * 100 / total)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A trend line through [values] (oldest first) with the first and last labels under it. */
@Composable
internal fun LineChart(values: List<Long>, labels: List<String>, color: Color) {
    if (values.size < 2) return
    val min = values.min()
    val max = values.max()
    val span = (max - min).takeIf { it > 0 } ?: 1L
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val pad = 8.dp.toPx()
            val w = size.width - 2 * pad
            val h = size.height - 2 * pad
            fun x(i: Int) = pad + w * i / (values.size - 1)
            fun y(v: Long) = pad + h * (1f - (v - min).toFloat() / span)
            drawLine(gridColor, Offset(pad, size.height - pad), Offset(size.width - pad, size.height - pad), strokeWidth = 1.dp.toPx())
            val path = Path().apply {
                moveTo(x(0), y(values[0]))
                for (i in 1 until values.size) lineTo(x(i), y(values[i]))
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            values.forEachIndexed { i, v ->
                drawCircle(color, radius = if (i == values.lastIndex) 5.dp.toPx() else 3.dp.toPx(), center = Offset(x(i), y(v)))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(labels.first(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(labels.last(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Grouped vertical bars: one group per label, one bar per series (for example income, spent, invested). */
@Composable
internal fun GroupedBarChart(labels: List<String>, series: List<Pair<Color, List<Long>>>) {
    val max = series.maxOfOrNull { (_, v) -> v.maxOrNull() ?: 0L }?.takeIf { it > 0 } ?: return
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            val baseline = size.height - 2.dp.toPx()
            val top = 4.dp.toPx()
            val groupWidth = size.width / labels.size
            val barWidth = groupWidth * 0.7f / series.size
            val gap = groupWidth * 0.15f
            drawLine(gridColor, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 1.dp.toPx())
            labels.indices.forEach { g ->
                series.forEachIndexed { s, (color, values) ->
                    val v = values.getOrElse(g) { 0L }
                    val barHeight = (baseline - top) * v / max
                    if (barHeight > 0f) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(g * groupWidth + gap + s * barWidth, baseline - barHeight),
                            size = Size(barWidth - 2.dp.toPx(), barHeight),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A coloured dot and a label, for chart legends. */
@Composable
internal fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
