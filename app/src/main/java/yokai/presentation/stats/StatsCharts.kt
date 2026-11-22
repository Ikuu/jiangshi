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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.text.font.FontWeight
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
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
 * A calendar of reading days, weeks as columns ending this week. It starts on the last [COMPACT_WEEKS] weeks
 * as large numbered squares filling the width, with weekday and month names, and Show more switches to small
 * squares going back as far as fit. More reading is a darker step of one colour.
 */
@Composable
fun ReadingCalendar(
    readingDays: Map<LocalDate, Long>,
    today: LocalDate,
    formatDate: (LocalDate) -> String,
    formatValue: (Long) -> String,
    summary: String,
    modifier: Modifier = Modifier,
    /** Opens the sessions for a day tapped in the calendar, if given. */
    onOpenDay: ((LocalDate) -> Unit)? = null,
) {
    var selected by remember(readingDays) { mutableStateOf<LocalDate?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val full = MaterialTheme.colorScheme.primary
    // The lightest step starts well clear of empty, so a few minutes' reading still stands out
    val levels = remember(empty, full) { listOf(empty) + (1..4).map { lerp(empty, full, 0.3f + 0.7f * it / 4f) } }
    val outline = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onFull = MaterialTheme.colorScheme.onPrimary
    val labelStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()
    val locale = remember { Locale.getDefault() }
    val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("MMM", locale) }

    Column(modifier) {
        val readout = selected?.let { "${formatDate(it)} · ${formatValue(readingDays[it] ?: 0L)}" } ?: summary
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                readout,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val day = selected
            if (day != null && onOpenDay != null && (readingDays[day] ?: 0L) > 0L) {
                TextButton(onClick = { onOpenDay(day) }) { Text(stringResource(MR.strings.stats_day_sessions)) }
            }
        }
        Spacer(Modifier.height(4.dp))

        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            // Weekday names down the side and month names along the top, only with the large squares
            val labelled = !expanded
            val sideLabel = if (labelled) 34.dp else 0.dp
            val topLabel = if (labelled) 20.dp else 0.dp
            val gap = if (expanded) 3.dp else 4.dp
            val weeks: Int
            val cell: Dp
            if (expanded) {
                cell = 14.dp
                weeks = ((maxWidth + gap) / (cell + gap)).toInt().coerceAtLeast(1)
            } else {
                weeks = COMPACT_WEEKS
                cell = ((maxWidth - sideLabel - gap * (weeks - 1)) / weeks).coerceIn(14.dp, 48.dp)
            }
            val lastWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val firstWeekStart = lastWeekStart.minusWeeks((weeks - 1).coerceAtLeast(0).toLong())
            val visibleMax = readingDays.filterKeys { !it.isBefore(firstWeekStart) && !it.isAfter(today) }.values.maxOrNull() ?: 0L
            val showNumbers = cell >= 22.dp

            fun level(value: Long): Int = when {
                value <= 0L || visibleMax == 0L -> 0
                else -> (1 + (value * 4 / (visibleMax + 1)).toInt()).coerceIn(1, 4)
            }

            Canvas(
                modifier = Modifier
                    .width(sideLabel + cell * weeks + gap * (weeks - 1))
                    .height(topLabel + cell * 7 + gap * 6)
                    .pointerInput(weeks, firstWeekStart, cell) {
                        detectTapGestures { offset ->
                            val step = (cell + gap).toPx()
                            val x = offset.x - sideLabel.toPx()
                            val y = offset.y - topLabel.toPx()
                            if (x < 0f || y < 0f) return@detectTapGestures
                            val column = (x / step).toInt()
                            val row = (y / step).toInt().coerceIn(0, 6)
                            if (column !in 0 until weeks) return@detectTapGestures
                            val date = firstWeekStart.plusWeeks(column.toLong()).plusDays(row.toLong())
                            if (date.isAfter(today)) return@detectTapGestures
                            selected = if (selected == date) null else date
                        }
                    },
            ) {
                val step = (cell + gap).toPx()
                val cellPx = cell.toPx()
                val left = sideLabel.toPx()
                val top = topLabel.toPx()
                val corner = CornerRadius(if (showNumbers) 6.dp.toPx() else 3.dp.toPx())

                if (labelled) {
                    for (row in 0 until 7) {
                        val name = DayOfWeek.MONDAY.plus(row.toLong()).getDisplayName(TextStyle.SHORT, locale)
                        val layout = textMeasurer.measure(name, labelStyle.copy(color = labelColor))
                        drawText(layout, topLeft = Offset(0f, top + row * step + (cellPx - layout.size.height) / 2))
                    }
                    // A month's name goes over the first column, and over the week its first day falls in
                    for (column in 0 until weeks) {
                        val weekStart = firstWeekStart.plusWeeks(column.toLong())
                        val firstOfMonth = (0L..6L).map { weekStart.plusDays(it) }.find { it.dayOfMonth == 1 && !it.isAfter(today) }
                        val labelDate = firstOfMonth ?: weekStart.takeIf { column == 0 } ?: continue
                        val layout = textMeasurer.measure(monthFormat.format(labelDate), labelStyle.copy(color = labelColor))
                        drawText(layout, topLeft = Offset(left + column * step, 0f))
                    }
                }

                for (column in 0 until weeks) {
                    for (row in 0 until 7) {
                        val date = firstWeekStart.plusWeeks(column.toLong()).plusDays(row.toLong())
                        if (date.isAfter(today)) continue
                        val topLeft = Offset(left + column * step, top + row * step)
                        val level = level(readingDays[date] ?: 0L)
                        drawRoundRect(levels[level], topLeft, Size(cellPx, cellPx), corner)
                        if (showNumbers) {
                            val style = labelStyle.copy(
                                color = if (level >= 3) onFull else labelColor,
                                fontWeight = if (date == today) FontWeight.Bold else null,
                            )
                            val layout = textMeasurer.measure(date.dayOfMonth.toString(), style)
                            drawText(
                                layout,
                                topLeft = topLeft + Offset((cellPx - layout.size.width) / 2, (cellPx - layout.size.height) / 2),
                            )
                        }
                        if (date == selected) {
                            drawRoundRect(
                                outline,
                                topLeft,
                                Size(cellPx, cellPx),
                                corner,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(MR.strings.stats_less), style = labelStyle, color = labelColor)
            Spacer(Modifier.width(4.dp))
            levels.forEach { color ->
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
            }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(MR.strings.stats_more), style = labelStyle, color = labelColor)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) MR.strings.stats_calendar_show_less else MR.strings.stats_calendar_show_more))
            }
        }
    }
}

/** Weeks the reading calendar shows at first: about the last two months. */
private const val COMPACT_WEEKS = 9

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
