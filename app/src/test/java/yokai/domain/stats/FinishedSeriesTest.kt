package yokai.domain.stats

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FinishedSeriesTest {
    private val start = LocalDateTime.of(2026, 11, 20, 9, 0)

    @Test
    fun `a complete series is dated by the last chapter it took to finish`() {
        val sessions = listOf(
            testSession(start, 5, chapter = "c1", completed = true),
            testSession(start.plusHours(1), 5, chapter = "c3", completed = true),
            testSession(start.plusHours(2), 5, chapter = "c2", completed = true),
        )
        val finished = FinishedSeries.calculate(sessions, setOf(1L to "a")).single()

        assertEquals("c2", finished.chapterUrl)
        assertEquals(sessions[2].endedAt, finished.finishedAt)
        assertTrue(finished.finishedBy(sessions[2]))
    }

    @Test
    fun `reading a chapter again later doesn't move the date`() {
        val sessions = listOf(
            testSession(start, 5, chapter = "c1", completed = true),
            testSession(start.plusHours(1), 5, chapter = "c2", completed = true),
            testSession(start.plusDays(3), 5, chapter = "c1", completed = true),
        )
        val finished = FinishedSeries.calculate(sessions, setOf(1L to "a")).single()

        assertEquals(sessions[1].endedAt, finished.finishedAt)
    }

    @Test
    fun `only complete series with a chapter finished in a session count`() {
        val sessions = listOf(
            testSession(start, 5, series = "a", completed = true),
            testSession(start, 5, series = "b", completed = true),
            testSession(start, 5, series = "c", completed = false),
        )
        val finished = FinishedSeries.calculate(sessions, setOf(1L to "a", 1L to "c"))

        assertEquals(listOf("a"), finished.map { it.mangaUrl })
    }

    @Test
    fun `finished series reach milestones when they're finished`() {
        val sessions = (1..5).map { testSession(start.plusHours(it.toLong()), 5, series = "s$it", completed = true) }
        val finished = FinishedSeries.calculate(sessions, sessions.map { it.mangaSource to it.mangaUrl }.toSet())
        val milestones = Milestones.calculate(sessions, LocalDate.of(2026, 11, 20), ZoneOffset.UTC, finished)

        assertEquals(
            listOf(5L to sessions[4].endedAt, 1L to sessions[0].endedAt),
            milestones.reached.filter { it.kind == MilestoneKind.FINISHED }.map { it.value to it.reachedAt },
        )
        assertEquals(NextMilestone(MilestoneKind.FINISHED, 10, 5), milestones.next.single { it.kind == MilestoneKind.FINISHED })
    }

    @Test
    fun `reviews count series finished in their period`() {
        val sessions = listOf(
            testSession(LocalDateTime.of(2026, 10, 30, 9, 0), 5, series = "a", completed = true),
            testSession(LocalDateTime.of(2026, 11, 2, 9, 0), 5, series = "b", completed = true),
        )
        val finished = FinishedSeries.calculate(sessions, setOf(1L to "a", 1L to "b"))
        val review = ReadingReview.calculate(sessions, ReviewPeriod.Month(java.time.YearMonth.of(2026, 11)), ZoneOffset.UTC, finished)

        assertEquals(1, review.seriesFinished)
    }
}
