package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.core.preference.Preference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Read marks made in the app that haven't reached a server yet, so they can be sent later, and the
 * server isn't trusted about those chapters until they are. Kept as "mangaId:chapter:read", where
 * chapter is however that server identifies a chapter.
 */
class PendingReadMarks(private val preference: Preference<Set<String>>) {

    data class Mark(val mangaId: Long, val chapter: String, val read: Boolean) {
        val entry get() = "$mangaId:$chapter:${if (read) "1" else "0"}"
    }

    private val lock = Mutex()

    fun all(): List<Mark> = preference.get().mapNotNull { entry ->
        val (id, chapter, read) = entry.split(":").takeIf { it.size == 3 } ?: return@mapNotNull null
        Mark(id.toLongOrNull() ?: return@mapNotNull null, chapter, read == "1")
    }

    /** Chapters of one series with a mark waiting, and whether it marks them read. */
    fun forManga(mangaId: Long): Map<String, Boolean> =
        all().filter { it.mangaId == mangaId }.associate { it.chapter to it.read }

    /** The latest mark for a chapter replaces any earlier one still waiting. */
    suspend fun add(mangaId: Long, chapters: Collection<String>, read: Boolean) = lock.withLock {
        val prefixes = chapters.map { "$mangaId:$it:" }
        val kept = preference.get().filterNot { entry -> prefixes.any(entry::startsWith) }
        preference.set(kept.toSet() + chapters.map { Mark(mangaId, it, read).entry })
    }

    /** Only clears marks matching what was sent, so a newer opposite mark made meanwhile stays. */
    suspend fun remove(mangaId: Long, chapters: Collection<String>, read: Boolean) = lock.withLock {
        preference.set(preference.get() - chapters.map { Mark(mangaId, it, read).entry }.toSet())
    }

    /** Drops marks that will never go through. */
    suspend fun discard(marks: Collection<Mark>) = lock.withLock {
        preference.set(preference.get() - marks.map { it.entry }.toSet())
    }
}
