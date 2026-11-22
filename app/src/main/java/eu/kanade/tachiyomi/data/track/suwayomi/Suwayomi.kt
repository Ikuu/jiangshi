package eu.kanade.tachiyomi.data.track.suwayomi

import android.content.Context
import android.graphics.Color
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.PendingReadMarks
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.updateNewTrackInfo
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import java.util.concurrent.ConcurrentHashMap
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uy.kohesive.injekt.injectLazy
import yokai.domain.chapter.interactor.UpdateChapter
import yokai.domain.track.interactor.GetTrack
import yokai.i18n.MR
import yokai.util.lang.getString

class Suwayomi(private val context: Context, id: Long) : TrackService(id), EnhancedTrackService {
    val api by lazy { TachideskApi() }
    private val preferences: PreferencesHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val getTrack: GetTrack by injectLazy()
    private val updateChapter: UpdateChapter by injectLazy()
    private val serverTrackers = ConcurrentHashMap<String, List<ServerTrackerInfo>>()
    private val pendingLock = Mutex()

    // Chapters sync one by one when a series opens. The server's last read chapter is the most
    // recently opened one, so going by it would undo chapters marked unread on the server.
    override val syncsChaptersOnRefresh = false

    override fun nameRes() = MR.strings.suwayomi

    override fun getLogo() = R.drawable.ic_tracker_suwayomi

    override fun getTrackerColor() = Color.rgb(255, 214, 0)

    override fun getLogoColor() = Color.TRANSPARENT

    companion object {
        const val UNREAD = 1
        const val READING = 2
        const val COMPLETED = 3
    }

    override fun getStatusList() = listOf(UNREAD, READING, COMPLETED)

    override fun isCompletedStatus(index: Int): Boolean = getStatusList()[index] == COMPLETED

    override fun getStatus(status: Int): String = with(context) {
        when (status) {
            UNREAD -> getString(MR.strings.unread)
            READING -> getString(MR.strings.reading)
            COMPLETED -> getString(MR.strings.completed)
            else -> ""
        }
    }

    override fun getGlobalStatus(status: Int): String = with(context) {
        when (status) {
            UNREAD -> getString(MR.strings.plan_to_read)
            READING -> getString(MR.strings.reading)
            COMPLETED -> getString(MR.strings.completed)
            else -> ""
        }
    }

    override fun completedStatus(): Int = COMPLETED
    override fun readingStatus() = READING
    override fun planningStatus() = UNREAD

    override fun getScoreList(): ImmutableList<String> = persistentListOf()

    override fun displayScore(track: Track): String = ""

    override suspend fun add(track: Track): Track {
        track.status = READING
        updateNewTrackInfo(track)
        return api.updateProgress(track).also { loadServerTrackers(it) }
    }

    override suspend fun update(track: Track, setToRead: Boolean): Track {
        updateTrackStatus(track, setToRead)
        return api.updateProgress(track).also { loadServerTrackers(it) }
    }

    /** Trackers linked to the series on the server, as of the last refresh or update. */
    fun serverTrackers(track: Track?): List<ServerTrackerInfo> =
        track?.let { serverTrackers[seriesId(it)] }.orEmpty()

    /** Tracking URLs are either the series' ID or its API URL ending in the ID. */
    private fun seriesId(track: Track) = track.tracking_url.trimEnd('/').substringAfterLast('/')

    /** Only for display, so a server too old to list its trackers still tracks as before. */
    suspend fun loadServerTrackers(track: Track) {
        try {
            serverTrackers[seriesId(track)] = api.getServerTrackers(track.tracking_url)
        } catch (e: Exception) {
            Logger.w(e) { "Could not get the trackers linked on Suwayomi for ${track.tracking_url}" }
        }
    }

    /** The series on the server, from its tracker link if it has one. */
    fun seriesUrl(manga: Manga, tracks: List<Track>): String =
        tracks.find { it.sync_id == id }?.tracking_url ?: manga.url

    /**
     * Sends a change made in the app to the server, so the next sync doesn't undo it. Suwayomi is where the
     * chapters come from, so this doesn't depend on the tracking settings. A change that can't be sent is kept
     * and tried again on the next sync, which leaves those chapters alone until it's through.
     * The server's own trackers are only updated when [updateServerTrackers], following the app's tracking settings.
     */
    suspend fun updateChaptersRead(
        manga: Manga,
        chapterNumbers: List<Float>,
        read: Boolean,
        updateServerTrackers: Boolean = preferences.trackMarkedAsRead().get(),
    ) {
        if (!accept(sourceManager.getOrStub(manga.source))) return
        val mangaId = manga.id ?: return
        val numbers = chapterNumbers.filter { it >= 0f }.toSet()
        if (numbers.isEmpty()) return
        setPendingMarks(mangaId, numbers, read)
        try {
            api.setChaptersRead(seriesUrl(manga, getTrack.awaitAllByMangaId(mangaId)), numbers, read, updateServerTrackers)
            clearPendingMarks(mangaId, numbers, read)
        } catch (e: Exception) {
            Logger.w(e) { "Could not update read chapters on Suwayomi for ${manga.url}, will try again" }
        }
    }

    /**
     * Sends a bookmark added or removed in the app to the server. Like read marks, one that can't be sent is
     * kept and tried again on the next sync, and the server's bookmark for that chapter is ignored until then.
     */
    suspend fun updateChaptersBookmarked(manga: Manga, chapterNumbers: List<Float>, bookmarked: Boolean) {
        if (!accept(sourceManager.getOrStub(manga.source))) return
        val mangaId = manga.id ?: return
        val numbers = chapterNumbers.filter { it >= 0f }.map { it.toString() }
        if (numbers.isEmpty()) return
        pendingBookmarks.add(mangaId, numbers, bookmarked)
        try {
            api.setChaptersBookmarked(seriesUrl(manga, getTrack.awaitAllByMangaId(mangaId)), numbers.mapTo(mutableSetOf()) { it.toFloat() }, bookmarked)
            pendingBookmarks.remove(mangaId, numbers, bookmarked)
        } catch (e: Exception) {
            Logger.w(e) { "Could not update bookmarks on Suwayomi for ${manga.url}, will try again" }
        }
    }

    /** Bookmarks still to send, for every series, where [PendingMark.read] says whether it adds the bookmark. */
    fun allPendingBookmarks(): List<PendingMark> =
        pendingBookmarks.all().mapNotNull { mark -> mark.chapter.toFloatOrNull()?.let { PendingMark(mark.mangaId, it, mark.read) } }

    suspend fun discardPendingBookmarks(marks: Collection<PendingMark>) =
        pendingBookmarks.discard(marks.map { PendingReadMarks.Mark(it.mangaId, it.chapterNumber.toString(), it.read) })

    private fun pendingBookmarkNumbers(mangaId: Long): Set<Float> =
        pendingBookmarks.forManga(mangaId).keys.mapNotNullTo(mutableSetOf()) { it.toFloatOrNull() }

    private suspend fun sendPendingBookmarks(manga: Manga, url: String) {
        val mangaId = manga.id ?: return
        pendingBookmarks.forManga(mangaId).entries.groupBy({ it.value }, { it.key }).forEach { (bookmarked, numbers) ->
            try {
                api.setChaptersBookmarked(url, numbers.mapNotNullTo(mutableSetOf()) { it.toFloatOrNull() }, bookmarked)
                pendingBookmarks.remove(mangaId, numbers, bookmarked)
            } catch (e: Exception) {
                Logger.w(e) { "Could not send waiting bookmarks to Suwayomi for ${manga.url}" }
            }
        }
    }

    private val pending by lazy { PendingReadMarks(preferences.suwayomiPendingMarks()) }

    private val pendingBookmarks by lazy { PendingReadMarks(preferences.suwayomiPendingBookmarks()) }

    /** A read mark made in the app that hasn't reached the server yet. */
    data class PendingMark(val mangaId: Long, val chapterNumber: Float, val read: Boolean)

    /** Read marks still to send, for every series. */
    fun allPendingMarks(): List<PendingMark> =
        pending.all().mapNotNull { mark -> mark.chapter.toFloatOrNull()?.let { PendingMark(mark.mangaId, it, mark.read) } }

    /** Chapter numbers with a read mark still to send, and whether it marks them read. */
    private fun pendingMarks(mangaId: Long): Map<Float, Boolean> =
        pending.forManga(mangaId).mapNotNull { (chapter, read) -> chapter.toFloatOrNull()?.let { it to read } }.toMap()

    /** Drops marks that can't be sent, such as for a series no longer on the server. */
    suspend fun discardPendingMarks(marks: Collection<PendingMark>) =
        pending.discard(marks.map { PendingReadMarks.Mark(it.mangaId, it.chapterNumber.toString(), it.read) })

    /** Tries to send the waiting marks for one series. Ones that still fail stay waiting. */
    private suspend fun sendPendingMarks(manga: Manga, url: String) {
        val mangaId = manga.id ?: return
        pendingMarks(mangaId).entries.groupBy({ it.value }, { it.key }).forEach { (read, numbers) ->
            try {
                api.setChaptersRead(url, numbers.toSet(), read, updateServerTrackers = false)
                clearPendingMarks(mangaId, numbers, read)
            } catch (e: Exception) {
                Logger.w(e) { "Could not send waiting read marks to Suwayomi for ${manga.url}" }
            }
        }
    }

    /** Tries to send every waiting mark and bookmark now, rather than on each series' next sync. */
    suspend fun sendAllPendingMarks() {
        (allPendingMarks() + allPendingBookmarks()).map { it.mangaId }.distinct().forEach { mangaId ->
            val manga = getManga.awaitById(mangaId) ?: return@forEach
            val url = seriesUrl(manga, getTrack.awaitAllByMangaId(mangaId))
            sendPendingMarks(manga, url)
            sendPendingBookmarks(manga, url)
        }
    }

    private suspend fun setPendingMarks(mangaId: Long, numbers: Set<Float>, read: Boolean) =
        pending.add(mangaId, numbers.map { it.toString() }, read)

    private suspend fun clearPendingMarks(mangaId: Long, numbers: Collection<Float>, read: Boolean) =
        pending.remove(mangaId, numbers.map { it.toString() }, read)

    /**
     * Brings in chapters read and pages reached on the server, matching chapters by number, and saves them.
     * Pages only move forward. Marks that couldn't be sent before go first, and their chapters are left as
     * they are in the app until they're through.
     *
     * The first time a series syncs, chapters read in the app but not on the server are sent to it, since
     * earlier versions only sent marks with "Update tracking when marked as read" on. After that, a chapter
     * unread on the server goes back to unread in the app, unless a tracker update is still waiting to be sent.
     *
     * Bookmarks sync the same way: the first time, a chapter bookmarked on either side ends up bookmarked on
     * both, and after that the server decides, apart from bookmarks still waiting to be sent.
     * Returns whether any chapter changed.
     */
    suspend fun syncChapterProgress(manga: Manga, chapters: List<Chapter>, tracks: List<Track>): Boolean {
        val mangaId = manga.id ?: return false
        val url = seriesUrl(manga, tracks)
        sendPendingMarks(manga, url)
        sendPendingBookmarks(manga, url)
        val stillPending = pendingMarks(mangaId).keys
        val bookmarksPending = pendingBookmarkNumbers(mangaId)

        val remote = try {
            api.getChapterProgress(url)
        } catch (e: Exception) {
            Logger.w(e) { "Could not get Suwayomi progress for ${manga.url}" }
            return false
        }.groupBy { it.chapterNumber }

        val syncedBefore = mangaId.toString() in preferences.suwayomiSyncedSeries().get()
        if (!syncedBefore) {
            val readOnlyHere = chapters
                .filter { it.read && it.chapter_number >= 0f && it.chapter_number !in stillPending }
                .map { it.chapter_number }
                .filter { number -> remote[number]?.none { it.isRead } == true }
                .toSet()
            try {
                if (readOnlyHere.isNotEmpty()) api.setChaptersRead(url, readOnlyHere, true, updateServerTrackers = false)
                pendingLock.withLock {
                    preferences.suwayomiSyncedSeries().set(preferences.suwayomiSyncedSeries().get() + mangaId.toString())
                }
            } catch (e: Exception) {
                Logger.w(e) { "Could not send chapters read in the app to Suwayomi for ${manga.url}" }
            }
        }

        val hasPendingTrackerUpdates = preferences.trackingsToAddOnline().get()
            .any { it.startsWith("$mangaId:$id:") }
        val serverDecidesUnread = syncedBefore && !hasPendingTrackerUpdates

        val bookmarksSyncedBefore = mangaId.toString() in preferences.suwayomiBookmarkSyncedSeries().get()
        if (!bookmarksSyncedBefore) {
            val bookmarkedOnlyHere = chapters
                .filter { it.bookmark && it.chapter_number >= 0f && it.chapter_number !in bookmarksPending }
                .map { it.chapter_number }
                .filter { number -> remote[number]?.none { it.isBookmarked } == true }
                .toSet()
            try {
                if (bookmarkedOnlyHere.isNotEmpty()) api.setChaptersBookmarked(url, bookmarkedOnlyHere, true)
                pendingLock.withLock {
                    preferences.suwayomiBookmarkSyncedSeries()
                        .set(preferences.suwayomiBookmarkSyncedSeries().get() + mangaId.toString())
                }
            } catch (e: Exception) {
                Logger.w(e) { "Could not send chapters bookmarked in the app to Suwayomi for ${manga.url}" }
            }
        }

        val updates = chapters.mapNotNull { chapter ->
            if (chapter.chapter_number < 0f) return@mapNotNull null
            val matches = remote[chapter.chapter_number] ?: return@mapNotNull null
            val readChanged = chapter.chapter_number !in stillPending && syncReadFrom(chapter, matches, serverDecidesUnread)
            val bookmarkChanged = chapter.chapter_number !in bookmarksPending &&
                syncBookmarkFrom(chapter, matches, serverDecidesUnbookmark = bookmarksSyncedBefore)
            if (readChanged || bookmarkChanged) chapter.toProgressUpdate() else null
        }
        if (updates.isEmpty()) return false
        updateChapter.awaitAll(updates)
        return true
    }

    /** Applies the server's read status and page to [chapter]. Returns whether it changed. */
    private fun syncReadFrom(chapter: Chapter, matches: List<ChapterNode>, serverDecidesUnread: Boolean): Boolean {
        val furthestPage = matches.maxOf { it.lastPageRead }
        val pagesLeft = (matches.maxOf { it.pageCount } - furthestPage).coerceAtLeast(0)
        val isReadOnServer = matches.any { it.isRead }
        if (chapter.read) {
            if (isReadOnServer || !serverDecidesUnread) return false
            chapter.read = false
            chapter.last_page_read = furthestPage
            chapter.pages_left = pagesLeft
        } else if (isReadOnServer) {
            chapter.read = true
        } else if (furthestPage > chapter.last_page_read) {
            chapter.last_page_read = furthestPage
            chapter.pages_left = pagesLeft
        } else {
            return false
        }
        return true
    }

    /** Applies the server's bookmark to [chapter]. Returns whether it changed. */
    private fun syncBookmarkFrom(chapter: Chapter, matches: List<ChapterNode>, serverDecidesUnbookmark: Boolean): Boolean {
        val isBookmarkedOnServer = matches.any { it.isBookmarked }
        if (chapter.bookmark == isBookmarkedOnServer) return false
        if (chapter.bookmark && !serverDecidesUnbookmark) return false
        chapter.bookmark = isBookmarkedOnServer
        return true
    }

    override suspend fun bind(track: Track): Track {
        return track
    }

    override suspend fun search(query: String): List<TrackSearch> {
        TODO("Not yet implemented")
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getTrackSearch(track.tracking_url)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        loadServerTrackers(track)
        return track
    }

    override suspend fun login(username: String, password: String): Boolean {
        saveCredentials("user", "pass")
        return true
    }

    override fun loginNoop() {
        saveCredentials("user", "pass")
    }

    override fun getAcceptedSources(): List<String> = listOf(TachideskApi.SOURCE_CLASS)

    override suspend fun match(manga: Manga): TrackSearch? =
        try {
            api.getTrackSearch(manga.url)
        } catch (e: Exception) {
            Logger.e(e) { "Could not match ${manga.title} (url: ${manga.url}) with Suwayomi" }
            null
        }
}
