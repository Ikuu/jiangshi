package eu.kanade.tachiyomi.data.track

import co.touchlab.kermit.Logger
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.create
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.EntryKind
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uy.kohesive.injekt.injectLazy
import yokai.domain.chapter.interactor.GetChapter
import yokai.domain.chapter.interactor.UpdateChapter
import yokai.domain.manga.interactor.GetLibraryManga
import yokai.domain.manga.interactor.GetManga
import yokai.domain.manga.interactor.InsertManga
import yokai.util.normalizedLevenshteinSimilarity

/**
 * Pairs of series that are the same series on Kavita and on Suwayomi, such as volumes on one and newer
 * chapters on the other. Chapters marked read or unread in one are marked the same in the other, matching
 * them by chapter number, and the change is sent on to the other's server. Whole volumes and specials
 * have numbers of their own, so they're left out.
 *
 * Either series can be linked without being in the library: it only needs to be in the database, which
 * clearing the database leaves alone while it's linked. Its chapters aren't kept up to date by library
 * updates, so they're fetched from its server when linking, and again when a chapter to mark isn't there.
 *
 * Links are kept as "mangaId:mangaId" in [PreferencesHelper.linkedSeries].
 */
class LinkedSeries(private val trackManager: TrackManager) {
    private val preferences: PreferencesHelper by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val getChapter: GetChapter by injectLazy()
    private val updateChapter: UpdateChapter by injectLazy()
    private val insertManga: InsertManga by injectLazy()
    private val getLibraryManga: GetLibraryManga by injectLazy()
    private val lock = Mutex()

    /** When each linked series last had its chapters fetched for a missing chapter, so it isn't done every mark. */
    private val lastRefreshed = mutableMapOf<Long, Long>()

    private fun links(): Map<Long, Long> = preferences.linkedSeries().get()
        .mapNotNull { entry ->
            val (a, b) = entry.split(":").mapNotNull { it.toLongOrNull() }.takeIf { it.size == 2 } ?: return@mapNotNull null
            a to b
        }
        .flatMap { (a, b) -> listOf(a to b, b to a) }
        .toMap()

    /** The ID of the series [mangaId] is linked with, if any. */
    fun linkedTo(mangaId: Long): Long? = links()[mangaId]

    /** Every series in a link, which clearing the database has to keep. */
    fun linkedIds(): Set<Long> = links().keys

    /** Whether [manga] comes from one of the servers series can be linked across. */
    fun canLink(manga: Manga): Boolean = serverOf(manga) != null

    /** Whether [candidate] comes from the other server to [manga], so the two could be linked. */
    fun canLinkTogether(manga: Manga, candidate: Manga): Boolean {
        val server = serverOf(manga) ?: return false
        val other = serverOf(candidate) ?: return false
        return server != other
    }

    private fun serverOf(manga: Manga): TrackService? {
        val source = trackManager.sourceManager.getOrStub(manga.source)
        return listOf(trackManager.kavita, trackManager.suwayomi).find { it.accept(source) }
    }

    /** A series on the other server that could be linked with: one in the library, or one found on the server. */
    class Candidate(val title: String, val inLibrary: Boolean, internal val sourceId: Long, internal val sManga: SManga)

    /**
     * Series from the other server to [manga]'s that it could be linked with, closest titles first: ones found
     * searching that server for [query], and ones in the library whose titles match it. A server that can't be
     * searched is skipped, so matching library ones still show.
     */
    suspend fun candidates(manga: Manga, query: String = manga.title): List<Candidate> = coroutineScope {
        val server = serverOf(manga) ?: return@coroutineScope emptyList()
        val other = listOf(trackManager.kavita, trackManager.suwayomi).first { it != server }
        val inLibrary = getLibraryManga.await()
            .map { it.manga }
            .distinctBy { it.id }
            .filter { canLinkTogether(manga, it) }
        val libraryKeys = inLibrary.map { it.source to it.url }.toSet()
        val library = inLibrary
            .filter { titleMatches(it.title, query) }
            .map { Candidate(it.title, true, it.source, it) }
        // A series found on the server that's in the library too is shown as in the library
        val found = trackManager.sourceManager.getCatalogueSources()
            .filter { other.accept(it) }
            .map { source -> async { search(source, query) } }
            .awaitAll()
            .flatten()
            .map { if ((it.sourceId to it.sManga.url) in libraryKeys) Candidate(it.title, true, it.sourceId, it.sManga) else it }
        val title = query.lowercase()
        (library + found)
            .distinctBy { it.sourceId to it.sManga.url }
            .sortedByDescending { normalizedLevenshteinSimilarity(it.title.lowercase(), title) }
    }

    private suspend fun search(source: CatalogueSource, query: String): List<Candidate> = try {
        source.getSearchManga(1, query, source.getFilterList()).mangas.map { Candidate(it.title, false, source.id, it) }
    } catch (e: Exception) {
        Logger.w(e) { "Could not search ${source.name} for series to link with" }
        emptyList()
    }

    /** The series in the database for [candidate], adding it if it isn't there yet. It isn't added to the library. */
    suspend fun seriesFor(candidate: Candidate): Manga {
        (candidate.sManga as? Manga)?.takeIf { it.id != null }?.let { return it }
        getManga.awaitByUrlAndSource(candidate.sManga.url, candidate.sourceId)?.let { return it }
        val manga = Manga.create(candidate.sManga.url, candidate.sManga.title, candidate.sourceId)
        manga.copyFrom(candidate.sManga)
        manga.id = insertManga.await(manga)
        return manga
    }

    /** Fetches [manga]'s chapters from its server, which a series outside the library doesn't get from library updates. */
    private suspend fun refreshChapters(manga: Manga) {
        val mangaId = manga.id ?: return
        val source = trackManager.sourceManager.get(manga.source) ?: return
        try {
            val chapters = source.getMangaUpdate(
                manga = manga.copy(),
                chapters = getChapter.awaitAll(mangaId, false),
                fetchDetails = false,
                fetchChapters = true,
            ).chapters
            if (chapters.isNotEmpty()) syncChaptersWithSource(chapters, manga, source)
        } catch (e: Exception) {
            Logger.w(e) { "Could not fetch the chapters of ${manga.url}, linked with another series" }
        }
    }

    /**
     * Links two series, replacing any links either had, and brings them into step: a chapter read in
     * either ends up read in both. Returns how many chapters that marked read.
     */
    suspend fun link(manga: Manga, other: Manga): Int {
        val mangaId = manga.id ?: return 0
        val otherId = other.id ?: return 0
        lock.withLock {
            val kept = preferences.linkedSeries().get().filterNot { entry ->
                entry.split(":").any { it == mangaId.toString() || it == otherId.toString() }
            }
            preferences.linkedSeries().set(kept.toSet() + "$mangaId:$otherId")
        }
        refreshChapters(other)
        val readHere = getChapter.awaitAll(mangaId, false).filter { it.read }
        val readThere = getChapter.awaitAll(otherId, false).filter { it.read }
        return copyMarks(manga, readHere, other, true).size + copyMarks(other, readThere, manga, true).size
    }

    /** Takes off the link [mangaId] has, if any. */
    suspend fun unlink(mangaId: Long) = lock.withLock {
        preferences.linkedSeries().set(
            preferences.linkedSeries().get().filterNot { entry -> entry.split(":").any { it == mangaId.toString() } }.toSet(),
        )
    }

    /**
     * Marks the chapters matching [chapters] read or unread in the series [manga] is linked with, and sends
     * that to its server. Doesn't go any further, so it can't come back round to [manga].
     */
    suspend fun markLinked(manga: Manga, chapters: List<Chapter>, read: Boolean) {
        val otherId = manga.id?.let(::linkedTo) ?: return
        try {
            val other = getManga.awaitById(otherId) ?: return
            copyMarks(manga, chapters, other, read)
        } catch (e: Exception) {
            Logger.w(e) { "Could not mark chapters in the series linked with ${manga.url}" }
        }
    }

    /** Marks [other]'s chapters with the same numbers as [chapters] read or unread. Returns those it changed. */
    private suspend fun copyMarks(manga: Manga, chapters: List<Chapter>, other: Manga, read: Boolean): List<Chapter> {
        val otherId = other.id ?: return emptyList()
        val numbers = matchable(chapters).map { it.chapter_number }.toSet()
        if (numbers.isEmpty()) return emptyList()
        var theirs = matchable(getChapter.awaitAll(otherId, false))
        if (!numbers.all { number -> theirs.any { it.chapter_number == number } } && shouldRefresh(otherId)) {
            refreshChapters(other)
            theirs = matchable(getChapter.awaitAll(otherId, false))
        }
        val changed = theirs.filter { it.chapter_number in numbers && it.read != read }
        if (changed.isEmpty()) return emptyList()
        changed.forEach { chapter ->
            chapter.read = read
            if (!read) {
                chapter.last_page_read = 0
                chapter.pages_left = 0
            }
        }
        updateChapter.awaitAll(changed.map { it.toProgressUpdate() })
        Logger.d { "Marked ${changed.size} chapters ${if (read) "read" else "unread"} in ${other.url}, linked with ${manga.url}" }
        trackManager.suwayomi.updateChaptersRead(other, changed.map { it.chapter_number }, read)
        trackManager.kavita.updateChaptersRead(other, changed, read)
        return changed
    }

    /** A chapter to carry on with in a linked series, from [server]. */
    class Next(val manga: Manga, val chapter: Chapter, val server: StringResource)

    /**
     * The chapter to carry on with in the series [manga] is linked with, once [after], the last chapter in [manga],
     * is read: the first unread one past both [after] and the furthest chapter read there, as marks are kept the
     * same in both. Null when [manga] isn't linked or the other has nothing further, and when neither says how far
     * the reading has got, such as after a whole volume with nothing read in the other. A series outside the
     * library has its chapters fetched again when nothing further is found, at most once an hour.
     */
    suspend fun nextChapter(manga: Manga, after: Chapter): Next? {
        val otherId = manga.id?.let(::linkedTo) ?: return null
        return try {
            val other = getManga.awaitById(otherId) ?: return null
            val server = serverOf(other) ?: return null
            val reachedHere = matchable(listOf(after)).firstOrNull()?.chapter_number
            suspend fun find(): Chapter? {
                val theirs = matchable(getChapter.awaitAll(otherId, true))
                val reached = listOfNotNull(reachedHere, theirs.filter { it.read }.maxOfOrNull { it.chapter_number }).maxOrNull()
                    ?: return null
                return theirs.filter { !it.read && it.chapter_number > reached }.minByOrNull { it.chapter_number }
            }
            val next = find() ?: if (!other.favorite && shouldRefresh(otherId)) {
                refreshChapters(other)
                find()
            } else {
                null
            }
            next?.let { Next(other, it, server.nameRes()) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(e) { "Could not find the next chapter in the series linked with ${manga.url}" }
            null
        }
    }

    /**
     * Whether [mangaId]'s chapters can be fetched again for a missing chapter. Often the other server doesn't
     * have it at all, such as a newer chapter than its last volume, so this is held to once an hour.
     */
    private fun shouldRefresh(mangaId: Long): Boolean = synchronized(lastRefreshed) {
        val now = System.currentTimeMillis()
        if (now - (lastRefreshed[mangaId] ?: 0L) < REFRESH_INTERVAL) return false
        lastRefreshed[mangaId] = now
        true
    }

    companion object {
        private const val REFRESH_INTERVAL = 60 * 60 * 1000L
        private const val MIN_TITLE_SIMILARITY = 0.6

        private val notWord = Regex("[^\\p{L}\\p{N}]+")

        private fun normalize(title: String) = title.lowercase().replace(notWord, " ").trim()

        /**
         * Whether a library series called [title] matches what was searched for, so only those are offered
         * alongside the server's results: one title contains the other, or they're close, allowing for typos.
         */
        internal fun titleMatches(title: String, query: String): Boolean {
            val a = normalize(title)
            val b = normalize(query)
            if (a.isEmpty() || b.isEmpty()) return false
            return a.contains(b) || b.contains(a) || normalizedLevenshteinSimilarity(a, b) >= MIN_TITLE_SIMILARITY
        }

        /** Chapters that can be matched by number: not whole volumes or specials, and with a number. */
        internal fun matchable(chapters: List<Chapter>): List<Chapter> = chapters.filter { chapter ->
            chapter.chapter_number >= 0f && ChapterUtil.entryKind(chapter).let { it != EntryKind.Volume && it != EntryKind.Special }
        }
    }
}
