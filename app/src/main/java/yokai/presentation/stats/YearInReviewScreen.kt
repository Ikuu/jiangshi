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
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.EntryKind
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
import yokai.domain.manga.interactor.GetManga
import yokai.domain.stats.ReadingCategory
import yokai.domain.stats.ReadingSession
import yokai.domain.stats.YearInReview
import yokai.domain.stats.interactor.GetReadingSessions
import yokai.i18n.MR
import yokai.presentation.AppBarType
import yokai.presentation.YokaiScaffold
import yokai.presentation.core.enterAlwaysAppBarScrollBehavior
import yokai.util.Screen
import yokai.util.lang.getString

private val ContentMaxWidth = 720.dp
private const val TOP_SERIES = 5
private const val TOP_GENRES = 5

/** One year's reading, with arrows to step through the other years read. */
class YearInReviewScreen(private val initialYear: Int) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val backPress = LocalBackPress.currentOrThrow
        val screenModel = rememberScreenModel { YearInReviewScreenModel(initialYear) }
        val state by screenModel.state.collectAsState()
        val listState = rememberLazyListState()
        val success = state as? YearInReviewScreenModel.State.Success

        YokaiScaffold(
            onNavigationIconClicked = { if (navigator.canPop) navigator.pop() else backPress() },
            title = stringResource(MR.strings.stats_year_in_review, success?.review?.year ?: initialYear),
            appBarType = AppBarType.SMALL,
            scrollBehavior = enterAlwaysAppBarScrollBehavior(
                canScroll = { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 },
            ),
            actions = {
                if (success != null) {
                    val index = success.years.indexOf(success.review.year)
                    // Years run newest first, so the previous year is further along the list
                    IconButton(onClick = { screenModel.showYear(success.years[index + 1]) }, enabled = index < success.years.lastIndex) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(MR.strings.year_review_previous))
                    }
                    IconButton(onClick = { screenModel.showYear(success.years[index - 1]) }, enabled = index > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(MR.strings.year_review_next))
                    }
                }
            },
        ) { innerPadding ->
            when (val current = state) {
                YearInReviewScreenModel.State.Loading -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                    CircularProgressIndicator()
                }
                is YearInReviewScreenModel.State.Success -> YearInReviewContent(current, listState, innerPadding)
            }
        }
    }
}

@Composable
private fun YearInReviewContent(
    state: YearInReviewScreenModel.State.Success,
    listState: androidx.compose.foundation.lazy.LazyListState,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val router = LocalRouter.current
    val review = state.review
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
                item(key = "empty") { Message(stringResource(MR.strings.year_review_empty, review.year), null) }
                return@LazyColumn
            }

            item(key = "overview") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(stringResource(MR.strings.stats_time_read), time(review.totalTime), Modifier.fillMaxWidth(), hero = true)
                    review.previousYearTime?.let { previous ->
                        val difference = review.totalTime - previous
                        Message(
                            stringResource(
                                if (difference >= 0) MR.strings.year_review_more_than else MR.strings.year_review_less_than,
                                time(kotlin.math.abs(difference)),
                                review.year - 1,
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
                        stringResource(MR.strings.year_review_days_read) to numbers.format(review.daysRead),
                        stringResource(MR.strings.stats_longest_streak) to
                            pluralStringResource(MR.plurals.stats_days, review.longestStreak, review.longestStreak),
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

            item(key = "months") {
                val labelFormat = remember { DateTimeFormatter.ofPattern("MMMM", locale) }
                val axisFormat = remember { DateTimeFormatter.ofPattern("MMMMM", locale) }
                Card {
                    CardTitle(stringResource(MR.strings.stats_time_per_month))
                    ColumnChart(
                        values = review.months.map { it.time },
                        labels = review.months.map { labelFormat.format(it.start) },
                        axisLabel = { axisFormat.format(review.months[it].start) },
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
                            val usual = if (series.category == ReadingCategory.COMIC) EntryKind.Issue else EntryKind.Chapter
                            SeriesRow(
                                rank = index + 1,
                                series = series,
                                entries = context.getString(
                                    MR.strings.stats_entries_read,
                                    context.getString(usual.plural, series.chaptersFinished, series.chaptersFinished),
                                ),
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

class YearInReviewScreenModel(initialYear: Int) : StateScreenModel<YearInReviewScreenModel.State>(State.Loading) {

    private val getReadingSessions: GetReadingSessions by injectLazy()
    private val getManga: GetManga by injectLazy()

    private var sessions: List<ReadingSession> = emptyList()
    private var mangaByKey: Map<StatsScreenModel.SeriesKey, Manga> = emptyMap()
    private val zone = ZoneId.systemDefault()

    init {
        screenModelScope.launchIO {
            sessions = getReadingSessions.awaitAll()
            val keys = sessions.map { StatsScreenModel.SeriesKey(it.mangaSource, it.mangaUrl) }.toSet()
            mangaByKey = getManga.awaitAll().associateBy { StatsScreenModel.SeriesKey(it.source, it.url) }.filterKeys { it in keys }
            mutableState.update { build(initialYear) }
        }
    }

    fun showYear(year: Int) {
        if (state.value is State.Success) mutableState.update { build(year) }
    }

    private fun build(year: Int): State.Success {
        val review = YearInReview.calculate(sessions, year, zone)
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
            years = YearInReview.years(sessions, zone).ifEmpty { listOf(year) },
            mangaByKey = mangaByKey,
            topGenres = genres.entries.sortedByDescending { it.value }.take(TOP_GENRES).map { it.key to it.value },
        )
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val review: YearInReview,
            /** Years with any reading, newest first. */
            val years: List<Int>,
            val mangaByKey: Map<StatsScreenModel.SeriesKey, Manga>,
            val topGenres: List<Pair<String, Long>>,
        ) : State
    }
}
