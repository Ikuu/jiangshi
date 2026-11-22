package yokai.domain.stats

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YearInReviewTest {
    private val zone = ZoneOffset.UTC

    private val sessions = listOf(
        testSession(LocalDateTime.of(2025, 12, 30, 21, 0), 40, series = "old", chapter = "o1", completed = true),
        testSession(LocalDateTime.of(2026, 1, 1, 21, 0), 30, series = "old", chapter = "o2", completed = true),
        testSession(LocalDateTime.of(2026, 1, 2, 21, 0), 20, series = "new", chapter = "n1", completed = true),
        testSession(LocalDateTime.of(2026, 1, 3, 9, 0), 10, series = "new", chapter = "n2"),
        testSession(LocalDateTime.of(2026, 3, 10, 21, 0), 90, series = "new", chapter = "n2", completed = true),
        testSession(LocalDateTime.of(2026, 3, 10, 22, 30), 15, series = "new", chapter = "n3"),
    )

    @Test
    fun `years with reading, newest first`() {
        assertEquals(listOf(2026, 2025), YearInReview.years(sessions, zone))
    }

    @Test
    fun `totals and highlights cover only the year`() {
        val review = YearInReview.calculate(sessions, 2026, zone)

        assertEquals(165 * MINUTE, review.totalTime)
        assertEquals(3, review.chaptersFinished)
        assertEquals(5, review.sessionCount)
        assertEquals(4, review.daysRead)
        // 1, 2 and 3 January; 30 December was the year before
        assertEquals(3, review.longestStreak)
        assertEquals(LocalDate.of(2026, 3, 1), review.busiestMonth?.start)
        assertEquals(LocalDate.of(2026, 3, 10) to 105 * MINUTE, review.busiestDay)
        assertEquals(21, review.favouriteHour)
        assertEquals(12, review.months.size)
        assertEquals(listOf("new", "old"), review.series.map { it.mangaUrl })
        // "old" was first read in 2025
        assertEquals(1, review.seriesStarted)
        assertEquals(40 * MINUTE, review.previousYearTime)
    }

    @Test
    fun `a year with nothing read is empty`() {
        val review = YearInReview.calculate(sessions, 2024, zone)
        assertTrue(review.isEmpty)
        assertNull(review.busiestMonth)
        assertNull(review.busiestDay)
        assertNull(review.favouriteHour)
        assertNull(review.previousYearTime)
    }
}
