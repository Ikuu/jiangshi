package yokai.presentation.stats

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.seriesType
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.system.launchIO
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.update
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.interactor.GetLibraryManga
import yokai.domain.manga.interactor.GetManga
import yokai.domain.stats.ReadingCategory
import yokai.domain.stats.ReadingSession
import yokai.domain.stats.ReadingStats
import yokai.domain.stats.SeriesStats
import yokai.domain.stats.StatsPeriod
import yokai.domain.stats.interactor.GetReadingSessions

class StatsScreenModel : StateScreenModel<StatsScreenModel.State>(State.Loading) {

    private val getReadingSessions: GetReadingSessions by injectLazy()
    private val getLibraryManga: GetLibraryManga by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val sourceManager: SourceManager by injectLazy()

    private var sessions: List<ReadingSession> = emptyList()
    private var mangaByKey: Map<SeriesKey, Manga> = emptyMap()
    private var library: List<LibraryManga> = emptyList()
    private var librarySummary: LibrarySummary? = null

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
            librarySummary = librarySummary()
            mutableState.update { build(period) }
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

        return State.Success(
            period = period,
            stats = stats,
            hasAnyReading = sessions.isNotEmpty(),
            mangaByKey = mangaByKey,
            finished = finished,
            topGenres = genres.entries.sortedByDescending { it.value }.take(TOP_GENRES).map { it.key to it.value },
            library = librarySummary,
        )
    }

    private fun librarySummary(): LibrarySummary? {
        if (library.isEmpty()) return null

        // Estimate the backlog from how long finished chapters took, per series type where known
        val allTime = ReadingStats.calculate(sessions, StatsPeriod.ALL, Instant.now(), ZoneId.systemDefault())
        val overallPerChapter = allTime.types.sumOf { it.time }
            .takeIf { allTime.chaptersFinished > 0 }
            ?.let { it / allTime.chaptersFinished }
        val perChapterByType = allTime.types.mapNotNull { type -> type.timePerChapter?.let { type.category to it } }.toMap()
        val backlog = overallPerChapter?.let { fallback ->
            library.sumOf { item ->
                val category = ReadingCategory.of(item.manga.seriesType(sourceManager = sourceManager))
                val perChapter = perChapterByType[category] ?: fallback
                item.unread * perChapter
            }
        }

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

    @Immutable
    data class LibrarySummary(
        val seriesCount: Int,
        val unreadChapters: Int,
        /** Null until a chapter has been finished, since there's no pace to go on. */
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
            val hasAnyReading: Boolean,
            val mangaByKey: Map<SeriesKey, Manga>,
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
