package eu.kanade.tachiyomi.data.track

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.EntryKind
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uy.kohesive.injekt.injectLazy
import yokai.domain.chapter.interactor.GetChapter
import yokai.domain.chapter.interactor.UpdateChapter
import yokai.domain.manga.interactor.GetManga

/**
 * Pairs of series that are the same series on Kavita and on Suwayomi, such as volumes on one and newer
 * chapters on the other. Chapters marked read or unread in one are marked the same in the other, matching
 * them by chapter number, and the change is sent on to the other's server. Whole volumes and specials
 * have numbers of their own, so they're left out.
 *
 * Links are kept as "mangaId:mangaId" in [PreferencesHelper.linkedSeries].
 */
class LinkedSeries(private val trackManager: TrackManager) {
    private val preferences: PreferencesHelper by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val getChapter: GetChapter by injectLazy()
    private val updateChapter: UpdateChapter by injectLazy()
    private val lock = Mutex()

    private fun links(): Map<Long, Long> = preferences.linkedSeries().get()
        .mapNotNull { entry ->
            val (a, b) = entry.split(":").mapNotNull { it.toLongOrNull() }.takeIf { it.size == 2 } ?: return@mapNotNull null
            a to b
        }
        .flatMap { (a, b) -> listOf(a to b, b to a) }
        .toMap()

    /** The ID of the series [mangaId] is linked with, if any. */
    fun linkedTo(mangaId: Long): Long? = links()[mangaId]

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
        val changed = matchable(getChapter.awaitAll(otherId, false))
            .filter { it.chapter_number in numbers && it.read != read }
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

    companion object {
        /** Chapters that can be matched by number: not whole volumes or specials, and with a number. */
        internal fun matchable(chapters: List<Chapter>): List<Chapter> = chapters.filter { chapter ->
            chapter.chapter_number >= 0f && ChapterUtil.entryKind(chapter).let { it != EntryKind.Volume && it != EntryKind.Special }
        }
    }
}
