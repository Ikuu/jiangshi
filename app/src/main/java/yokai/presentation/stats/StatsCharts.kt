package yokai.presentation.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.compose.stringResource
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import yokai.i18n.MR

private val BarMaxWidth = 24.dp
private val BarCorner = 4.dp
private val BarGap = 2.dp
private val LabelStrip = 18.dp

/**
 * Columns growing from one baseline, in one colour. Tapping a column shows its label and value in the
 * readout above; with nothing tapped the readout shows [summary].
 */
@Composable
fun ColumnChart(
    values: List<Long>,
    labels: List<String>,
    axisLabel: (Int) -> String?,
    formatValue: (Long) -> String,
    summary: String,
    modifier: Modifier = Modifier,
    chartHeight: androidx.compose.ui.unit.Dp = 140.dp,
) {
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val barColor = MaterialTheme.colorScheme.primary
    val mutedBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val axisTextStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()
    val maxValue = values.maxOrNull()?.takeIf { it > 0 } ?: 0L

    Column(modifier) {
        val readout = selected?.let { "${labels[it]} · ${formatValue(values[it])}" } ?: summary
        Text(
            text = readout,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(LabelStrip + chartHeight + 20.dp)
                .semantics {
                    contentDescription = values.indices.joinToString { "${labels[it]} ${formatValue(values[it])}" }
                }
                .pointerInput(values) {
                    detectTapGestures { offset ->
                        if (values.isEmpty()) return@detectTapGestures
                        val index = floor(offset.x / (size.width.toFloat() / values.size)).toInt()
                            .coerceIn(0, values.lastIndex)
                        selected = if (selected == index) null else index
                    }
                },
        ) {
            if (values.isEmpty()) return@Canvas
            val plotTop = LabelStrip.toPx()
            val plotHeight = plotTop + chartHeight.toPx()
            val slot = size.width / values.size
            val barWidth = min(BarMaxWidth.toPx(), max(slot - BarGap.toPx(), 1f))
            val corner = min(BarCorner.toPx(), barWidth / 2)

            values.forEachIndexed { index, value ->
                if (value <= 0 || maxValue == 0L) return@forEachIndexed
                val height = max(chartHeight.toPx() * value / maxValue, corner * 2)
                val left = slot * index + (slot - barWidth) / 2
                val top = plotHeight - height
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left,
                            top = top,
                            right = left + barWidth,
                            bottom = plotHeight,
                            topLeftCornerRadius = CornerRadius(corner),
                            topRightCornerRadius = CornerRadius(corner),
                        ),
                    )
                }
                val color = if (selected == null || selected == index) barColor else mutedBarColor
                drawPath(path, color)
            }

            // Baseline, plus a hairline at the tallest column so its height can be read
            drawLine(lineColor, Offset(0f, plotHeight), Offset(size.width, plotHeight), strokeWidth = 1.dp.toPx())
            if (maxValue > 0) {
                drawLine(lineColor, Offset(0f, plotTop), Offset(size.width, plotTop), strokeWidth = 1.dp.toPx())
                val maxLabel = textMeasurer.measure(formatValue(maxValue), axisTextStyle)
                drawText(
                    maxLabel,
                    topLeft = Offset(size.width - maxLabel.size.width, plotTop - maxLabel.size.height - 2.dp.toPx()),
                )
            }

            values.indices.forEach { index ->
                val text = axisLabel(index) ?: return@forEach
                val layout = textMeasurer.measure(text, axisTextStyle)
                val centre = slot * index + slot / 2
                val x = (centre - layout.size.width / 2).coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(x, plotHeight + 4.dp.toPx()))
            }
        }
    }
}

/**
 * A calendar of reading days, weeks as columns ending this week, as many weeks as fit.
 * More reading is a darker step of one colour.
 */
@Composable
fun ReadingCalendar(
    readingDays: Map<LocalDate, Long>,
    today: LocalDate,
    formatDate: (LocalDate) -> String,
    formatValue: (Long) -> String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    var selected by remember(readingDays) { mutableStateOf<LocalDate?>(null) }
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val full = MaterialTheme.colorScheme.primary
    val levels = remember(empty, full) { listOf(empty) + (1..4).map { lerp(empty, full, it / 4f) } }
    val outline = MaterialTheme.colorScheme.onSurface
    val cell = 14.dp
    val gap = 3.dp

    Column(modifier) {
        val readout = selected?.let { "${formatDate(it)} · ${formatValue(readingDays[it] ?: 0L)}" } ?: summary
        Text(readout, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val weeks = ((maxWidth + gap) / (cell + gap)).toInt().coerceAtLeast(1)
        val lastWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val firstWeekStart = lastWeekStart.minusWeeks((weeks - 1).coerceAtLeast(0).toLong())
        val visibleMax = readingDays.filterKeys { !it.isBefore(firstWeekStart) && !it.isAfter(today) }.values.maxOrNull() ?: 0L

        fun level(value: Long): Int = when {
            value <= 0L || visibleMax == 0L -> 0
            else -> (1 + (value * 4 / (visibleMax + 1)).toInt()).coerceIn(1, 4)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(cell * 7 + gap * 6)
                .pointerInput(weeks, firstWeekStart) {
                    detectTapGestures { offset ->
                        val step = (cell + gap).toPx()
                        val column = (offset.x / step).toInt()
                        val row = (offset.y / step).toInt().coerceIn(0, 6)
                        if (column !in 0 until weeks) return@detectTapGestures
                        val date = firstWeekStart.plusWeeks(column.toLong()).plusDays(row.toLong())
                        if (date.isAfter(today)) return@detectTapGestures
                        selected = if (selected == date) null else date
                    }
                },
        ) {
            val step = (cell + gap).toPx()
            val corner = CornerRadius(3.dp.toPx())
            for (column in 0 until weeks) {
                for (row in 0 until 7) {
                    val date = firstWeekStart.plusWeeks(column.toLong()).plusDays(row.toLong())
                    if (date.isAfter(today)) continue
                    val topLeft = Offset(column * step, row * step)
                    drawRoundRect(levels[level(readingDays[date] ?: 0L)], topLeft, Size(cell.toPx(), cell.toPx()), corner)
                    if (date == selected) {
                        drawRoundRect(
                            outline,
                            topLeft,
                            Size(cell.toPx(), cell.toPx()),
                            corner,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                        )
                    }
                }
            }
        }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(MR.strings.stats_less), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            levels.forEach { color ->
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
            }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(MR.strings.stats_more), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A name and figure per row with a thin bar under it, scaled to the largest row. Largest first. */
@Composable
fun MeterList(
    rows: List<Pair<String, Long>>,
    formatValue: (Long) -> String,
    modifier: Modifier = Modifier,
    detail: ((Int) -> String?)? = null,
) {
    val maxValue = rows.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1L
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEachIndexed { index, (name, value) ->
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(formatValue(value), style = MaterialTheme.typography.bodyMedium)
                }
                detail?.invoke(index)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Meter(fraction = value.toFloat() / maxValue)
            }
        }
    }
}

@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        val width = size.width * fraction.coerceIn(0f, 1f)
        if (width > 0f) drawRoundRect(fill, size = Size(max(width, size.height), size.height), cornerRadius = radius)
    }
}

/** Small label over a figure, for the overview row. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, hero: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = if (hero) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            fontSize = if (hero) 48.sp else MaterialTheme.typography.titleLarge.fontSize,
        )
    }
}
