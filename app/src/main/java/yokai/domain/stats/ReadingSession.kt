package yokai.domain.stats

import java.time.Instant
import java.time.ZoneId

/**
 * A stretch of reading one chapter. [duration] is active reading time in milliseconds, with long gaps
 * between page turns capped (see [ReadingSessionTimer]), so it can be shorter than [endedAt] - [startedAt].
 */
data class ReadingSession(
    val mangaSource: Long,
    val mangaUrl: String,
    val mangaTitle: String,
    val seriesType: Int,
    val chapterUrl: String,
    val chapterNumber: Double,
    val startedAt: Long,
    val endedAt: Long,
    val duration: Long,
    val pagesRead: Int,
    val completed: Boolean,
    val id: Long? = null,
) {
    /**
     * Whether [next], the same chapter read again soon after this ended, should be added to this rather than
     * kept as a session of its own. The reader ends a session whenever it's paused, such as the screen going off
     * or the tablet's cover closing, and whenever it moves to another chapter, so one sitting of reading would
     * otherwise be split into many. Sessions on different days are kept apart, so each day keeps its own time.
     */
    fun canMergeWith(next: ReadingSession, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        mangaSource == next.mangaSource &&
            mangaUrl == next.mangaUrl &&
            chapterUrl == next.chapterUrl &&
            next.startedAt >= startedAt &&
            next.startedAt - endedAt <= MERGE_GAP &&
            Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate() ==
            Instant.ofEpochMilli(next.startedAt).atZone(zone).toLocalDate()

    /** This session with [next] added: the time and pages of both, finished if either finished the chapter. */
    fun mergedWith(next: ReadingSession): ReadingSession = copy(
        endedAt = maxOf(endedAt, next.endedAt),
        duration = duration + next.duration,
        pagesRead = pagesRead + next.pagesRead,
        completed = completed || next.completed,
    )

    companion object {
        /** How long after a chapter's session ends that reading it again still counts as the same session. */
        const val MERGE_GAP = 15 * 60 * 1000L

        @Suppress("UNUSED_PARAMETER")
        fun mapper(
            id: Long,
            mangaSource: Long,
            mangaUrl: String,
            mangaTitle: String,
            seriesType: Long,
            chapterUrl: String,
            chapterNumber: Double,
            startedAt: Long,
            endedAt: Long,
            duration: Long,
            pagesRead: Long,
            completed: Long,
        ) = ReadingSession(
            mangaSource = mangaSource,
            mangaUrl = mangaUrl,
            mangaTitle = mangaTitle,
            seriesType = seriesType.toInt(),
            chapterUrl = chapterUrl,
            chapterNumber = chapterNumber,
            startedAt = startedAt,
            endedAt = endedAt,
            duration = duration,
            pagesRead = pagesRead.toInt(),
            completed = completed != 0L,
            id = id,
        )
    }
}
