package yokai.domain.stats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SeriesReadingSummaryTest {
    private fun session(start: Long, end: Long, duration: Long) = ReadingSession(
        mangaSource = 1L,
        mangaUrl = "/series",
        mangaTitle = "Series",
        seriesType = 1,
        chapterUrl = "/c$start",
        chapterNumber = 1.0,
        startedAt = start,
        endedAt = end,
        duration = duration,
        pagesRead = 1,
        completed = false,
    )

    @Test
    fun `nothing read gives no summary`() {
        assertNull(SeriesReadingSummary.from(emptyList()))
    }

    @Test
    fun `adds up time and finds the first and last read`() {
        val summary = SeriesReadingSummary.from(
            listOf(
                session(start = 5_000, end = 9_000, duration = 3_000),
                session(start = 1_000, end = 2_000, duration = 1_000),
                session(start = 20_000, end = 30_000, duration = 6_000),
            ),
        )!!

        assertEquals(10_000L, summary.time)
        assertEquals(3, summary.sessions)
        assertEquals(1_000L, summary.firstReadAt)
        assertEquals(30_000L, summary.lastReadAt)
    }
}
