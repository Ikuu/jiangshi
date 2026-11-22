package eu.kanade.tachiyomi.ui.manga

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toFile
import co.touchlab.kermit.Logger
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.hippo.unifile.UniFile
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.bookmarkedFilter
import eu.kanade.tachiyomi.data.database.models.chapterOrder
import eu.kanade.tachiyomi.data.database.models.downloadedFilter
import eu.kanade.tachiyomi.data.database.models.prepareCoverUpdate
import eu.kanade.tachiyomi.data.database.models.readFilter
import eu.kanade.tachiyomi.data.database.models.removeCover
import eu.kanade.tachiyomi.data.database.models.seriesType
import eu.kanade.tachiyomi.data.database.models.sortDescending
import eu.kanade.tachiyomi.data.database.models.updateCoverLastModified
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.LinkedSeries
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.kavita.KavitaApi
import eu.kanade.tachiyomi.data.track.kavita.KavitaChapterInfo
import eu.kanade.tachiyomi.data.track.kavita.KavitaEntryType
import eu.kanade.tachiyomi.data.track.kavita.KavitaLibraryTypes
import eu.kanade.tachiyomi.data.track.kavita.KavitaSeriesLayout
import eu.kanade.tachiyomi.data.track.suwayomi.ServerDownload
import eu.kanade.tachiyomi.data.track.suwayomi.Suwayomi
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.SourceNotFoundException
import eu.kanade.tachiyomi.source.getExtension
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.ui.manga.track.TrackingBottomSheet
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.util.chapter.ChapterFilter
import eu.kanade.tachiyomi.util.chapter.ChapterSort
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.EntryKind
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithTrackServiceTwoWay
import eu.kanade.tachiyomi.util.chapter.updateTrackChapterMarkedAsRead
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.lang.trimOrNull
import eu.kanade.tachiyomi.util.manga.MangaShortcutManager
import eu.kanade.tachiyomi.util.manga.MangaUtil
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.e
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchNonCancellableIO
import eu.kanade.tachiyomi.util.system.launchNow
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.widget.TriStateCheckBox
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import eu.kanade.tachiyomi.util.view.snack
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.category.interactor.GetCategories
import yokai.domain.chapter.interactor.GetAvailableScanlators
import yokai.domain.chapter.interactor.GetChapter
import yokai.domain.chapter.interactor.UpdateChapter
import yokai.domain.history.interactor.GetHistory
import yokai.domain.stats.ReadingCategory
import yokai.domain.stats.ReadingPace
import yokai.domain.stats.ReadingSession
import yokai.domain.stats.SeriesReadingSummary
import yokai.domain.stats.UnreadEntry
import yokai.domain.stats.interactor.GetReadingSessions
import yokai.domain.library.custom.model.CustomMangaInfo
import yokai.domain.manga.interactor.GetExcludedScanlators
import yokai.domain.manga.interactor.GetManga
import yokai.domain.manga.interactor.UpdateManga
import yokai.domain.manga.models.MangaUpdate
import yokai.domain.manga.models.cover
import yokai.domain.storage.StorageManager
import yokai.domain.track.interactor.DeleteTrack
import yokai.domain.track.interactor.GetTrack
import yokai.domain.track.interactor.InsertTrack
import yokai.i18n.MR
import yokai.util.lang.getString

class MangaDetailsPresenter(
    val mangaId: Long,
    val sourceManager: SourceManager = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get(),
    val coverCache: CoverCache = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val chapterFilter: ChapterFilter = Injekt.get(),
    private val storageManager: StorageManager = Injekt.get(),
) : BaseCoroutinePresenter<MangaDetailsController>(),
    DownloadQueue.Listener {
    private val getAvailableScanlators: GetAvailableScanlators by injectLazy()
    private val getCategories: GetCategories by injectLazy()
    private val getChapter: GetChapter by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val updateChapter: UpdateChapter by injectLazy()
    private val updateManga: UpdateManga by injectLazy()
    private val deleteTrack: DeleteTrack by injectLazy()
    private val getTrack: GetTrack by injectLazy()
    private val insertTrack: InsertTrack by injectLazy()
    private val getHistory: GetHistory by injectLazy()
    private val getReadingSessions: GetReadingSessions by injectLazy()
    private val getExcludedScanlators: GetExcludedScanlators by injectLazy()

    private val networkPreferences: NetworkPreferences by injectLazy()

//    private val currentMangaInternal: MutableStateFlow<Manga?> = MutableStateFlow(null)
//    val currentManga get() = currentMangaInternal.asStateFlow()

    lateinit var manga: Manga
    var excludedScanlators: Set<String> = setOf()
    fun isMangaLateInitInitialized() = ::manga.isInitialized

    private val customMangaManager: CustomMangaManager by injectLazy()
    private val mangaShortcutManager: MangaShortcutManager by injectLazy()

    val source: Source by lazy { sourceManager.getOrStub(manga.source) }

    private lateinit var chapterSort: ChapterSort
    val extension by lazy { (source as? HttpSource)?.getExtension() }

    var isLockedFromSearch = false
    var hasRequested = false
    var isLoading = false
    var scrollType = 0

    private val loggedServices by lazy { Injekt.get<TrackManager>().services.filter { it.isLogged } }
    private var tracks = emptyList<Track>()

    var trackList: List<TrackItem> = emptyList()

    var chapters: List<ChapterItem> = emptyList()
        private set

    var allChapters: List<ChapterItem> = emptyList()
        private set

    var allHistory: List<History> = emptyList()
        private set

    /** Time spent reading this series since stats began, or null if it hasn't been read since. */
    var readingSummary: SeriesReadingSummary? = null
        private set

    /** This series' reading sessions, oldest first. */
    var readingSessions: List<ReadingSession> = emptyList()
        private set

    /** Roughly how long the unread chapters would take at the usual pace, or null with no pace to go on. */
    var readingTimeLeft: Long? = null
        private set

    /** Every series' sessions, for a pace before this series has its own. Loaded once, as a session more changes little. */
    private var allReadingSessions: List<ReadingSession>? = null

    val headerItem: MangaHeaderItem by lazy { MangaHeaderItem(mangaId, view?.fromCatalogue == true)}
    var tabletChapterHeaderItem: MangaHeaderItem? = null
        get() {
            when (view?.isTablet) {
                true -> if (field == null) {
                    field = MangaHeaderItem(mangaId, false).apply {
                        isChapterHeader = true
                    }
                }
                else -> if (field != null) {
                    field = null
                }
            }
            return field
        }
        private set

    var allChapterScanlators: Set<String> = emptySet()

    /**
     * Volumes the user has expanded or collapsed against the default, stored per series as
     * "mangaId|volume|1" (collapsed) or "|0" (expanded). Only overrides are kept, so a volume left
     * alone follows the default and collapses once it's been read.
     */
    private val volumeCollapsePreference by lazy {
        preferences.preferenceStore.getStringSet("manga_details_collapsed_volumes", emptySet())
    }
    private var volumeOverrides: Map<String, Boolean>? = null

    private fun volumeOverrides(): Map<String, Boolean> {
        return volumeOverrides ?: volumeCollapsePreference.get()
            .filter { it.substringBefore('|') == mangaId.toString() }
            .associate { it.substringAfter('|').substringBeforeLast('|') to it.endsWith("|1") }
            .also { volumeOverrides = it }
    }

    /** Null [volume] is the group of chapters outside a volume */
    private fun volumeKey(volume: Float?) = volume?.toString() ?: "none"

    /** Volumes are hidden when every chapter in them is read, unless the user expanded them */
    fun isVolumeCollapsed(volume: Float?, chapters: List<ChapterItem>): Boolean {
        return volumeOverrides()[volumeKey(volume)] ?: chapters.all { it.read }
    }

    fun toggleVolume(volume: Float?, chapters: List<ChapterItem>) {
        val key = volumeKey(volume)
        val collapsed = !isVolumeCollapsed(volume, chapters)
        val overrides = if (collapsed == chapters.all { it.read }) {
            volumeOverrides() - key
        } else {
            volumeOverrides() + (key to collapsed)
        }
        volumeOverrides = overrides
        val otherSeries = volumeCollapsePreference.get().filterNot { it.substringBefore('|') == mangaId.toString() }
        volumeCollapsePreference.set(
            otherSeries.toSet() + overrides.map { (key, collapsed) -> "$mangaId|$key|${if (collapsed) 1 else 0}" },
        )
    }

    /** Whether a Kavita series is on the server's Want to Read list, or null if that isn't known. */
    var isInWantToRead: Boolean? = null
        private set

    /** Adds a Kavita series to Want to Read on the server, or takes it off. */
    fun toggleWantToRead() {
        val wanted = !(isInWantToRead ?: return)
        presenterScope.launchIO {
            val message = try {
                Injekt.get<TrackManager>().kavita.api.setWantToRead(manga.url, wanted)
                isInWantToRead = wanted
                if (wanted) MR.strings.added_to_want_to_read else MR.strings.removed_from_want_to_read
            } catch (e: Exception) {
                Logger.w(e) { "Could not update Kavita's Want to Read for ${manga.url}" }
                MR.strings.could_not_update_want_to_read
            }
            withUIContext {
                view?.view?.snack(message)
                view?.updateChapters()
            }
        }
    }

    /** Whether this series can be linked with the same series on the other of Kavita and Suwayomi. */
    val canLinkSeries: Boolean
        get() = Injekt.get<TrackManager>().linkedSeries.canLink(manga)

    /** The name of the server a linked series would come from: Suwayomi for a Kavita series, and the other way round. */
    fun otherServerName(context: Context): String {
        val trackManager = Injekt.get<TrackManager>()
        val other = if (trackManager.kavita.accept(source)) trackManager.suwayomi else trackManager.kavita
        return context.getString(other.nameRes())
    }

    /** The series this one is linked with on the other server, if any. */
    suspend fun linkedSeries(): Manga? =
        Injekt.get<TrackManager>().linkedSeries.linkedTo(manga.id!!)?.let { getManga.awaitById(it) }

    /**
     * Series that this one could be linked with, closest titles first: ones from the other server in the
     * library, and ones found searching that server for [query].
     */
    suspend fun linkCandidates(query: String = manga.title): List<LinkedSeries.Candidate> =
        Injekt.get<TrackManager>().linkedSeries.candidates(manga, query)

    /** Links this series with [candidate], bringing the chapters read in either into both. */
    fun linkSeries(candidate: LinkedSeries.Candidate) {
        presenterScope.launchIO {
            val linkedSeries = Injekt.get<TrackManager>().linkedSeries
            val other = linkedSeries.seriesFor(candidate)
            val marked = linkedSeries.link(manga, other)
            getChapters()
            withUIContext {
                view?.updateChapters()
                val context = view?.view?.context ?: return@withUIContext
                view?.view?.snack(
                    if (marked > 0) {
                        context.getString(MR.plurals.linked_series_marked, marked, other.title, marked)
                    } else {
                        context.getString(MR.strings.linked_series_done, other.title)
                    },
                )
            }
        }
    }

    fun unlinkSeries() {
        presenterScope.launchIO {
            Injekt.get<TrackManager>().linkedSeries.unlink(manga.id!!)
            withUIContext { view?.view?.snack(MR.strings.linked_series_removed) }
        }
    }

    /** Library type and chapter volumes from the Kavita server, when the source is Kavita */
    var kavitaLayout: KavitaSeriesLayout? = null
        private set

    override val progressJobs: MutableMap<Download, Job> = mutableMapOf()
    override val queueListenerScope get() = presenterScope

    override fun onCreate() {
        val controller = view ?: return

        isLockedFromSearch = controller.shouldLockIfNeeded && SecureActivityDelegate.shouldBeLocked()
        if (!::manga.isInitialized) runBlocking {
            setCurrentExcludedScanlators(getExcludedScanlators.await(mangaId))
            refreshMangaFromDb()
        }
        syncData()

        presenterScope.launchUI {
            downloadManager.statusFlow()
                .filter { it.manga.id == mangaId }
                .catch { error -> Logger.e(error) }
                .collect(::onStatusChange)
        }
        presenterScope.launchUI {
            downloadManager.progressFlow()
                .filter { it.manga.id == mangaId }
                .catch { error -> Logger.e(error) }
                .collect(::onQueueUpdate)
        }
        presenterScope.launchIO {
            downloadManager.queueState.collectLatest(::onQueueUpdate)
        }
        presenterScope.launchIO {
            getExcludedScanlators.subscribe(mangaId)
                .distinctUntilChanged()
                .collect(::setCurrentExcludedScanlators)
        }

        runBlocking {
            tracks = getTrack.awaitAllByMangaId(mangaId)
        }
    }

    /**
     * onCreate but executed after UI layout is ready otherwise it'd only show blank screen
     */
    fun onCreateLate() {
        val controller = view ?: return

        LibraryUpdateJob.updateFlow
            .filter { it == mangaId }
            .onEach { onUpdateManga() }
            .launchIn(presenterScope)

        val fetchMangaNeeded = !manga.initialized
        val fetchChaptersNeeded = runBlocking { getChaptersNow() }.isEmpty()

        presenterScope.launch {
            isLoading = true
            withUIContext {
                controller.updateHeader()
            }
            if (fetchMangaNeeded || fetchChaptersNeeded) {
                fetchMangaUpdateFromSource(
                    fetchDetails = fetchMangaNeeded,
                    fetchChapters = fetchChaptersNeeded,
                    manualFetch = false,
                )
            }
            isLoading = false
            withUIContext {
                controller.updateChapters()
            }

            setTrackItems()
            fetchKavitaLayout()
            fetchSuwayomiProgress()
            fetchSuwayomiServerTrackers()
            fetchServerDownloads()
        }

        refreshTracking(false)
    }

    private suspend fun fetchKavitaLayout() {
        val kavita = Injekt.get<TrackManager>().kavita
        if (!kavita.accept(source)) return
        kavita.sendPendingMarks(manga)
        val layout = try {
            kavita.api.getSeriesLayout(manga.url)
        } catch (e: Exception) {
            Logger.w(e) { "Could not get Kavita volumes for ${manga.url}" }
            return
        }
        kavitaLayout = layout
        updateReadingTimeLeft()
        isInWantToRead = try {
            kavita.api.isInWantToRead(manga.url)
        } catch (e: Exception) {
            Logger.w(e) { "Could not check Kavita's Want to Read for ${manga.url}" }
            null
        }
        layout.libraryType?.let { KavitaLibraryTypes.putAll(mapOf(manga.url to it)) }
        applyKavitaProgress()
        withUIContext { view?.updateChapters() }
    }

    /**
     * Brings in what's been read on the Kavita server. The tracker only covers library series and
     * goes by the last chapter read, so series being browsed would otherwise show as unread.
     * Progress only moves forward here; marking unread stays a choice made in the app.
     */
    private suspend fun applyKavitaProgress() {
        val waiting = Injekt.get<TrackManager>().kavita.pendingChapterIds(manga.id!!)
        val updates = allChapters.mapNotNull { item ->
            if (KavitaApi.chapterId(item.url) in waiting) return@mapNotNull null
            val info = kavitaInfo(item.chapter) ?: return@mapNotNull null
            if (item.read || info.pages <= 0 || info.pagesRead <= 0) return@mapNotNull null
            if (info.pagesRead >= info.pages) {
                item.read = true
            } else if (info.pagesRead > item.last_page_read) {
                item.last_page_read = info.pagesRead
                item.pages_left = info.pages - info.pagesRead
            } else {
                return@mapNotNull null
            }
            item.toProgressUpdate()
        }
        if (updates.isEmpty()) return
        updateChapter.awaitAll(updates)
        val updated = updates.map { it.id }.toSet()
        markLinkedRead(allChapters.filter { it.read && it.id in updated }.map { it.chapter })
        getChapters()
    }

    /** Chapters found read on the server go on to the series linked with this one, if any. */
    private suspend fun markLinkedRead(chapters: List<Chapter>) {
        if (chapters.isEmpty()) return
        Injekt.get<TrackManager>().linkedSeries.markLinked(manga, chapters, true)
    }

    /** Trackers linked on the Suwayomi server, for the tracking sheet. */
    private suspend fun fetchSuwayomiServerTrackers() {
        val suwayomi = Injekt.get<TrackManager>().suwayomi
        val track = tracks.find { it.sync_id == suwayomi.id } ?: return
        if (!suwayomi.accept(source)) return
        suwayomi.loadServerTrackers(track)
        setTrackItems()
        withUIContext { view?.refreshTracking(trackList) }
    }

    /** Brings in chapters read and pages reached on the Suwayomi server. */
    private suspend fun fetchSuwayomiProgress() {
        val suwayomi = Injekt.get<TrackManager>().suwayomi
        if (!suwayomi.accept(source)) return
        val readBefore = allChapters.filter { it.read }.map { it.id }.toSet()
        if (!suwayomi.syncChapterProgress(manga, allChapters, tracks)) return
        markLinkedRead(allChapters.filter { it.read && it.id !in readBefore }.map { it.chapter })
        getChapters()
        withUIContext { view?.updateChapters() }
    }

    /** Chapters downloaded on the Suwayomi server or waiting in its queue, by chapter number. */
    private var serverDownloads: Map<Float, ServerDownload> = emptyMap()
    private var serverDownloadsJob: Job? = null

    val isSuwayomiSource: Boolean get() = Injekt.get<TrackManager>().suwayomi.accept(source)

    /** The chapter's download on the Suwayomi server, or null if the server's downloads aren't known. */
    fun serverDownload(chapter: Chapter): ServerDownload? = serverDownloads[chapter.chapter_number]

    /**
     * Gets the chapters downloaded on the Suwayomi server, and checks again every few seconds while any
     * are still waiting there, so the rows follow its progress.
     */
    private fun fetchServerDownloads() {
        if (!isSuwayomiSource) return
        val suwayomi = Injekt.get<TrackManager>().suwayomi
        serverDownloadsJob?.cancel()
        serverDownloadsJob = presenterScope.launchIO {
            // The server takes a finished chapter off its queue a moment before it counts as downloaded,
            // so chapters that were queued are followed for a few more checks until they show up
            var lastQueued = emptySet<Float>()
            var settleChecks = 0
            repeat(SERVER_DOWNLOAD_CHECKS) {
                val downloads = try {
                    suwayomi.serverDownloads(manga, tracks)
                } catch (e: Exception) {
                    Logger.w(e) { "Could not get the downloads on Suwayomi for ${manga.url}" }
                    return@launchIO
                }
                if (downloads != serverDownloads) {
                    serverDownloads = downloads
                    withUIContext { view?.updateChapters() }
                }
                val queued = downloads.filterValues { it.isQueued }.keys
                val settling = lastQueued.any { number -> downloads[number]?.let { !it.isQueued && !it.isDownloaded && !it.hasError } == true }
                if (queued.isEmpty() && (!settling || settleChecks++ >= SERVER_DOWNLOAD_SETTLE_CHECKS)) return@launchIO
                if (queued.isNotEmpty()) lastQueued = queued
                delay(if (queued.isEmpty()) SERVER_DOWNLOAD_SETTLE_INTERVAL else SERVER_DOWNLOAD_CHECK_INTERVAL)
            }
        }
    }

    /** Asks the Suwayomi server to download these chapters, and says how it went. */
    fun downloadOnServer(chapters: List<ChapterItem>) = serverDownloadAction { suwayomi ->
        val count = suwayomi.downloadOnServer(manga, chapters.map { it.chapter }, tracks)
        if (count == 0) {
            view?.view?.context?.getString(MR.strings.server_already_has_chapters)
        } else {
            view?.view?.context?.getString(MR.plurals.downloading_on_server, count, count)
        }
    }

    /** Removes these chapters' downloads from the Suwayomi server, and says how it went. */
    fun removeFromServer(chapters: List<ChapterItem>) = serverDownloadAction { suwayomi ->
        val count = suwayomi.removeFromServer(manga, chapters.map { it.chapter }, tracks)
        view?.view?.context?.getString(MR.plurals.removed_from_server, count, count)
    }

    private fun serverDownloadAction(action: suspend (Suwayomi) -> String?) {
        val suwayomi = Injekt.get<TrackManager>().suwayomi
        presenterScope.launchIO {
            val message = try {
                action(suwayomi)
            } catch (e: Exception) {
                Logger.w(e) { "Could not change the downloads on Suwayomi for ${manga.url}" }
                view?.view?.context?.getString(MR.strings.could_not_reach_suwayomi)
            }
            withUIContext { message?.let { view?.view?.snack(it) } }
            fetchServerDownloads()
        }
    }

    private fun kavitaInfo(chapter: Chapter): KavitaChapterInfo? {
        val chapters = kavitaLayout?.chapters ?: return null
        return KavitaApi.chapterId(chapter.url)?.let { chapters[it] }
    }

    /**
     * The volume a chapter belongs to. Kavita's server knows this even when the chapter name doesn't
     * include it; other sources fall back to "Vol. 2" in the name.
     */
    fun volumeOf(chapter: Chapter): Float? {
        val info = kavitaInfo(chapter) ?: return ChapterUtil.getVolumeNumber(chapter)?.toFloat()
        return info.volume
    }

    fun entryKind(chapter: Chapter): EntryKind {
        return when (kavitaInfo(chapter)?.type) {
            KavitaEntryType.Volume -> EntryKind.Volume
            KavitaEntryType.Chapter -> EntryKind.Chapter
            KavitaEntryType.Issue -> EntryKind.Issue
            KavitaEntryType.Special -> EntryKind.Special
            null -> ChapterUtil.entryKind(chapter)
        }
    }

    /** Page count from the Kavita server, which knows it before the chapter is opened */
    fun pageCount(chapter: Chapter): Int? = kavitaInfo(chapter)?.pages?.takeIf { it > 0 }

    /**
     * A Kavita comic library, or comic issues from any source, make the series a comic unless its
     * tags or the edit dialog already give it a type.
     */
    fun isComic(): Boolean {
        val hasTypeTag = manga.genre?.split(",")?.any { manga.isSeriesTag(it.trim()) } == true
        if (hasTypeTag) return manga.seriesType() == Manga.TYPE_COMIC
        return kavitaLayout?.isComic == true || allChapters.any { entryKind(it) == EntryKind.Issue }
    }

    /**
     * Puts the chapter number in front of names that don't contain it, so "Part 6" reads
     * "#24 · Part 6". Whole volumes and specials keep their names.
     */
    fun numberedName(context: Context, chapter: Chapter, isComic: Boolean): String {
        val kind = entryKind(chapter)
        if (kind != EntryKind.Chapter && kind != EntryKind.Issue) return chapter.name
        val number = kavitaInfo(chapter)?.number
            ?: chapter.chapter_number.takeIf { chapter.isRecognizedNumber }
            ?: return chapter.name
        val numbersInName = numberRegex.findAll(chapter.name).mapNotNull { it.value.toFloatOrNull() }
        if (numbersInName.any { it == number }) return chapter.name
        return context.getString(
            if (isComic) MR.strings.issue_numbered_name else MR.strings.chapter_numbered_name,
            numberFormat.format(number),
            chapter.name,
        )
    }

    /**
     * Show a row per volume when chapters fall into more than one group. Comic volumes named after
     * years (a 2016 relaunch) aren't books in a series, so those are left as a flat list.
     */
    fun showVolumeHeaders(chapters: List<Chapter>): Boolean {
        // Wait for the server rather than flashing headers guessed from names
        if (kavitaLayout == null && Injekt.get<TrackManager>().kavita.accept(source)) return false
        val volumes = chapters.map { volumeOf(it) }.toSet()
        if (volumes.any { it != null && it >= 1800f && it <= 2200f }) return false
        return volumes.size > 1 && volumes.any { it != null }
    }

    fun fetchChapters(andTracking: Boolean = true) {
        presenterScope.launch {
            getChapters()
            if (andTracking) fetchTracks()
            withUIContext { view?.updateChapters() }
            getHistory()
        }
    }

    fun setCurrentManga(manga: Manga?) {
//        currentMangaInternal.update { manga }
        this.manga = manga!!
    }

    suspend fun setCurrentExcludedScanlators(scanlators: Set<String>) {
        this.excludedScanlators = scanlators
        asyncUpdateMangaAndChapters(true)
    }

    // TODO: Use flow to "sync" data instead
    fun syncData() {
        chapterSort = ChapterSort(manga, chapterFilter, preferences)
        headerItem.apply {
            isTablet = view?.isTablet == true
            isLocked = isLockedFromSearch
        }
    }

    suspend fun getChaptersNow(): List<ChapterItem> {
        getChapters()
        return chapters
    }

    private suspend fun getChapters(queue: List<Download> = downloadManager.queueState.value) {
        val chapters = getChapter.awaitAll(mangaId, isScanlatorFiltered()).map { it.toModel() }
        allChapters = if (!isScanlatorFiltered()) chapters else getChapter.awaitAll(mangaId, false).map { it.toModel() }

        // Find downloaded chapters
        setDownloadedChapters(chapters, queue)
        allChapterScanlators = allChapters.mapNotNull { it.chapter.scanlator }.toSet()

        this.chapters = applyChapterFilters(chapters)
        getReadingSummary()
    }

    private suspend fun getReadingSummary() {
        try {
            readingSessions = getReadingSessions.awaitByManga(manga.source, manga.url).sortedBy { it.startedAt }
            readingSummary = SeriesReadingSummary.from(readingSessions)
            if (allReadingSessions == null) allReadingSessions = getReadingSessions.awaitAll()
            updateReadingTimeLeft()
        } catch (e: Exception) {
            Logger.e(e) { "Unable to load reading stats for ${manga.url}" }
            readingSummary = null
            readingTimeLeft = null
        }
    }

    /** Works out the time left again, such as once Kavita has said how long each entry is. */
    private fun updateReadingTimeLeft() {
        val all = allReadingSessions ?: return
        readingTimeLeft = ReadingPace.timeLeft(
            entries = unreadEntries(),
            seriesSessions = readingSessions,
            category = ReadingCategory.of(readingSessions.lastOrNull()?.seriesType ?: manga.seriesType(sourceManager = sourceManager)),
            allSessions = all,
        )
    }

    /**
     * What's left of each unread chapter, with its pages where Kavita or the reader knows them. Chapters that
     * share a number, such as from different scanlators, count once.
     */
    private fun unreadEntries(): List<UnreadEntry> = allChapters
        .filter { !it.read }
        .distinctBy { if (it.chapter_number >= 0f) it.chapter_number.toString() else it.url }
        .map { UnreadEntry.of(it.last_page_read, it.pages_left, pageCount(it.chapter)) }

    private suspend fun getHistory() {
        allHistory = getHistory.awaitAllByMangaId(mangaId)
    }

    /**
     * Finds and assigns the list of downloaded chapters.
     *
     * @param chapters the list of chapter from the database.
     */
    private fun setDownloadedChapters(chapters: List<ChapterItem>, queue: List<Download>) {
        for (chapter in chapters) {
            if (downloadManager.isChapterDownloaded(chapter, manga)) {
                chapter.status = Download.State.DOWNLOADED
            } else if (queue.isNotEmpty()) {
                chapter.status = queue.find { it.chapter.id == chapter.id }
                    ?.status ?: Download.State.default
            }
        }
    }

    /**
     * Converts a chapter from the database to an extended model, allowing to store new fields.
     */
    private fun Chapter.toModel(): ChapterItem {
        // Create the model object.
        val model = ChapterItem(this, manga)
        model.isLocked = isLockedFromSearch

        // Find an active download for this chapter.
        val download = downloadManager.queueState.value.find { it.chapter.id == id }

        if (download != null) {
            // If there's an active download, assign it.
            model.download = download
        }
        return model
    }

    /**
     * Whether the sorting method is descending or ascending.
     */
    fun sortDescending() = manga.sortDescending(preferences)

    fun sortingOrder() = manga.chapterOrder(preferences)

    /**
     * Applies the view filters to the list of chapters obtained from the database.
     * @param chapterList the list of chapters from the database
     * @return an observable of the list of chapters filtered and sorted.
     */
    private fun applyChapterFilters(chapterList: List<ChapterItem>): List<ChapterItem> {
        if (isLockedFromSearch) {
            return chapterList
        }
        getScrollType(chapterList)
        return chapterSort.getChaptersSorted(chapterList)
    }

    fun getChapterUrl(chapter: Chapter): String? {
        val source = source as? HttpSource ?: return null
        val chapterUrl = try { source.getChapterUrl(chapter) } catch (_: Exception) { null }
        return chapterUrl.takeIf { !it.isNullOrBlank() }
            ?: try { source.getChapterUrl(manga, chapter) } catch (_: Exception) { null }
    }

    private fun getScrollType(chapters: List<ChapterItem>) {
        scrollType = when {
            ChapterUtil.hasMultipleVolumes(chapters) -> MULTIPLE_VOLUMES
            ChapterUtil.hasMultipleSeasons(chapters) -> MULTIPLE_SEASONS
            ChapterUtil.hasTensOfChapters(chapters) -> TENS_OF_CHAPTERS
            else -> 0
        }
    }

    /**
     * Returns the next unread chapter or null if everything is read.
     */
    fun getNextUnreadChapter(): ChapterItem? {
        return chapterSort.getNextUnreadChapter(chapters)
    }

    fun anyRead(): Boolean = allChapters.any { it.read }
    fun hasBookmark(): Boolean = allChapters.any { it.bookmark }
    fun hasDownloads(): Boolean = allChapters.any { it.isDownloaded }

    fun getUnreadChaptersSorted() =
        chapters.filter { !it.read && it.status == Download.State.NOT_DOWNLOADED }.distinctBy { it.name }
            .sortedWith(chapterSort.sortComparator(true))

    fun startDownloadingNow(chapter: Chapter) {
        downloadManager.startDownloadNow(chapter)
    }

    /**
     * Downloads the given list of chapters with the manager.
     * @param chapters the list of chapters to download.
     */
    fun downloadChapters(chapters: List<ChapterItem>) {
        downloadManager.downloadChapters(manga, chapters.filter { !it.isDownloaded })
    }

    /**
     * Deletes the given list of chapter.
     * @param chapter the chapter to delete.
     */
    fun deleteChapter(chapter: ChapterItem) {
        this.chapters.find { it.id == chapter.id }?.apply {
            if (chapter.chapter.bookmark && !preferences.removeBookmarkedChapters().get()) return@apply
            status = Download.State.NOT_DOWNLOADED
            download = null
        }

        view?.updateChapters()

        downloadManager.deleteChapters(listOf(chapter), manga, source, true)
    }

    /**
     * Deletes the given list of chapter.
     * @param chapters the list of chapters to delete.
     */
    fun deleteChapters(chapters: List<ChapterItem>, update: Boolean = true, isEverything: Boolean = false) {
        chapters.forEach { chapter ->
            this.chapters.find { it.id == chapter.id }?.apply {
                if (chapter.chapter.bookmark && !preferences.removeBookmarkedChapters().get() && !isEverything) return@apply
                status = Download.State.NOT_DOWNLOADED
                download = null
            }
        }

        if (update) view?.updateChapters()

        if (isEverything) {
            downloadManager.deleteManga(manga, source)
        } else {
            downloadManager.deleteChapters(chapters, manga, source)
        }
    }

    suspend fun refreshMangaFromDb(): Manga {
        val dbManga = getManga.awaitById(mangaId)!!
        setCurrentManga(dbManga)
        return dbManga
    }

    private suspend fun fetchMangaUpdateFromSource(
        fetchDetails: Boolean = true,
        fetchChapters: Boolean = true,
        manualFetch: Boolean = true,
    ) {
        if (!fetchDetails && !fetchChapters) return
        withIOContext {
            val existingChapters = if (fetchChapters) {
                getChapter.awaitAll(mangaId, false)
            } else {
                emptyList()
            }

            // Combined fetch first; fall back to separate calls on failure.
            if (fetchDetails && fetchChapters) {
                try {
                    val update = source.getMangaUpdate(
                        manga = manga.copy(),
                        chapters = existingChapters,
                        fetchDetails = true,
                        fetchChapters = true,
                    )
                    applyMangaDetailsUpdate(update.manga)
                    applyChapterListUpdate(update.chapters, manualFetch)
                    return@withIOContext
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Fall through.
                }
            }

            if (fetchDetails) {
                try {
                    val networkManga = source.getMangaUpdate(
                        manga = manga.copy(),
                        chapters = emptyList(),
                        fetchDetails = true,
                        fetchChapters = false,
                    ).manga
                    applyMangaDetailsUpdate(networkManga)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e !is HttpException || e.code != 103) {
                        withUIContext {
                            view?.showError(trimException(e))
                        }
                    }
                }
            }

            if (fetchChapters) {
                try {
                    val networkChapters = source.getMangaUpdate(
                        manga = manga.copy(),
                        chapters = existingChapters,
                        fetchDetails = false,
                        fetchChapters = true,
                    ).chapters
                    applyChapterListUpdate(networkChapters, manualFetch)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    withUIContext {
                        view?.showError(trimException(e))
                    }
                }
            }
        }
    }

    private suspend fun applyMangaDetailsUpdate(networkManga: SManga) {
        manga.prepareCoverUpdate(coverCache, networkManga, false)
        manga.copyFrom(networkManga)
        manga.initialized = true
        updateManga.await(manga.toMangaUpdate())

        presenterScope.launchNonCancellableIO {
            val request =
                ImageRequest.Builder(preferences.context).data(manga.cover())
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.WRITE_ONLY)
                    .build()

            if (preferences.context.imageLoader.execute(request) is SuccessResult) {
                withUIContext {
                    view?.setPaletteColor()
                }
            }
        }
    }

    private suspend fun applyChapterListUpdate(networkChapters: List<SChapter>, manualFetch: Boolean) {
        val (added, removed) = syncChaptersWithSource(networkChapters, manga, source)
        if (added.isNotEmpty()) {
            if (manga.shouldDownloadNewChapters(preferences) && manualFetch) {
                downloadChapters(
                    added.sortedBy { it.chapter_number }
                        .map { it.toModel() },
                )
            }
            view?.view?.context?.let { mangaShortcutManager.updateShortcuts(it) }
        }
        if (removed.isNotEmpty()) {
            val removedChaptersId = removed.map { it.id }
            val removedChapters = chapters.filter {
                it.id in removedChaptersId && it.isDownloaded
            }
            if (removedChapters.isNotEmpty()) {
                withUIContext {
                    view?.showChaptersRemovedPopup(removedChapters)
                }
            }
        }
        getChapters()
        getHistory()
    }

    /** Refresh Manga Info and Chapter List (not tracking) */
    fun refreshAll() {
        val isLocal by lazy { manga.isLocal() }
        if (view?.isNotOnline() == true && !isLocal) return

        presenterScope.launch {
            isLoading = true
            fetchMangaUpdateFromSource(fetchDetails = true, fetchChapters = true, manualFetch = true)
            isLoading = false
            withUIContext {
                view?.updateChapters()
            }
            fetchKavitaLayout()
            fetchSuwayomiProgress()
            fetchSuwayomiServerTrackers()
            fetchServerDownloads()
        }
    }

    private fun trimException(e: java.lang.Exception): String {
        return (
            if (e !is SourceNotFoundException &&
                e.message?.contains(": ") == true
            ) {
                e.message?.split(": ")?.drop(1)
                    ?.joinToString(": ")
            } else {
                e.message
            }
            ) ?: view?.view?.context?.getString(MR.strings.unknown_error) ?: ""
    }

    /**
     * Bookmarks the given list of chapters.
     * @param selectedChapters the list of chapters to bookmark.
     */
    fun bookmarkChapters(selectedChapters: List<ChapterItem>, bookmarked: Boolean) {
        presenterScope.launchNonCancellableIO {
            val updates = selectedChapters.map {
                it.bookmark = bookmarked
                it.toProgressUpdate()
            }
            updateChapter.awaitAll(updates)
            presenterScope.launchNonCancellableIO {
                Injekt.get<TrackManager>().updateServerChaptersBookmarked(manga, selectedChapters.map { it.chapter }, bookmarked)
            }
            getChapters()
            withUIContext { view?.updateChapters() }
        }
    }

    /**
     * Mark the selected chapter list as read/unread.
     * @param selectedChapters the list of selected chapters.
     * @param read whether to mark chapters as read or unread.
     */
    fun markChaptersRead(
        selectedChapters: List<ChapterItem>,
        read: Boolean,
        deleteNow: Boolean = true,
        lastRead: Int? = null,
        pagesLeft: Int? = null,
    ) {
        presenterScope.launchNonCancellableIO {
            val updates = selectedChapters.map {
                it.read = read
                if (!read) {
                    it.last_page_read = lastRead ?: 0
                    it.pages_left = pagesLeft ?: 0
                }
                it.toProgressUpdate()
            }
            updateChapter.awaitAll(updates)
            presenterScope.launchNonCancellableIO {
                Injekt.get<TrackManager>().updateServerChaptersRead(manga, selectedChapters.map { it.chapter }, read)
            }
            if (read && deleteNow && preferences.removeAfterMarkedAsRead().get()) {
                deleteChapters(selectedChapters, false)
            }
            getChapters()
            withUIContext { view?.updateChapters() }
            if (read && deleteNow) {
                val latestReadChapter = selectedChapters.maxByOrNull { it.chapter_number.toInt() }?.chapter
                updateTrackChapterMarkedAsRead(preferences, latestReadChapter, manga.id) {
                    fetchTracks()
                }
            }
        }
    }

    /**
     * Sets the sorting order and requests an UI update.
     */
    fun setSortOrder(sort: Int, descend: Boolean) {
        manga.setChapterOrder(sort, if (descend) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC)
        if (mangaSortMatchesDefault()) {
            manga.setSortToGlobal()
        }
        presenterScope.launchNonCancellableIO { asyncUpdateMangaAndChapters() }
    }

    fun setGlobalChapterSort(sort: Int, descend: Boolean) {
        preferences.sortChapterOrder().set(sort)
        preferences.chaptersDescAsDefault().set(descend)
        manga.setSortToGlobal()
        presenterScope.launchNonCancellableIO { asyncUpdateMangaAndChapters() }
    }

    fun mangaSortMatchesDefault(): Boolean {
        return (
            manga.sortDescending == preferences.chaptersDescAsDefault().get() &&
                manga.sorting == preferences.sortChapterOrder().get()
            ) || !manga.usesLocalSort
    }

    fun mangaFilterMatchesDefault(): Boolean {
        return (
            manga.readFilter == preferences.filterChapterByRead().get() &&
                manga.downloadedFilter == preferences.filterChapterByDownloaded().get() &&
                manga.bookmarkedFilter == preferences.filterChapterByBookmarked().get() &&
                manga.hideChapterTitles == preferences.hideChapterTitlesByDefault().get()
            ) || !manga.usesLocalFilter
    }

    fun resetSortingToDefault() {
        manga.setSortToGlobal()
        presenterScope.launchNonCancellableIO { asyncUpdateMangaAndChapters() }
    }

    /**
     * Removes all filters and requests an UI update.
     */
    fun setFilters(
        unread: TriStateCheckBox.State,
        downloaded: TriStateCheckBox.State,
        bookmarked: TriStateCheckBox.State,
    ) {
        manga.readFilter = when (unread) {
            TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_UNREAD
            TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_READ
            else -> Manga.SHOW_ALL
        }
        manga.downloadedFilter = when (downloaded) {
            TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_DOWNLOADED
            TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_NOT_DOWNLOADED
            else -> Manga.SHOW_ALL
        }
        manga.bookmarkedFilter = when (bookmarked) {
            TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_BOOKMARKED
            TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_NOT_BOOKMARKED
            else -> Manga.SHOW_ALL
        }
        manga.setFilterToLocal()
        if (mangaFilterMatchesDefault()) {
            manga.setFilterToGlobal()
        }
        presenterScope.launchNonCancellableIO { asyncUpdateMangaAndChapters() }
    }

    /**
     * Sets the active display mode.
     * @param hide set title to hidden
     */
    fun hideTitle(hide: Boolean) {
        manga.displayMode = if (hide) Manga.CHAPTER_DISPLAY_NUMBER else Manga.CHAPTER_DISPLAY_NAME
        manga.setFilterToLocal()
        presenterScope.launchNonCancellableIO { updateManga.await(MangaUpdate(manga.id!!, chapterFlags = manga.chapter_flags)) }
        if (mangaFilterMatchesDefault()) {
            manga.setFilterToGlobal()
        }
        view?.refreshAdapter()
    }

    fun resetFilterToDefault() {
        manga.setFilterToGlobal()
        presenterScope.launchNonCancellableIO { asyncUpdateMangaAndChapters() }
    }

    fun setGlobalChapterFilters(
        unread: TriStateCheckBox.State,
        downloaded: TriStateCheckBox.State,
        bookmarked: TriStateCheckBox.State,
    ) {
        preferences.filterChapterByRead().set(
            when (unread) {
                TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_UNREAD
                TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_READ
                else -> Manga.SHOW_ALL
            },
        )
        preferences.filterChapterByDownloaded().set(
            when (downloaded) {
                TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_DOWNLOADED
                TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_NOT_DOWNLOADED
                else -> Manga.SHOW_ALL
            },
        )
        preferences.filterChapterByBookmarked().set(
            when (bookmarked) {
                TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_BOOKMARKED
                TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_NOT_BOOKMARKED
                else -> Manga.SHOW_ALL
            },
        )
        preferences.hideChapterTitlesByDefault().set(manga.hideChapterTitles)
        manga.setFilterToGlobal()
        presenterScope.launchNonCancellableIO { asyncUpdateMangaAndChapters() }
    }

    private suspend fun asyncUpdateMangaAndChapters(justChapters: Boolean = false) {
        if (!justChapters) updateManga.await(MangaUpdate(manga.id!!, chapterFlags = manga.chapter_flags))
        getChapters()
        withUIContext { view?.updateChapters() }
    }

    private fun isScanlatorFiltered() = excludedScanlators?.isNotEmpty() == true

    fun currentFilters(): String {
        val filtersId = mutableListOf<StringResource?>()
        filtersId.add(if (manga.readFilter(preferences) == Manga.CHAPTER_SHOW_READ) MR.strings.read else null)
        filtersId.add(if (manga.readFilter(preferences) == Manga.CHAPTER_SHOW_UNREAD) MR.strings.unread else null)
        filtersId.add(if (manga.downloadedFilter(preferences) == Manga.CHAPTER_SHOW_DOWNLOADED) MR.strings.downloaded else null)
        filtersId.add(if (manga.downloadedFilter(preferences) == Manga.CHAPTER_SHOW_NOT_DOWNLOADED) MR.strings.not_downloaded else null)
        filtersId.add(if (manga.bookmarkedFilter(preferences) == Manga.CHAPTER_SHOW_BOOKMARKED) MR.strings.bookmarked else null)
        filtersId.add(if (manga.bookmarkedFilter(preferences) == Manga.CHAPTER_SHOW_NOT_BOOKMARKED) MR.strings.not_bookmarked else null)
        filtersId.add(if (isScanlatorFiltered()) MR.strings.scanlators else null)
        return filtersId.filterNotNull()
            .joinToString(", ") { view?.view?.context?.getString(it) ?: "" }
    }

    fun setScanlatorFilter(filteredScanlators: Set<String>) {
        presenterScope.launchNonCancellableIO {
            val manga = manga
            MangaUtil.setScanlatorFilter(
                manga,
                if (filteredScanlators.size == allChapterScanlators.size) emptySet() else filteredScanlators
            )
            asyncUpdateMangaAndChapters(true)
        }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    fun getCategories(): List<Category> {
        return runBlocking { getCategories.await() }
    }

    fun confirmDeletion() {
        presenterScope.launchNonCancellableIO {
            manga.removeCover(coverCache)
            customMangaManager.saveMangaInfo(CustomMangaInfo(
                mangaId = manga.id!!,
                title = null,
                author = null,
                artist = null,
                description = null,
                genre = null,
                status = null,
            ))
            downloadManager.deleteManga(manga, source)
            asyncUpdateMangaAndChapters(true)
        }
    }

    private fun onUpdateManga() = fetchChapters()

    fun shareManga() {
        val context = Injekt.get<Application>()

        val destDir = UniFile.fromFile(context.cacheDir)!!.createDirectory("shared_image")!!

        presenterScope.launchIO {
            try {
                val uri = saveCover(destDir)
                withUIContext {
                    view?.shareManga(uri.uri.toFile())
                }
            } catch (_: java.lang.Exception) {
            }
        }
    }

    private fun saveImage(cover: Bitmap, directory: File, manga: Manga): File? {
        directory.mkdirs()

        // Build destination file.
        val filename = DiskUtil.buildValidFilename("${manga.title} - Cover.jpg")

        val destFile = File(directory, filename)
        val stream: OutputStream = FileOutputStream(destFile)
        cover.compress(Bitmap.CompressFormat.JPEG, 75, stream)
        stream.flush()
        stream.close()
        return destFile
    }

    fun updateManga(
        title: String?,
        author: String?,
        artist: String?,
        uri: Uri?,
        description: String?,
        tags: Array<String>?,
        status: Int?,
        seriesType: Int?,
        lang: String?,
        resetCover: Boolean = false,
    ) {
        if (manga.isLocal()) {
            manga.title = if (title.isNullOrBlank()) manga.url else title.trim()
            manga.author = author?.trimOrNull()
            manga.artist = artist?.trimOrNull()
            manga.description = description?.trimOrNull()
            val tagsString = tags?.joinToString(", ") { tag ->
                tag.replaceFirstChar {
                    it.uppercase(Locale.getDefault())
                }
            }
            manga.genre = if (tags.isNullOrEmpty()) null else tagsString?.trim()
            if (seriesType != null) {
                manga.genre = setSeriesType(seriesType, manga.genre).joinToString(", ") {
                    it.replaceFirstChar { genre ->
                        genre.titlecase(Locale.getDefault())
                    }
                }
                manga.viewer_flags = -1
                presenterScope.launchIO { updateManga.await(MangaUpdate(manga.id!!, viewerFlags = manga.viewer_flags)) }
            }
            manga.status = status ?: SManga.UNKNOWN
            LocalSource(downloadManager.context).updateMangaInfo(manga, lang)
            presenterScope.launchIO {
                updateManga.await(
                    MangaUpdate(
                        manga.id!!,
                        title = manga.ogTitle,
                        author = manga.originalAuthor,
                        artist = manga.originalArtist,
                        description = manga.originalDescription,
                        genres = manga.originalGenre?.split(", ").orEmpty(),
                        status = manga.ogStatus,
                    )
                )
            }
        } else {
            var genre = if (!tags.isNullOrEmpty() && tags.joinToString(", ") != manga.originalGenre) {
                tags.map { tag -> tag.replaceFirstChar { it.titlecase(Locale.getDefault()) } }
                    .toTypedArray()
            } else {
                null
            }
            if (seriesType != null) {
                genre = setSeriesType(seriesType, genre?.joinToString())
                manga.viewer_flags = -1
                presenterScope.launchIO { updateManga.await(MangaUpdate(manga.id!!, viewerFlags = manga.viewer_flags)) }
            }
            val manga = CustomMangaInfo(
                mangaId = manga.id!!,
                title?.trimOrNull(),
                author?.trimOrNull(),
                artist?.trimOrNull(),
                description?.trimOrNull(),
                genre?.joinToString(),
                if (status != this.manga.ogStatus) status else null,
            )
            launchNow {
                customMangaManager.saveMangaInfo(manga)
            }
        }
        if (uri != null) {
            editCoverWithStream(uri)
        } else if (resetCover) {
            coverCache.deleteCustomCover(manga)
            presenterScope.launchIO { manga.updateCoverLastModified() }
            view?.setPaletteColor()
        }
        view?.updateHeader()
    }

    private fun setSeriesType(seriesType: Int, genres: String? = null): Array<String> {
        val tags = (genres ?: manga.genre)?.split(",")?.map { it.trim() }?.toMutableList() ?: mutableListOf()
        tags.removeAll { manga.isSeriesTag(it) }
        when (seriesType) {
            Manga.TYPE_MANGA -> tags.add("Manga")
            Manga.TYPE_MANHUA -> tags.add("Manhua")
            Manga.TYPE_MANHWA -> tags.add("Manhwa")
            Manga.TYPE_COMIC -> tags.add("Comic")
            Manga.TYPE_WEBTOON -> tags.add("Webtoon")
        }
        return tags.toTypedArray()
    }

    fun editCoverWithStream(uri: Uri): Boolean {
        val inputStream =
            downloadManager.context.contentResolver.openInputStream(uri) ?: return false
        if (manga.isLocal()) {
            LocalSource.updateCover(manga, inputStream)
            presenterScope.launchNonCancellableIO { manga.updateCoverLastModified() }
            view?.setPaletteColor()
            return true
        }

        if (manga.favorite) {
            coverCache.setCustomCoverToCache(manga, inputStream)
            presenterScope.launchNonCancellableIO { manga.updateCoverLastModified() }
            view?.setPaletteColor()
            return true
        }
        return false
    }

    fun shareCover(): Uri? {
        return try {
            val destDir = UniFile.fromFile(coverCache.context.cacheDir)!!.createDirectory("shared_image")!!
            val file = saveCover(destDir)
            file.uri
        } catch (e: Exception) {
            null
        }
    }

    fun saveCover(): Boolean {
        return try {
            val directory = if (preferences.folderPerManga().get()) {
                storageManager.getCoversDirectory()!!.createDirectory(DiskUtil.buildValidFilename(manga.title))!!
            } else {
                storageManager.getCoversDirectory()!!
            }
            val file = saveCover(directory)
            DiskUtil.scanMedia(preferences.context, file)
            true
        } catch (e: Exception) {
            if (networkPreferences.verboseLogging().get()) Logger.e(e) { "Unable to save cover" }
            false
        }
    }

    private fun saveCover(directory: UniFile): UniFile {
        val cover = coverCache.getCustomCoverFile(manga).takeIf { it.exists() } ?: coverCache.getCoverFile(manga.thumbnail_url, !manga.favorite)
        val type = cover?.let { ImageUtil.findImageType(it.inputStream()) }
            ?: throw Exception("Not an image")

        // Build destination file.
        val filename = DiskUtil.buildValidFilename("${manga.title}.${type.extension}")

        val destFile = directory.createFile(filename)!!
        cover.inputStream().use { input ->
            destFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    fun isTracked(): Boolean =
        loggedServices.any { service -> tracks.any { it.sync_id == service.id } }

    fun hasTrackers(): Boolean = loggedServices.isNotEmpty()

    // Tracking
    private fun setTrackItems() {
        trackList = loggedServices.filter { service ->
            if (service !is EnhancedTrackService) return@filter true
            service.accept(source)
        }.map { service ->
            val track = tracks.find { it.sync_id == service.id }
            TrackItem(track, service, (service as? Suwayomi)?.serverTrackers(track).orEmpty())
        }
    }

    suspend fun fetchTracks() {
        tracks = withContext(Dispatchers.IO) { getTrack.awaitAllByMangaId(manga.id!!) }
        setTrackItems()
        withContext(Dispatchers.Main) { view?.refreshTracking(trackList) }
    }

    fun refreshTracking(showOfflineSnack: Boolean = false, trackIndex: Int? = null) {
        if (view?.isNotOnline(showOfflineSnack) == false) {
            presenterScope.launch {
                val asyncList = (trackIndex?.let { listOf(trackList[it]) } ?: trackList.filter { it.track != null })
                    .map { item ->
                        async(Dispatchers.IO) {
                            val trackItem = try {
                                item.service.refresh(item.track!!)
                            } catch (e: Exception) {
                                trackError(e)
                                null
                            }
                            if (trackItem != null) {
                                insertTrack.await(trackItem)
                                if (item.service.syncsChaptersOnRefresh) {
                                    syncChaptersWithTrackServiceTwoWay(chapters, trackItem, item.service)
                                }
                                trackItem
                            } else {
                                item.track
                            }
                        }
                    }
                asyncList.awaitAll()
                fetchTracks()
            }
        }
    }

    fun trackSearch(query: String, service: TrackService) {
        if (view?.isNotOnline() == false) {
            presenterScope.launch(Dispatchers.IO) {
                val results = try {
                    service.search(query)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { view?.trackSearchError(e) }
                    return@launch
                }
                withContext(Dispatchers.Main) { view?.onTrackSearchResults(results) }
            }
        }
    }

    fun registerTracking(item: Track?, service: TrackService) {
        if (item != null) {
            item.manga_id = manga.id!!

            presenterScope.launch {
                val binding = try {
                    service.bind(item)
                } catch (e: Exception) {
                    trackError(e)
                    null
                }
                withContext(Dispatchers.IO) {
                    if (binding != null) {
                        insertTrack.await(binding)
                    }

                    syncChaptersWithTrackServiceTwoWay(chapters, item, service)
                }
                fetchTracks()
            }
        }
    }

    fun removeTracker(trackItem: TrackItem, removeFromService: Boolean) {
        presenterScope.launch {
            withContext(Dispatchers.IO) {
                deleteTrack.awaitForManga(manga.id!!, trackItem.service.id)
                if (removeFromService && trackItem.service.canRemoveFromService()) {
                    trackItem.service.removeFromService(trackItem.track!!)
                }
            }
            fetchTracks()
        }
    }

    private fun updateRemote(track: Track, service: TrackService) {
        presenterScope.launch {
            val binding = try {
                service.update(track)
            } catch (e: Exception) {
                trackError(e)
                null
            }
            if (binding != null) {
                withContext(Dispatchers.IO) { insertTrack.await(binding) }
                fetchTracks()
            } else {
                trackRefreshDone()
            }
        }
    }

    private fun trackRefreshDone() {
        presenterScope.launch(Dispatchers.Main) { view?.trackRefreshDone() }
    }

    private fun trackError(error: Exception) {
        presenterScope.launch(Dispatchers.Main) { view?.trackRefreshError(error) }
    }

    fun setStatus(item: TrackItem, index: Int) {
        val track = item.track!!
        track.status = item.service.getStatusList()[index]
        if (item.service.isCompletedStatus(index) && track.total_chapters > 0L) {
            track.last_chapter_read = track.total_chapters.toFloat()
        }
        updateRemote(track, item.service)
    }

    fun setScore(item: TrackItem, index: Int) {
        val track = item.track!!
        track.score = item.service.indexToScore(index)
        updateRemote(track, item.service)
    }

    fun setLastChapterRead(item: TrackItem, chapterNumber: Int) {
        val track = item.track!!
        track.last_chapter_read = chapterNumber.toFloat()
        updateRemote(track, item.service)
    }

    fun setTrackerStartDate(item: TrackItem, date: Long) {
        val track = item.track!!
        track.started_reading_date = date
        updateRemote(track, item.service)
    }

    fun setTrackerFinishDate(item: TrackItem, date: Long) {
        val track = item.track!!
        track.finished_reading_date = date
        updateRemote(track, item.service)
    }

    suspend fun getSuggestedDate(readingDate: TrackingBottomSheet.ReadingDate): Long? {
        val chapters = getHistory.awaitAllByMangaId(manga.id ?: 0L)
        val date = when (readingDate) {
            TrackingBottomSheet.ReadingDate.Start -> chapters.minOfOrNull { it.last_read }
            TrackingBottomSheet.ReadingDate.Finish -> chapters.maxOfOrNull { it.last_read }
        } ?: return null
        return if (date <= 0L) null else date
    }

    override fun onStatusChange(download: Download) {
        super.onStatusChange(download)
        chapters.find { it.id == download.chapter.id }?.status = download.status
        onPageProgressUpdate(download)
    }

    private suspend fun onQueueUpdate(queue: List<Download>) = withIOContext {
        getChapters(queue)
        withUIContext {
            view?.updateChapters()
        }
    }

    override fun onQueueUpdate(download: Download) {
        // already handled by onStatusChange
    }

    override fun onProgressUpdate(download: Download) {
        // already handled by onStatusChange
    }

    override fun onPageProgressUpdate(download: Download) {
        chapters.find { it.id == download.chapter.id }?.download = download
        view?.updateChapterDownload(download)
    }

    companion object {
        const val MULTIPLE_VOLUMES = 1
        const val TENS_OF_CHAPTERS = 2
        const val MULTIPLE_SEASONS = 3

        /** Following the server's queue for up to 20 minutes is plenty for a few chapters. */
        private const val SERVER_DOWNLOAD_CHECKS = 240
        private val SERVER_DOWNLOAD_CHECK_INTERVAL = 5.seconds
        private const val SERVER_DOWNLOAD_SETTLE_CHECKS = 3
        private val SERVER_DOWNLOAD_SETTLE_INTERVAL = 2.seconds

        private val numberRegex = Regex("""[0-9]+(\.[0-9]+)?""")
        private val numberFormat = DecimalFormat("#.###", DecimalFormatSymbols().apply { decimalSeparator = '.' })
    }
}
