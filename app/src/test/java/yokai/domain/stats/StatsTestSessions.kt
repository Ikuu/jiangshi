package yokai.domain.stats

import java.time.LocalDateTime
import java.time.ZoneOffset

internal const val MINUTE = 60_000L

/** A session for tests, starting at [at] in UTC. */
internal fun testSession(
    at: LocalDateTime,
    minutes: Long,
    series: String = "a",
    chapter: String = "c1",
    type: Int = 1,
    pages: Int = 10,
    completed: Boolean = false,
): ReadingSession {
    val start = at.toInstant(ZoneOffset.UTC).toEpochMilli()
    return ReadingSession(
        mangaSource = 1L,
        mangaUrl = series,
        mangaTitle = series.uppercase(),
        seriesType = type,
        chapterUrl = chapter,
        chapterNumber = 1.0,
        startedAt = start,
        endedAt = start + minutes * MINUTE,
        duration = minutes * MINUTE,
        pagesRead = pages,
        completed = completed,
    )
}
