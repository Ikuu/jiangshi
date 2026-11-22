package yokai.domain.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One series' reading, shown on its page. */
data class SeriesReadingSummary(
    val time: Long,
    val sessions: Int,
    val firstReadAt: Long,
    val lastReadAt: Long,
) {
    companion object {
        /** Null when the series hasn't been read since stats began. */
        fun from(sessions: List<ReadingSession>): SeriesReadingSummary? {
            if (sessions.isEmpty()) return null
            return SeriesReadingSummary(
                time = sessions.sumOf { it.duration },
                sessions = sessions.size,
                firstReadAt = sessions.minOf { it.startedAt },
                lastReadAt = sessions.maxOf { it.endedAt },
            )
        }
    }
}

/** One day's reading of a series, for its reading history. */
data class SeriesReadingDay(
    val date: LocalDate,
    val time: Long,
    val sessions: Int,
    val pagesRead: Int,
    /** Chapters read that day, in the order they were first opened. */
    val chapters: List<ReadingSession>,
    /** URLs of the chapters finished that day. */
    val finishedChapterUrls: Set<String>,
) {
    companion object {
        /** Newest day first. */
        fun from(sessions: List<ReadingSession>, zone: ZoneId): List<SeriesReadingDay> = sessions
            .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            .map { (date, daySessions) ->
                SeriesReadingDay(
                    date = date,
                    time = daySessions.sumOf { it.duration },
                    sessions = daySessions.size,
                    pagesRead = daySessions.sumOf { it.pagesRead },
                    chapters = daySessions.sortedBy { it.startedAt }.distinctBy { it.chapterUrl },
                    finishedChapterUrls = daySessions.filter { it.completed }.map { it.chapterUrl }.toSet(),
                )
            }
            .sortedByDescending { it.date }
    }
}
