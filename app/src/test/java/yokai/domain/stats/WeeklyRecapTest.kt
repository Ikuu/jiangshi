package yokai.domain.stats

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WeeklyRecapTest {
    // Sunday 22 November 2026, in the evening
    private val now = LocalDateTime.of(2026, 11, 22, 20, 0).toInstant(ZoneOffset.UTC)

    private fun recap(sessions: List<ReadingSession>, firstDay: DayOfWeek = DayOfWeek.MONDAY) =
        WeeklyRecap.of(sessions, now, ZoneOffset.UTC, firstDay)

    @Test
    fun `counts the week so far against the week before`() {
        val sessions = listOf(
            testSession(LocalDateTime.of(2026, 11, 10, 9, 0), 40),
            testSession(LocalDateTime.of(2026, 11, 16, 9, 0), 30, chapter = "c1", completed = true),
            testSession(LocalDateTime.of(2026, 11, 16, 21, 0), 15, chapter = "c2", completed = true),
            testSession(LocalDateTime.of(2026, 11, 22, 8, 0), 20, chapter = "c2", completed = true),
        )
        val result = recap(sessions)!!
        assertEquals(65 * MINUTE, result.time)
        assertEquals(2, result.chaptersFinished)
        assertEquals(2, result.daysRead)
        assertEquals(40 * MINUTE, result.previousTime)
    }

    @Test
    fun `weeks start on the locale's first day`() {
        // With weeks starting on Sunday, only today is this week, and Monday the 16th is last week
        val sessions = listOf(
            testSession(LocalDateTime.of(2026, 11, 16, 9, 0), 30),
            testSession(LocalDateTime.of(2026, 11, 22, 8, 0), 20),
        )
        val result = recap(sessions, DayOfWeek.SUNDAY)!!
        assertEquals(20 * MINUTE, result.time)
        assertEquals(30 * MINUTE, result.previousTime)
    }

    @Test
    fun `no recap without reading this week`() {
        assertNull(recap(listOf(testSession(LocalDateTime.of(2026, 11, 12, 9, 0), 30))))
    }
}
