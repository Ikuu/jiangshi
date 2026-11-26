package yokai.domain.stats

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadingReviewTest {
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
        assertEquals(listOf(2026, 2025), ReadingReview.years(sessions, zone).map { it.year })
    }

    @Test
    fun `totals and highlights cover only the year`() {
        val review = ReadingReview.calculate(sessions, ReviewPeriod.Year(2026), zone)

        assertEquals(165 * MINUTE, review.totalTime)
        assertEquals(3, review.chaptersFinished)
        assertEquals(5, review.sessionCount)
        assertEquals(4, review.daysRead)
        // 1, 2 and 3 January; 30 December was the year before
        assertEquals(3, review.longestStreak)
        assertEquals(LocalDate.of(2026, 3, 1), review.busiestMonth?.start)
        assertEquals(LocalDate.of(2026, 3, 10) to 105 * MINUTE, review.busiestDay)
        assertEquals(21, review.favouriteHour)
        assertEquals(12, review.timeline.size)
        assertEquals(listOf("new", "old"), review.series.map { it.mangaUrl })
        // "old" was first read in 2025
        assertEquals(1, review.seriesStarted)
        assertEquals(40 * MINUTE, review.previousTime)
    }

    @Test
    fun `a year with nothing read is empty`() {
        val review = ReadingReview.calculate(sessions, ReviewPeriod.Year(2024), zone)
        assertTrue(review.isEmpty)
        assertNull(review.busiestMonth)
        assertNull(review.busiestDay)
        assertNull(review.favouriteHour)
        assertNull(review.previousTime)
    }

    @Test
    fun `months with reading, newest first`() {
        assertEquals(
            listOf(YearMonth.of(2026, 3), YearMonth.of(2026, 1), YearMonth.of(2025, 12)),
            ReadingReview.months(sessions, zone).map { it.month },
        )
    }

    @Test
    fun `a month's totals cover only that month, with a bar for each day`() {
        val review = ReadingReview.calculate(sessions, ReviewPeriod.Month(YearMonth.of(2026, 1)), zone)

        assertEquals(60 * MINUTE, review.totalTime)
        assertEquals(2, review.chaptersFinished)
        assertEquals(3, review.daysRead)
        assertEquals(3, review.longestStreak)
        assertEquals(31, review.timeline.size)
        assertEquals(30 * MINUTE, review.timeline.first().time)
        assertNull(review.busiestMonth)
        assertEquals(LocalDate.of(2026, 1, 1) to 30 * MINUTE, review.busiestDay)
        // "new" was first read in January, "old" in December
        assertEquals(1, review.seriesStarted)
        // December 2025, the month before
        assertEquals(40 * MINUTE, review.previousTime)
    }

    @Test
    fun `a day with under a minute of reading counts as read but not towards the streak`() {
        val month = listOf(
            testSession(LocalDateTime.of(2026, 2, 1, 21, 0), 10, chapter = "c1"),
            briefSession(LocalDateTime.of(2026, 2, 2, 0, 30), 20, chapter = "c1"),
            testSession(LocalDateTime.of(2026, 2, 3, 21, 0), 10, chapter = "c2"),
        )
        val review = ReadingReview.calculate(month, ReviewPeriod.Month(YearMonth.of(2026, 2)), zone)

        assertEquals(3, review.daysRead)
        assertEquals(1, review.longestStreak)
    }

    @Test
    fun `the month before January is December of the year before`() {
        assertEquals(ReviewPeriod.Month(YearMonth.of(2025, 12)), ReviewPeriod.Month(YearMonth.of(2026, 1)).previous)
    }
}
