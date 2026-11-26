package yokai.domain.stats.interactor

import eu.kanade.tachiyomi.source.model.SManga
import yokai.domain.chapter.interactor.GetChapter
import yokai.domain.manga.interactor.GetManga
import yokai.domain.stats.FinishedSeries
import yokai.domain.stats.ReadingSession

/** Series read to the end, from the reading sessions and what the database says is complete now. */
class GetFinishedSeries(
    private val getManga: GetManga,
    private val getChapter: GetChapter,
) {
    /**
     * Series that have finished publishing, been cancelled or completed, with every chapter read. A chapter counts
     * as read once a session in [sessions] finished it, even before the reader saves that, such as the chapter
     * just finished. Chapters from other scanlators with the same number count as one, and excluded scanlators
     * are left out, as in the unread count.
     */
    suspend fun await(sessions: List<ReadingSession>): List<FinishedSeries> {
        val finishedChapters = sessions.filter { it.completed }.groupBy({ it.mangaSource to it.mangaUrl }, { it.chapterUrl })
        if (finishedChapters.isEmpty()) return emptyList()
        val complete = getManga.awaitAll()
            .filter { (it.source to it.url) in finishedChapters && it.status in FINISHED_STATUSES }
            .filter { manga ->
                val finished = finishedChapters[manga.source to manga.url].orEmpty().toSet()
                val chapters = manga.id?.let { getChapter.awaitAll(it, true) }.orEmpty()
                chapters.isNotEmpty() &&
                    chapters
                        .groupBy { if (it.chapter_number >= 0f) it.chapter_number.toString() else it.url }
                        .values
                        .all { entries -> entries.any { it.read || it.url in finished } }
            }
            .map { it.source to it.url }
            .toSet()
        return FinishedSeries.calculate(sessions, complete)
    }

    companion object {
        val FINISHED_STATUSES = setOf(SManga.COMPLETED, SManga.PUBLISHING_FINISHED, SManga.CANCELLED)
    }
}
