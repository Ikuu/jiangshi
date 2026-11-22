package yokai.presentation.stats

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.kavita.KavitaEntryType
import eu.kanade.tachiyomi.data.database.models.seriesType
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.EntryKind
import eu.kanade.tachiyomi.util.system.launchIO
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.update
import uy.kohesive.injekt.injectLazy
import yokai.domain.chapter.interactor.GetChapter
import yokai.domain.manga.interactor.GetLibraryManga
import yokai.domain.manga.interactor.GetManga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import java.time.temporal.WeekFields
import yokai.domain.stats.GoalProgress
import yokai.domain.stats.ReadingCategory
import yokai.domain.stats.ReadingGoal
import yokai.domain.stats.ReadingPace
import yokai.domain.stats.UnreadEntry
import eu.kanade.tachiyomi.data.track.kavita.KavitaApi
import yokai.domain.stats.YearInReview
import yokai.domain.stats.ReadingSession
import yokai.domain.stats.ReadingStats
import yokai.domain.stats.SeriesStats
import yokai.domain.stats.StatsPeriod
import yokai.domain.stats.interactor.GetReadingSessions

class StatsScreenModel : StateScreenModel<StatsScreenModel.State>(State.Loading) {

    private val getReadingSessions: GetReadingSessions by injectLazy()
    private val getLibraryManga: GetLibraryManga by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val getChapter: GetChapter by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val trackManager: TrackManager by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    private var sessions: List<ReadingSession> = emptyList()
    private var mangaByKey: Map<SeriesKey, Manga> = emptyMap()
    private var entryKinds: Map<SeriesKey, EntryKinds> = emptyMap()
    private var localKinds: Map<SeriesKey, Map<String, EntryKind>> = emptyMap()

    /** Pages in each entry of Kavita series, by Kavita chapter ID, kept for as long as the screen is open. */
    private var kavitaPages: Map<SeriesKey, Map<Int, Int>> = emptyMap()

    /** Entry types from the Kavita server, kept for as long as the screen is open. */
    private var kavitaKinds: Map<SeriesKey, Map<String, EntryKind>> = emptyMap()
    private var library: List<LibraryManga> = emptyList()
    private var librarySummary: LibrarySummary? = null

    /** Sets the reading goal, or clears it with null. */
    fun setGoal(goal: ReadingGoal?) {
        preferences.readingGoal().set(goal?.encode().orEmpty())
        val current = state.value as? State.Success ?: return
        mutableState.update { build(current.period) }
    }

    fun setPeriod(period: StatsPeriod) {
        val current = state.value as? State.Success ?: return
        if (current.period == period) return
        mutableState.update { build(period) }
    }

    /** Reloads everything, for coming back to the screen after reading. */
    fun refresh() {
        load((state.value as? State.Success)?.period ?: StatsPeriod.WEEK)
    }

    private fun load(period: StatsPeriod) {
        screenModelScope.launchIO {
            sessions = getReadingSessions.awaitAll()
            library = getLibraryManga.await().distinctBy { it.manga.id }
            val keys = sessions.map { SeriesKey(it.mangaSource, it.mangaUrl) }.toSet()
            mangaByKey = getManga.awaitAll()
                .associateBy { SeriesKey(it.source, it.url) }
                .filterKeys { it in keys }
            localKinds = mangaByKey.mapValues { (_, manga) ->
                manga.id?.let { getChapter.awaitAll(it, false) }.orEmpty().associate { it.url to ChapterUtil.entryKind(it) }
            }
            updateEntryKinds()
            librarySummary = librarySummary()
            mutableState.update { build(period) }
            fetchKavitaLayouts()
        }
    }

    /**
     * Chapter names don't always say whether an entry is a whole volume or an issue, so for Kavita series
     * ask the server, as the series page does. Local guesses show until it answers. The server also says
     * how many pages each entry has, so unread series in the library get a better time to clear them.
     */
    private suspend fun fetchKavitaLayouts() {
        val kavita = trackManager.kavita
        val unreadInLibrary = library.filter { it.unread > 0 }.map { SeriesKey(it.manga.source, it.manga.url) }
        val missing = (mangaByKey.keys + unreadInLibrary)
            .filter { it !in kavitaPages && kavita.accept(sourceManager.getOrStub(it.source)) }
        if (missing.isEmpty()) return

        val fetched = coroutineScope {
            missing.map { key ->
                async {
                    try {
                        key to kavita.api.getSeriesLayout(key.url).chapters
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Logger.w(e) { "Could not get Kavita volumes for ${key.url}" }
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (fetched.isEmpty()) return

        kavitaPages = kavitaPages + fetched.associate { (key, chapters) -> key to chapters.mapValues { it.value.pages } }
        kavitaKinds = kavitaKinds + fetched.filter { it.first in localKinds }.associate { (key, chapters) ->
            key to localKinds[key].orEmpty().keys.mapNotNull { url ->
                val id = url.substringAfter("/Chapter/").substringBefore("_").substringBefore("?").toIntOrNull()
                val type = id?.let { chapters[it]?.type } ?: return@mapNotNull null
                url to when (type) {
                    KavitaEntryType.Volume -> EntryKind.Volume
                    KavitaEntryType.Chapter -> EntryKind.Chapter
                    KavitaEntryType.Issue -> EntryKind.Issue
                    KavitaEntryType.Special -> EntryKind.Special
                }
            }.toMap()
        }
        updateEntryKinds()
        librarySummary = librarySummary()
        val period = (state.value as? State.Success)?.period ?: return
        mutableState.update { build(period) }
    }

    private fun updateEntryKinds() {
        entryKinds = localKinds.mapValues { (key, local) ->
            val kinds = local + kavitaKinds[key].orEmpty()
            EntryKinds(
                byUrl = kinds,
                usual = kinds.values.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: EntryKind.Chapter,
            )
        }
    }

    private fun build(period: StatsPeriod): State.Success {
        val stats = ReadingStats.calculate(sessions, period, Instant.now(), ZoneId.systemDefault())
        val libraryById = library.associateBy { it.manga.id }

        val finished = stats.series.filter { series ->
            val manga = mangaByKey[series.key] ?: return@filter false
            val libraryManga = libraryById[manga.id] ?: return@filter false
            series.chaptersFinished > 0 &&
                libraryManga.totalChapters > 0 && libraryManga.unread == 0 &&
                manga.status in FINISHED_STATUSES
        }

        val genres = mutableMapOf<String, Long>()
        stats.series.forEach { series ->
            mangaByKey[series.key]?.genre
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.distinctBy { it.lowercase(Locale.getDefault()) }
                ?.forEach { genre -> genres[genre] = (genres[genre] ?: 0L) + series.time }
        }

        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        // The streak carries over from yesterday until today is over, so it's at risk until something's read
        val streakAtRisk = stats.currentStreak > 0 && today !in stats.readingDays

        return State.Success(
            period = period,
            stats = stats,
            goal = ReadingGoal.decode(preferences.readingGoal().get())
                ?.progress(sessions, now, zone, WeekFields.of(Locale.getDefault()).firstDayOfWeek),
            streakAtRisk = streakAtRisk,
            years = YearInReview.years(sessions, zone),
            hasAnyReading = sessions.isNotEmpty(),
            mangaByKey = mangaByKey,
            entryKinds = entryKinds,
            finished = finished,
            topGenres = genres.entries.sortedByDescending { it.value }.take(TOP_GENRES).map { it.key to it.value },
            library = librarySummary,
        )
    }

    private suspend fun librarySummary(): LibrarySummary? {
        if (library.isEmpty()) return null

        // Estimate the backlog at each series' pace, going by pages where Kavita or the reader knows them
        val sessionsBySeries = sessions.groupBy { SeriesKey(it.mangaSource, it.mangaUrl) }
        val estimates = library.filter { it.unread > 0 }.mapNotNull { item ->
            val manga = item.manga
            val key = SeriesKey(manga.source, manga.url)
            val pages = kavitaPages[key]
            val entries = manga.id?.let { getChapter.awaitUnread(it, true) }.orEmpty()
                .distinctBy { if (it.chapter_number >= 0f) it.chapter_number.toString() else it.url }
                .map { UnreadEntry.of(it.last_page_read, it.pages_left, pages?.get(KavitaApi.chapterId(it.url))) }
            val category = ReadingCategory.of(manga.seriesType(sourceManager = sourceManager))
            ReadingPace.forSeries(sessionsBySeries[key].orEmpty(), category, sessions).timeLeft(entries)
        }
        val backlog = estimates.takeIf { it.isNotEmpty() }?.sum()

        val stalledBefore = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(STALLED_DAYS)
        return LibrarySummary(
            seriesCount = library.size,
            unreadChapters = library.sumOf { it.unread },
            backlogTime = backlog,
            byStatus = library.groupingBy { it.manga.status }.eachCount().entries
                .sortedByDescending { it.value }
                .map { it.key to it.value },
            bySource = library.groupingBy { it.manga.source }.eachCount().entries
                .sortedByDescending { it.value }
                .map { sourceManager.getOrStub(it.key).name to it.value },
            stalled = library
                .filter { it.hasRead && it.unread > 0 && it.lastRead in 1 until stalledBefore }
                .sortedByDescending { it.lastRead }
                .take(STALLED_LIMIT)
                .map { it.manga },
        )
    }

    data class SeriesKey(val source: Long, val url: String)

    /** Whether a series' entries are chapters, issues or volumes, so counts use the right word. */
    data class EntryKinds(val byUrl: Map<String, EntryKind>, val usual: EntryKind) {
        fun of(url: String): EntryKind = byUrl[url] ?: usual
    }

    @Immutable
    data class LibrarySummary(
        val seriesCount: Int,
        val unreadChapters: Int,
        /** Null until there's enough reading to know a pace, or nothing is unread. */
        val backlogTime: Long?,
        val byStatus: List<Pair<Int, Int>>,
        val bySource: List<Pair<String, Int>>,
        val stalled: List<Manga>,
    )

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val period: StatsPeriod,
            val stats: ReadingStats,
            /** The reading goal and how far along it is, or null without one. */
            val goal: GoalProgress?,
            /** There's a streak going, and nothing read yet today to keep it. */
            val streakAtRisk: Boolean,
            /** Years with any reading, newest first, for the year in review. */
            val years: List<Int>,
            val hasAnyReading: Boolean,
            val mangaByKey: Map<SeriesKey, Manga>,
            val entryKinds: Map<SeriesKey, EntryKinds>,
            val finished: List<SeriesStats>,
            val topGenres: List<Pair<String, Long>>,
            val library: LibrarySummary?,
        ) : State
    }

    companion object {
        private const val TOP_GENRES = 8
        private const val STALLED_DAYS = 30L
        private const val STALLED_LIMIT = 5
        private val FINISHED_STATUSES = setOf(SManga.COMPLETED, SManga.PUBLISHING_FINISHED, SManga.CANCELLED)
    }
}

val SeriesStats.key: StatsScreenModel.SeriesKey get() = StatsScreenModel.SeriesKey(mangaSource, mangaUrl)
