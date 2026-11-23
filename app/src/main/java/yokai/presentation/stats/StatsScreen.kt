package yokai.presentation.stats

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import java.time.Instant
import yokai.domain.stats.ReadingSession
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import cafe.adriel.voyager.navigator.LocalNavigator
import yokai.domain.stats.GoalMeasure
import yokai.domain.stats.GoalPeriod
import yokai.domain.stats.GoalProgress
import yokai.domain.stats.ReadingGoal
import yokai.domain.stats.StreakTier
import yokai.domain.stats.Milestones
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import dev.icerock.moko.resources.compose.pluralStringResource
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.EntryKind
import eu.kanade.tachiyomi.util.compose.LocalBackPress
import eu.kanade.tachiyomi.util.compose.LocalRouter
import eu.kanade.tachiyomi.util.compose.currentOrThrow
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import yokai.domain.stats.ReadingCategory
import yokai.domain.stats.ReadingStats
import yokai.domain.stats.SeriesStats
import yokai.domain.stats.StatsPeriod
import yokai.domain.stats.TimelineUnit
import yokai.i18n.MR
import yokai.presentation.AppBarType
import yokai.presentation.YokaiScaffold
import yokai.presentation.core.enterAlwaysAppBarScrollBehavior
import yokai.presentation.manga.components.MangaCover
import yokai.presentation.manga.components.MangaCoverRatio
import yokai.util.Screen
import yokai.util.lang.getString

private val ContentMaxWidth = 720.dp
private const val MOST_READ_LIMIT = 10
private const val NEXT_MILESTONES = 3
private const val REACHED_MILESTONES = 5

/** Set when Statistics is a tab of the main navigation, with the room the bottom navigation takes. */
data class StatsTab(val bottomPadding: androidx.compose.ui.unit.Dp)

val LocalStatsTab = androidx.compose.runtime.compositionLocalOf<StatsTab?> { null }

class StatsScreen : Screen() {
    @Composable
    override fun Content() {
        val onBackPress = LocalBackPress.currentOrThrow
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { StatsScreenModel() }
        val state by screenModel.state.collectAsState()
        val listState = rememberLazyListState()

        // Reload on every visit, so coming back from the reader shows the new reading
        LaunchedEffect(Unit) { screenModel.refresh() }

        val tab = LocalStatsTab.current
        YokaiScaffold(
            onNavigationIconClicked = onBackPress,
            showNavigationIcon = tab == null,
            title = stringResource(MR.strings.statistics),
            appBarType = AppBarType.SMALL,
            scrollBehavior = enterAlwaysAppBarScrollBehavior(
                canScroll = { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 },
            ),
        ) { innerPadding ->
            when (val current = state) {
                StatsScreenModel.State.Loading -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                    CircularProgressIndicator()
                }
                is StatsScreenModel.State.Success -> StatsContent(
                    state = current,
                    listState = listState,
                    contentPadding = innerPadding,
                    bottomPadding = tab?.bottomPadding ?: 0.dp,
                    onPeriodChange = screenModel::setPeriod,
                    onGoalChange = screenModel::setGoal,
                    onOpenYear = { navigator.push(YearInReviewScreen(it)) },
                    sessionsOn = screenModel::sessionsOn,
                    chapterName = screenModel::chapterName,
                    onDeleteSession = screenModel::deleteSession,
                )
            }
        }
    }
}

@Composable
private fun StatsContent(
    state: StatsScreenModel.State.Success,
    listState: androidx.compose.foundation.lazy.LazyListState,
    contentPadding: PaddingValues,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onPeriodChange: (StatsPeriod) -> Unit,
    onGoalChange: (ReadingGoal?) -> Unit,
    onOpenYear: (Int) -> Unit,
    sessionsOn: (LocalDate) -> List<ReadingSession>,
    chapterName: (ReadingSession) -> String?,
    onDeleteSession: (ReadingSession) -> Unit,
) {
    val context = LocalContext.current
    val router = LocalRouter.current
    var editingGoal by remember { mutableStateOf(false) }
    var openDay by remember { mutableStateOf<LocalDate?>(null) }
    openDay?.let { day ->
        // Worked out again with each new state, so a deleted session leaves the list
        val daySessions = remember(state, day) { sessionsOn(day) }
        DaySessionsDialog(
            date = day,
            sessions = daySessions,
            chapterName = chapterName,
            mangaFor = { state.mangaByKey[StatsScreenModel.SeriesKey(it.mangaSource, it.mangaUrl)] },
            onOpen = { manga ->
                openDay = null
                router?.pushController(MangaDetailsController(manga).withFadeTransaction())
            },
            onDelete = onDeleteSession,
            onDismiss = { openDay = null },
        )
    }
    if (editingGoal) {
        GoalDialog(
            current = state.goal?.goal,
            onDismiss = { editingGoal = false },
            onSave = {
                onGoalChange(it)
                editingGoal = false
            },
        )
    }
    val stats = state.stats
    val time = remember(context) { { ms: Long -> context.formatReadTime(ms) } }
    val openManga: (Manga) -> Unit = { manga ->
        router?.pushController(MangaDetailsController(manga).withFadeTransaction())
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + bottomPadding + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            item(key = "goal") { GoalCard(state.goal, time, onEdit = { editingGoal = true }) }
            if (state.streakAtRisk) {
                item(key = "streak_at_risk") {
                    val streak = stats.currentStreak
                    val emoji = StreakTier.of(streak)?.emoji?.let { "$it " }.orEmpty()
                    Message(emoji + pluralStringResource(MR.plurals.stats_streak_at_risk, streak, streak), null)
                }
            }
            state.years.firstOrNull()?.let { year ->
                item(key = "year_in_review") { YearInReviewCard(year) { onOpenYear(year) } }
            }

            item(key = "period") { PeriodSelector(state.period, onPeriodChange) }

            if (!state.hasAnyReading) {
                item(key = "empty") {
                    Message(stringResource(MR.strings.stats_empty_title), stringResource(MR.strings.stats_empty_body))
                }
            } else {
                overview(stats, time)
                if (stats.isEmpty) {
                    item(key = "period_empty") { Message(stringResource(MR.strings.stats_period_empty), null) }
                } else {
                    timeSection(stats, time, context, onOpenDay = { openDay = it })
                    seriesSection(state, time, openManga)
                    typesSection(state, time)
                }
                milestonesSection(state.milestones, time)
            }

            state.library?.let { librarySection(it, time, openManga) }
        }
    }
}

@Composable
private fun GoalCard(progress: GoalProgress?, time: (Long) -> String, onEdit: () -> Unit) {
    if (progress == null) {
        Surface(
            onClick = onEdit,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(MR.strings.stats_goal_set), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(MR.strings.stats_goal_set_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    val goal = progress.goal
    val numbers = NumberFormat.getIntegerInstance()
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(goalTitle(goal.period)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text(stringResource(MR.strings.edit)) }
        }
        val counts = when (goal.measure) {
            GoalMeasure.MINUTES -> MR.plurals.stats_goal_minutes_of
            GoalMeasure.CHAPTERS -> MR.plurals.stats_goal_chapters_of
        }
        Text(
            pluralStringResource(counts, goal.target, numbers.format(progress.done), numbers.format(goal.target)),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Meter(progress.fraction)
        Spacer(Modifier.height(8.dp))
        val status = if (progress.isMet) {
            stringResource(MR.strings.stats_goal_met)
        } else {
            val left = when (goal.measure) {
                GoalMeasure.MINUTES -> time(progress.remaining * 60_000L)
                GoalMeasure.CHAPTERS -> numbers.format(progress.remaining)
            }
            listOfNotNull(
                stringResource(MR.strings.stats_goal_to_go, left),
                when {
                    goal.period == GoalPeriod.DAY -> null
                    progress.isOnTrack -> stringResource(MR.strings.stats_goal_on_track)
                    else -> stringResource(MR.strings.stats_goal_behind)
                },
            ).joinToString(" · ")
        }
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun goalTitle(period: GoalPeriod) = when (period) {
    GoalPeriod.DAY -> MR.strings.stats_goal_day
    GoalPeriod.WEEK -> MR.strings.stats_goal_week
    GoalPeriod.MONTH -> MR.strings.stats_goal_month
    GoalPeriod.YEAR -> MR.strings.stats_goal_year
}

@Composable
private fun GoalDialog(current: ReadingGoal?, onDismiss: () -> Unit, onSave: (ReadingGoal?) -> Unit) {
    var period by remember { mutableStateOf(current?.period ?: GoalPeriod.WEEK) }
    var measure by remember { mutableStateOf(current?.measure ?: GoalMeasure.CHAPTERS) }
    var target by remember { mutableStateOf(current?.target?.toString() ?: "") }
    val targetNumber = target.toIntOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.stats_goal_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(MR.strings.stats_goal_each), style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    options = listOf(
                        GoalPeriod.DAY to MR.strings.stats_goal_period_day,
                        GoalPeriod.WEEK to MR.strings.stats_goal_period_week,
                        GoalPeriod.MONTH to MR.strings.stats_goal_period_month,
                        GoalPeriod.YEAR to MR.strings.stats_goal_period_year,
                    ),
                    selected = period,
                    onSelect = { period = it },
                )
                Text(stringResource(MR.strings.stats_goal_measure), style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    options = listOf(
                        GoalMeasure.MINUTES to MR.strings.stats_goal_minutes,
                        GoalMeasure.CHAPTERS to MR.strings.stats_goal_chapters,
                    ),
                    selected = measure,
                    onSelect = { measure = it },
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { value -> target = value.filter { it.isDigit() }.take(6) },
                    label = { Text(stringResource(MR.strings.stats_goal_target)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(ReadingGoal(period, measure, targetNumber ?: return@TextButton)) }, enabled = targetNumber != null) {
                Text(stringResource(MR.strings.save))
            }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onSave(null) }) { Text(stringResource(MR.strings.remove)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.cancel)) }
            }
        },
    )
}

/**
 * A day's reading sessions, each with the series' cover, and the chapter, when, how long and how many pages.
 * Tapping one opens its series, if it's still in the app, and each can be deleted, such as one from a reader
 * left open.
 */
@Composable
private fun DaySessionsDialog(
    date: LocalDate,
    sessions: List<ReadingSession>,
    chapterName: (ReadingSession) -> String?,
    mangaFor: (ReadingSession) -> Manga?,
    onOpen: (Manga) -> Unit,
    onDelete: (ReadingSession) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()) }
    var deleting by remember { mutableStateOf<ReadingSession?>(null) }

    deleting?.let { session ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(MR.strings.stats_delete_session_title)) },
            text = {
                Text(stringResource(MR.strings.stats_delete_session_body, context.formatReadTime(session.duration), session.mangaTitle))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(session)
                        deleting = null
                    },
                ) { Text(stringResource(MR.strings.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(MR.strings.cancel)) } },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dateFormat.format(date)) },
        text = {
            if (sessions.isEmpty()) {
                Text(stringResource(MR.strings.stats_day_no_sessions))
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(sessions, key = { it.id ?: it.startedAt }) { session ->
                        SessionRow(
                            session = session,
                            manga = mangaFor(session),
                            chapterName = chapterName(session),
                            onOpen = onOpen,
                            onDelete = { deleting = session },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.close)) } },
    )
}

@Composable
private fun SessionRow(
    session: ReadingSession,
    manga: Manga?,
    chapterName: String?,
    onOpen: (Manga) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()) }
    val chapterNumberFormat = remember { java.text.DecimalFormat("#.###") }
    val started = Instant.ofEpochMilli(session.startedAt).atZone(zone)
    // The number is only a fallback for series no longer in the app, as volumes don't always have a real one
    val chapter = chapterName ?: session.chapterNumber.takeIf { it > 0 }?.let { number ->
        stringResource(MR.strings.chapter_, chapterNumberFormat.format(number))
    }
    val details = listOfNotNull(
        stringResource(MR.strings.stats_session_when, timeFormat.format(started), context.formatReadTime(session.duration)),
        session.pagesRead.takeIf { it > 0 }?.let { pluralStringResource(MR.plurals.pages_plural, it, it) },
    ).joinToString(" · ")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (manga != null) Modifier.clickable { onOpen(manga) } else Modifier)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        MangaCover(
            data = manga,
            ratio = MangaCoverRatio.BOOK,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.width(48.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                session.mangaTitle,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            chapter?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (session.completed) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = stringResource(MR.strings.stats_session_finished),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(MR.strings.stats_delete_session))
        }
    }
}

@Composable
private fun <T> ChoiceRow(options: List<Pair<T, dev.icerock.moko.resources.StringResource>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
            ) {
                Text(stringResource(label), maxLines = 1)
            }
        }
    }
}

@Composable
private fun YearInReviewCard(year: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(MR.strings.stats_year_in_review, year),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    stringResource(MR.strings.stats_year_in_review_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun PeriodSelector(period: StatsPeriod, onPeriodChange: (StatsPeriod) -> Unit) {
    val options = listOf(
        StatsPeriod.WEEK to MR.strings.stats_period_week,
        StatsPeriod.MONTH to MR.strings.stats_period_month,
        StatsPeriod.YEAR to MR.strings.stats_period_year,
        StatsPeriod.ALL to MR.strings.stats_period_all,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = option == period,
                onClick = { onPeriodChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
            ) {
                Text(stringResource(label), maxLines = 1)
            }
        }
    }
}

private fun LazyListScope.overview(stats: ReadingStats, time: (Long) -> String) {
    item(key = "overview") {
        val numbers = NumberFormat.getIntegerInstance()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(MR.strings.stats_time_read), time(stats.totalTime), Modifier.fillMaxWidth(), hero = true)
            TileRow(
                stringResource(MR.strings.stats_chapters_finished) to numbers.format(stats.chaptersFinished),
                stringResource(MR.strings.stats_pages_read) to numbers.format(stats.pagesRead),
            )
            TileRow(
                stringResource(MR.strings.stats_series_read) to numbers.format(stats.seriesRead),
                stringResource(MR.strings.stats_sessions) to numbers.format(stats.sessionCount),
            )
            TileRow(
                stringResource(MR.strings.stats_current_streak) to
                    pluralStringResource(MR.plurals.stats_days, stats.currentStreak, stats.currentStreak) +
                    StreakTier.of(stats.currentStreak)?.emoji?.let { " $it" }.orEmpty(),
                stringResource(MR.strings.stats_longest_streak) to
                    pluralStringResource(MR.plurals.stats_days, stats.longestStreak, stats.longestStreak),
            )
        }
    }
}

@Composable
internal fun TileRow(first: Pair<String, String>, second: Pair<String, String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(first.first, first.second, Modifier.weight(1f))
        StatTile(second.first, second.second, Modifier.weight(1f))
    }
}

private fun LazyListScope.timeSection(
    stats: ReadingStats,
    time: (Long) -> String,
    context: Context,
    onOpenDay: (LocalDate) -> Unit,
) {
    item(key = "time_header") { SectionHeader(stringResource(MR.strings.stats_section_time)) }

    item(key = "timeline") {
        val locale = Locale.getDefault()
        val unit = stats.timelineUnit
        val labelFormat = remember(unit, stats.period) {
            when {
                unit == TimelineUnit.YEAR -> DateTimeFormatter.ofPattern("yyyy", locale)
                unit == TimelineUnit.MONTH && stats.period == StatsPeriod.ALL -> DateTimeFormatter.ofPattern("MMM yyyy", locale)
                unit == TimelineUnit.MONTH -> DateTimeFormatter.ofPattern("MMMM yyyy", locale)
                else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            }
        }
        val axisFormat = remember(unit, stats.period) {
            when {
                unit == TimelineUnit.YEAR -> DateTimeFormatter.ofPattern("yyyy", locale)
                unit == TimelineUnit.MONTH -> DateTimeFormatter.ofPattern("MMM", locale)
                stats.period == StatsPeriod.WEEK -> DateTimeFormatter.ofPattern("EEE", locale)
                else -> DateTimeFormatter.ofPattern("d MMM", locale)
            }
        }
        val bars = stats.timeline
        val axisEvery = when {
            bars.size <= 12 -> 1
            bars.size <= 24 -> 3
            else -> 7
        }
        val title = when (unit) {
            TimelineUnit.DAY -> MR.strings.stats_time_per_day
            TimelineUnit.MONTH -> MR.strings.stats_time_per_month
            TimelineUnit.YEAR -> MR.strings.stats_time_per_year
        }
        Card {
            CardTitle(stringResource(title))
            ColumnChart(
                values = bars.map { it.time },
                labels = bars.map { labelFormat.format(it.start) },
                // Count back from the latest bar, so today or this month is always labelled
                axisLabel = { index -> if ((bars.lastIndex - index) % axisEvery == 0) axisFormat.format(bars[index].start) else null },
                formatValue = time,
                summary = stringResource(MR.strings.stats_time_read) + " · " + time(stats.totalTime),
            )
        }
    }

    item(key = "calendar") {
        val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()) }
        val today = remember { LocalDate.now(ZoneId.systemDefault()) }
        Card {
            CardTitle(stringResource(MR.strings.stats_reading_calendar))
            ReadingCalendar(
                readingDays = stats.readingDays,
                today = today,
                formatDate = { dateFormat.format(it) },
                formatValue = time,
                summary = stringResource(MR.strings.stats_current_streak) + " · " +
                    context.getString(MR.plurals.stats_days, stats.currentStreak, stats.currentStreak),
                onOpenDay = onOpenDay,
            )
        }
    }

    item(key = "time_of_day") {
        Card {
            CardTitle(stringResource(MR.strings.stats_time_of_day))
            val hourLabel = remember { DateTimeFormatter.ofPattern("HH:00", Locale.getDefault()) }
            val hours = (0 until 24).map { java.time.LocalTime.of(it, 0) }
            val peak = stats.hourOfDay.indices.maxBy { stats.hourOfDay[it] }
            ColumnChart(
                values = stats.hourOfDay,
                labels = hours.map { hourLabel.format(it) },
                axisLabel = { if (it % 6 == 0) hourLabel.format(hours[it]) else null },
                formatValue = time,
                summary = hourLabel.format(hours[peak]) + " · " + time(stats.hourOfDay[peak]),
                chartHeight = 100.dp,
            )
        }
    }

    item(key = "day_of_week") {
        Card {
            CardTitle(stringResource(MR.strings.stats_day_of_week))
            val locale = Locale.getDefault()
            val days = ReadingStats.daysOfWeek
            val peak = stats.dayOfWeek.indices.maxBy { stats.dayOfWeek[it] }
            ColumnChart(
                values = stats.dayOfWeek,
                labels = days.map { it.getDisplayName(TextStyle.FULL, locale) },
                axisLabel = { days[it].getDisplayName(TextStyle.SHORT, locale) },
                formatValue = time,
                summary = days[peak].getDisplayName(TextStyle.FULL, locale) + " · " + time(stats.dayOfWeek[peak]),
                chartHeight = 100.dp,
            )
        }
    }

    item(key = "sessions") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(MR.strings.stats_average_session), time(stats.averageSession), Modifier.weight(1f))
            StatTile(stringResource(MR.strings.stats_longest_session), time(stats.longestSession), Modifier.weight(1f))
        }
    }
}

private fun LazyListScope.seriesSection(
    state: StatsScreenModel.State.Success,
    time: (Long) -> String,
    openManga: (Manga) -> Unit,
) {
    val stats = state.stats
    item(key = "series_header") { SectionHeader(stringResource(MR.strings.stats_section_series)) }

    item(key = "most_read") {
        Card {
            CardTitle(stringResource(MR.strings.stats_most_read))
            val top = stats.series.take(MOST_READ_LIMIT)
            val maxTime = top.firstOrNull()?.time?.takeIf { it > 0 } ?: 1L
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                top.forEachIndexed { index, series ->
                    SeriesRow(
                        rank = index + 1,
                        series = series,
                        entries = LocalContext.current.entriesRead(state, series),
                        manga = state.mangaByKey[series.key],
                        fraction = series.time.toFloat() / maxTime,
                        time = time,
                        onClick = openManga,
                    )
                }
            }
        }
    }

    if (state.finished.isNotEmpty()) {
        item(key = "finished") {
            Card {
                CardTitle(stringResource(MR.strings.stats_finished_series))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.finished.forEach { series ->
                        SeriesRow(
                            rank = null,
                            series = series,
                            entries = LocalContext.current.entriesRead(state, series),
                            manga = state.mangaByKey[series.key],
                            fraction = null,
                            time = time,
                            onClick = openManga,
                        )
                    }
                }
            }
        }
    }

    if (state.topGenres.isNotEmpty()) {
        item(key = "genres") {
            Card {
                CardTitle(stringResource(MR.strings.stats_top_genres))
                MeterList(rows = state.topGenres, formatValue = time)
            }
        }
    }
}

@Composable
internal fun SeriesRow(
    rank: Int?,
    series: SeriesStats,
    entries: String,
    manga: Manga?,
    fraction: Float?,
    time: (Long) -> String,
    onClick: (Manga) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (manga != null) Modifier.clickable { onClick(manga) } else Modifier),
    ) {
        rank?.let {
            Text(
                text = it.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
            )
        }
        MangaCover(
            data = manga,
            ratio = MangaCoverRatio.BOOK,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.width(40.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(series.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOf(stringResource(categoryName(series.category)), time(series.time), entries).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            fraction?.let {
                Spacer(Modifier.height(6.dp))
                Meter(it)
            }
        }
    }
}

private fun LazyListScope.typesSection(state: StatsScreenModel.State.Success, time: (Long) -> String) {
    val stats = state.stats
    item(key = "types_header") { SectionHeader(stringResource(MR.strings.stats_section_types)) }
    item(key = "types") {
        val context = LocalContext.current
        val numbers = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 } }
        Card {
            MeterList(
                rows = stats.types.map { stringResource(categoryName(it.category)) to it.time },
                formatValue = time,
                detail = { index ->
                    val type = stats.types[index]
                    listOfNotNull(
                        stats.series
                            .filter { it.category == type.category }
                            .flatMap { series -> series.finishedChapterUrls.map { state.entryKind(series, it) } }
                            .takeIf { it.isNotEmpty() }
                            ?.let { context.getString(MR.strings.stats_entries_read, ChapterUtil.entryCountText(context, it)) },
                        type.timePerChapter?.let { context.getString(MR.strings.stats_per_chapter, time(it)) },
                        type.pagesPerMinute?.let { context.getString(MR.strings.stats_pages_per_minute, numbers.format(it)) },
                    ).joinToString(" · ").ifEmpty { null }
                },
            )
        }
    }
}

private fun LazyListScope.milestonesSection(milestones: Milestones, time: (Long) -> String) {
    item(key = "milestones_header") { SectionHeader(stringResource(MR.strings.stats_section_milestones)) }

    if (milestones.next.isNotEmpty()) {
        item(key = "milestones_next") {
            val numbers = NumberFormat.getIntegerInstance()
            Card {
                CardTitle(stringResource(MR.strings.stats_milestones_next))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    milestones.next.take(NEXT_MILESTONES).forEach { next ->
                        Column {
                            Text(
                                stringResource(
                                    MR.strings.stats_milestone_progress,
                                    stringResource(next.kind.label()),
                                    numbers.format(next.current),
                                    numbers.format(next.value),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(6.dp))
                            Meter(next.fraction)
                        }
                    }
                }
            }
        }
    }

    item(key = "milestones_reached") {
        val context = LocalContext.current
        val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()) }
        var showAll by remember { mutableStateOf(false) }
        Card {
            CardTitle(stringResource(MR.strings.stats_milestones_reached))
            if (milestones.reached.isEmpty()) {
                Text(
                    stringResource(MR.strings.stats_milestones_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val shown = if (showAll) milestones.reached else milestones.reached.take(REACHED_MILESTONES)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { milestone ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$MILESTONE_EMOJI ${context.milestoneText(milestone)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            dateFormat.format(Instant.ofEpochMilli(milestone.reachedAt).atZone(ZoneId.systemDefault())),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (milestones.reached.size > REACHED_MILESTONES) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(stringResource(if (showAll) MR.strings.stats_calendar_show_less else MR.strings.stats_calendar_show_more))
                }
            }
        }
    }

    val records = milestones.records
    if (records.longestSession != null) {
        item(key = "records") {
            val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()) }
            val numbers = NumberFormat.getIntegerInstance()
            val rows = listOfNotNull(
                records.longestSession.let { session ->
                    val day = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                    Triple(
                        stringResource(MR.strings.stats_longest_session),
                        time(session.duration),
                        stringResource(MR.strings.stats_record_when, dateFormat.format(day), session.mangaTitle),
                    )
                },
                records.busiestDay?.let { (day, ms) ->
                    Triple(stringResource(MR.strings.stats_record_busiest_day), time(ms), dateFormat.format(day))
                },
                records.mostChaptersInADay?.let { (day, count) ->
                    Triple(stringResource(MR.strings.stats_record_most_chapters), numbers.format(count), dateFormat.format(day))
                },
                records.longestStreak?.let { (days, end) ->
                    Triple(
                        stringResource(MR.strings.stats_longest_streak),
                        pluralStringResource(MR.plurals.stats_days, days, days),
                        dateFormat.format(end.minusDays(days - 1L)) + " – " + dateFormat.format(end),
                    )
                },
            )
            Card {
                CardTitle(stringResource(MR.strings.stats_records))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { (label, value, detail) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(value, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.librarySection(
    library: StatsScreenModel.LibrarySummary,
    time: (Long) -> String,
    openManga: (Manga) -> Unit,
) {
    item(key = "library_header") { SectionHeader(stringResource(MR.strings.stats_section_library)) }
    item(key = "library_tiles") {
        val numbers = NumberFormat.getIntegerInstance()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TileRow(
                stringResource(MR.strings.stats_library_series) to numbers.format(library.seriesCount),
                stringResource(MR.strings.stats_unread_chapters) to numbers.format(library.unreadChapters),
            )
            library.backlogTime?.let {
                StatTile(stringResource(MR.strings.stats_backlog), time(it), Modifier.fillMaxWidth())
            }
        }
    }
    item(key = "library_status") {
        Card {
            CardTitle(stringResource(MR.strings.stats_by_status))
            MeterList(
                rows = library.byStatus.map { (status, count) -> stringResource(statusName(status)) to count.toLong() },
                formatValue = { NumberFormat.getIntegerInstance().format(it) },
            )
        }
    }
    item(key = "library_source") {
        Card {
            CardTitle(stringResource(MR.strings.stats_by_source))
            MeterList(
                rows = library.bySource.map { (name, count) -> name to count.toLong() },
                formatValue = { NumberFormat.getIntegerInstance().format(it) },
            )
        }
    }
    if (library.stalled.isNotEmpty()) {
        item(key = "library_stalled") {
            Card {
                CardTitle(stringResource(MR.strings.stats_stalled))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    library.stalled.forEach { manga ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { openManga(manga) },
                        ) {
                            MangaCover(data = manga, ratio = MangaCoverRatio.BOOK, shape = RoundedCornerShape(6.dp), modifier = Modifier.width(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(manga.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
internal fun Card(content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
internal fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
internal fun Message(title: String, body: String?) {
    Card {
        Text(title, style = MaterialTheme.typography.titleMedium)
        body?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun StatsScreenModel.State.Success.entryKind(series: SeriesStats, url: String): EntryKind =
    (entryKinds[series.key]?.of(url) ?: EntryKind.Chapter).forCategory(series.category)

/** A numbered chapter of a comic is an issue, even where Kavita files it under a volume. */
private fun EntryKind.forCategory(category: ReadingCategory): EntryKind =
    if (this == EntryKind.Chapter && category == ReadingCategory.COMIC) EntryKind.Issue else this

/** "3 issues read", or "0 volumes read" in the series' usual kind of entry when none were finished. */
private fun Context.entriesRead(state: StatsScreenModel.State.Success, series: SeriesStats): String {
    val kinds = series.finishedChapterUrls.map { state.entryKind(series, it) }
    val count = if (kinds.isEmpty()) {
        val usual = (state.entryKinds[series.key]?.usual ?: EntryKind.Chapter).forCategory(series.category)
        getString(usual.plural, 0, 0)
    } else {
        ChapterUtil.entryCountText(this, kinds)
    }
    return getString(MR.strings.stats_entries_read, count)
}

internal fun categoryName(category: ReadingCategory) = when (category) {
    ReadingCategory.COMIC -> MR.strings.comic
    ReadingCategory.MANGA -> MR.strings.manga
    ReadingCategory.WEBTOON -> MR.strings.webtoon
}

private fun statusName(status: Int) = when (status) {
    SManga.ONGOING -> MR.strings.ongoing
    SManga.COMPLETED -> MR.strings.completed
    SManga.LICENSED -> MR.strings.licensed
    SManga.PUBLISHING_FINISHED -> MR.strings.publishing_finished
    SManga.CANCELLED -> MR.strings.cancelled
    SManga.ON_HIATUS -> MR.strings.on_hiatus
    else -> MR.strings.unknown_status
}

/** "3h 20m", "45m" or "<1m". Hours keep counting past a day, which reads better for totals. */
fun Context.formatReadTime(ms: Long): String {
    val totalMinutes = ms / 60_000L
    if (totalMinutes < 1) return if (ms <= 0) getString(MR.strings.stats_duration_minutes, 0) else getString(MR.strings.stats_under_a_minute)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        getString(MR.strings.stats_duration_hours, hours.toInt(), minutes.toInt())
    } else {
        getString(MR.strings.stats_duration_minutes, minutes.toInt())
    }
}
