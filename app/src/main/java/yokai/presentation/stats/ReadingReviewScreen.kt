package yokai.presentation.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import dev.icerock.moko.resources.compose.pluralStringResource
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.compose.LocalBackPress
import eu.kanade.tachiyomi.util.compose.LocalRouter
import eu.kanade.tachiyomi.util.compose.currentOrThrow
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import java.text.NumberFormat
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.flow.update
import uy.kohesive.injekt.injectLazy
import yokai.domain.chapter.interactor.GetChapter
import yokai.domain.manga.interactor.GetManga
import yokai.domain.stats.ReadingSession
import yokai.domain.stats.ReadingReview
import yokai.domain.stats.ReviewPeriod
import yokai.domain.stats.interactor.GetFinishedSeries
import yokai.domain.stats.interactor.GetReadingSessions
import yokai.domain.stats.FinishedSeries
import yokai.i18n.MR
import yokai.presentation.AppBarType
import yokai.presentation.YokaiScaffold
import yokai.presentation.core.enterAlwaysAppBarScrollBehavior
import yokai.util.Screen

private val ContentMaxWidth = 720.dp
private const val TOP_SERIES = 5
private const val TOP_GENRES = 5

/** The name of [period], such as "2026" or "November 2026". */
@Composable
fun reviewPeriodName(period: ReviewPeriod): String {
    val locale = Locale.getDefault()
    return when (period) {
        is ReviewPeriod.Year -> period.year.toString()
        is ReviewPeriod.Month -> remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }.format(period.month)
    }
}

/** The title for [period]'s review, such as "2026 in review". */
@Composable
fun reviewTitle(period: ReviewPeriod): String = when (period) {
    is ReviewPeriod.Year -> stringResource(MR.strings.stats_year_in_review, period.year)
    is ReviewPeriod.Month -> stringResource(MR.strings.stats_month_in_review, reviewPeriodName(period))
}

/** One year's or month's reading, with arrows to step through the other years or months read. */
class ReadingReviewScreen(private val initialPeriod: ReviewPeriod) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val backPress = LocalBackPress.currentOrThrow
        val screenModel = rememberScreenModel { ReadingReviewScreenModel(initialPeriod) }
        val state by screenModel.state.collectAsState()
        val listState = rememberLazyListState()
        val success = state as? ReadingReviewScreenModel.State.Success
        val period = success?.review?.period ?: initialPeriod

        YokaiScaffold(
            onNavigationIconClicked = { if (navigator.canPop) navigator.pop() else backPress() },
            title = reviewTitle(period),
            appBarType = AppBarType.SMALL,
            scrollBehavior = enterAlwaysAppBarScrollBehavior(
                canScroll = { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 },
            ),
            actions = {
                if (success != null) {
                    val periods = success.periods
                    val index = periods.indexOf(period)
                    val isYear = period is ReviewPeriod.Year
                    // Periods run newest first, so the previous one is further along the list
                    IconButton(onClick = { screenModel.show(periods[index + 1]) }, enabled = index < periods.lastIndex) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            stringResource(if (isYear) MR.strings.year_review_previous else MR.strings.month_review_previous),
                        )
                    }
                    IconButton(onClick = { screenModel.show(periods[index - 1]) }, enabled = index > 0) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            stringResource(if (isYear) MR.strings.year_review_next else MR.strings.month_review_next),
                        )
                    }
                }
            },
        ) { innerPadding ->
            when (val current = state) {
                ReadingReviewScreenModel.State.Loading -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                    CircularProgressIndicator()
                }
                is ReadingReviewScreenModel.State.Success -> ReadingReviewContent(current, listState, innerPadding)
            }
        }
    }
}

@Composable
private fun ReadingReviewContent(
    state: ReadingReviewScreenModel.State.Success,
    listState: androidx.compose.foundation.lazy.LazyListState,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val router = LocalRouter.current
    val review = state.review
    val period = review.period
    val periodName = reviewPeriodName(period)
    val previousName = reviewPeriodName(period.previous)
    val time = remember(context) { { ms: Long -> context.formatReadTime(ms) } }
    val numbers = remember { NumberFormat.getIntegerInstance() }
    val locale = Locale.getDefault()
    val openManga: (Manga) -> Unit = { manga -> router?.pushController(MangaDetailsController(manga).withFadeTransaction()) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 8.dp,
                // Clear of the bottom navigation when Statistics is a tab
                bottom = contentPadding.calculateBottomPadding() + (LocalStatsTab.current?.bottomPadding ?: 0.dp) + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            if (review.isEmpty) {
                item(key = "empty") { Message(stringResource(MR.strings.year_review_empty, periodName), null) }
                return@LazyColumn
            }

            item(key = "overview") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(stringResource(MR.strings.stats_time_read), time(review.totalTime), Modifier.fillMaxWidth(), hero = true)
                    review.previousTime?.let { previous ->
                        val difference = review.totalTime - previous
                        Message(
                            stringResource(
                                if (difference >= 0) MR.strings.year_review_more_than else MR.strings.year_review_less_than,
                                time(kotlin.math.abs(difference)),
                                previousName,
                            ),
                            null,
                        )
                    }
                    TileRow(
                        stringResource(MR.strings.stats_chapters_finished) to numbers.format(review.chaptersFinished),
                        stringResource(MR.strings.stats_pages_read) to numbers.format(review.pagesRead),
                    )
                    TileRow(
                        stringResource(MR.strings.stats_series_read) to numbers.format(review.series.size),
                        stringResource(MR.strings.year_review_series_started) to numbers.format(review.seriesStarted),
                    )
                    TileRow(
                        stringResource(MR.strings.stats_series_finished) to numbers.format(review.seriesFinished),
                        stringResource(MR.strings.year_review_days_read) to numbers.format(review.daysRead),
                    )
                    StatTile(
                        stringResource(MR.strings.stats_longest_streak),
                        pluralStringResource(MR.plurals.stats_days, review.longestStreak, review.longestStreak),
                        Modifier.fillMaxWidth(),
                    )
                }
            }

            item(key = "highlights") {
                val monthFormat = remember { DateTimeFormatter.ofPattern("MMMM", locale) }
                val dayFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
                val hourFormat = remember { DateTimeFormatter.ofPattern("HH:00", locale) }
                Card {
                    MeterList(
                        rows = listOfNotNull(
                            review.busiestMonth?.let { stringResource(MR.strings.year_review_busiest_month) + " · " + monthFormat.format(it.start) to it.time },
                            review.busiestDay?.let { (day, dayTime) -> stringResource(MR.strings.year_review_busiest_day) + " · " + dayFormat.format(day) to dayTime },
                        ),
                        formatValue = time,
                    )
                    review.favouriteHour?.let { hour ->
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
                        TileRow(
                            stringResource(MR.strings.year_review_favourite_time) to hourFormat.format(LocalTime.of(hour, 0)),
                            stringResource(MR.strings.stats_sessions) to numbers.format(review.sessionCount),
                        )
                    }
                }
            }

            item(key = "timeline") {
                val isYear = period is ReviewPeriod.Year
                val labelFormat = remember(isYear) {
                    if (isYear) DateTimeFormatter.ofPattern("MMMM", locale) else DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
                }
                val axisFormat = remember { DateTimeFormatter.ofPattern("MMMMM", locale) }
                Card {
                    CardTitle(stringResource(if (isYear) MR.strings.stats_time_per_month else MR.strings.stats_time_per_day))
                    ColumnChart(
                        values = review.timeline.map { it.time },
                        labels = review.timeline.map { labelFormat.format(it.start) },
                        // Days of a month are labelled every week, starting from the 1st
                        axisLabel = { index ->
                            val start = review.timeline[index].start
                            when {
                                isYear -> axisFormat.format(start)
                                index % 7 == 0 -> start.dayOfMonth.toString()
                                else -> null
                            }
                        },
                        formatValue = time,
                        summary = stringResource(MR.strings.stats_time_read) + " · " + time(review.totalTime),
                    )
                }
            }

            item(key = "top_series") {
                Card {
                    CardTitle(stringResource(MR.strings.year_review_top_series))
                    val top = review.series.take(TOP_SERIES)
                    val maxTime = top.firstOrNull()?.time?.takeIf { it > 0 } ?: 1L
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        top.forEachIndexed { index, series ->
                            SeriesRow(
                                rank = index + 1,
                                series = series,
                                entries = context.entriesRead(state.entryKinds, series),
                                manga = state.mangaByKey[series.key],
                                fraction = series.time.toFloat() / maxTime,
                                time = time,
                                onClick = openManga,
                            )
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

            if (review.types.size > 1) {
                item(key = "types") {
                    Card {
                        CardTitle(stringResource(MR.strings.stats_section_types))
                        MeterList(rows = review.types.map { stringResource(categoryName(it.category)) to it.time }, formatValue = time)
                    }
                }
            }
        }
    }
}

class ReadingReviewScreenModel(initialPeriod: ReviewPeriod) : StateScreenModel<ReadingReviewScreenModel.State>(State.Loading) {

    private val getReadingSessions: GetReadingSessions by injectLazy()
    private val getFinishedSeries: GetFinishedSeries by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val getChapter: GetChapter by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val trackManager: TrackManager by injectLazy()

    private var sessions: List<ReadingSession> = emptyList()
    private var finished: List<FinishedSeries> = emptyList()
    private var mangaByKey: Map<StatsScreenModel.SeriesKey, Manga> = emptyMap()
    private var entryKinds: Map<StatsScreenModel.SeriesKey, EntryKinds> = emptyMap()
    private val zone = ZoneId.systemDefault()

    init {
        screenModelScope.launchIO {
            sessions = getReadingSessions.awaitAll()
            finished = getFinishedSeries.await(sessions)
            val keys = sessions.map { StatsScreenModel.SeriesKey(it.mangaSource, it.mangaUrl) }.toSet()
            mangaByKey = getManga.awaitAll().associateBy { StatsScreenModel.SeriesKey(it.source, it.url) }.filterKeys { it in keys }
            val localKinds = mangaByKey.mapValues { (_, manga) ->
                manga.id?.let { getChapter.awaitAll(it, false) }.orEmpty().associate { it.url to ChapterUtil.entryKind(it) }
            }
            entryKinds = localKinds.mapValues { (_, local) -> EntryKinds.from(local, emptyMap()) }
            mutableState.update { build(initialPeriod) }

            // Chapter names don't always say whether an entry is a whole volume, so ask Kavita, showing the guesses until it answers
            val layouts = fetchKavitaLayouts(localKinds.keys, trackManager, sourceManager)
            if (layouts.isEmpty()) return@launchIO
            entryKinds = localKinds.mapValues { (key, local) ->
                EntryKinds.from(local, layouts[key]?.let { kavitaEntryKinds(local.keys, it) }.orEmpty())
            }
            mutableState.update { current -> (current as? State.Success)?.copy(entryKinds = entryKinds) ?: current }
        }
    }

    fun show(period: ReviewPeriod) {
        if (state.value is State.Success) mutableState.update { build(period) }
    }

    private fun build(period: ReviewPeriod): State.Success {
        val review = ReadingReview.calculate(sessions, period, zone, finished)
        val genres = mutableMapOf<String, Long>()
        review.series.forEach { series ->
            mangaByKey[series.key]?.genre
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.distinctBy { it.lowercase(Locale.getDefault()) }
                ?.forEach { genre -> genres[genre] = (genres[genre] ?: 0L) + series.time }
        }
        return State.Success(
            review = review,
            periods = ReadingReview.periodsLike(period, sessions, zone).ifEmpty { listOf(period) },
            mangaByKey = mangaByKey,
            entryKinds = entryKinds,
            topGenres = genres.entries.sortedByDescending { it.value }.take(TOP_GENRES).map { it.key to it.value },
        )
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val review: ReadingReview,
            /** Years or months with any reading, the same kind as the review's, newest first. */
            val periods: List<ReviewPeriod>,
            val mangaByKey: Map<StatsScreenModel.SeriesKey, Manga>,
            val entryKinds: Map<StatsScreenModel.SeriesKey, EntryKinds>,
            val topGenres: List<Pair<String, Long>>,
        ) : State
    }
}
