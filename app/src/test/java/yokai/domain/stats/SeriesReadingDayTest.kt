package yokai.domain.stats

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SeriesReadingDayTest {
    @Test
    fun `sessions group into days, newest first, with each chapter once`() {
        val sessions = listOf(
            testSession(LocalDateTime.of(2026, 11, 17, 21, 0), 10, chapter = "c1", completed = true),
            testSession(LocalDateTime.of(2026, 11, 18, 8, 0), 5, chapter = "c2", pages = 4),
            testSession(LocalDateTime.of(2026, 11, 18, 20, 0), 15, chapter = "c2", pages = 6, completed = true),
            testSession(LocalDateTime.of(2026, 11, 18, 20, 30), 2, chapter = "c3", pages = 1),
        )
        val days = SeriesReadingDay.from(sessions, ZoneOffset.UTC)

        assertEquals(listOf(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 17)), days.map { it.date })
        val latest = days.first()
        assertEquals(22 * MINUTE, latest.time)
        assertEquals(3, latest.sessions)
        assertEquals(11, latest.pagesRead)
        assertEquals(listOf("c2", "c3"), latest.chapters.map { it.chapterUrl })
        assertEquals(setOf("c2"), latest.finishedChapterUrls)
    }
}
