package yokai.domain.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadingGoalTest {
    private val zone = ZoneOffset.UTC

    // Wednesday 2026-11-18, 12:00
    private val now = LocalDateTime.of(2026, 11, 18, 12, 0).toInstant(ZoneOffset.UTC)

    @Test
    fun `goals survive being saved and read back`() {
        val goal = ReadingGoal(GoalPeriod.WEEK, GoalMeasure.CHAPTERS, 20)
        assertEquals(goal, ReadingGoal.decode(goal.encode()))
        assertNull(ReadingGoal.decode(""))
        assertNull(ReadingGoal.decode("WEEK:CHAPTERS:0"))
        assertNull(ReadingGoal.decode("FORTNIGHT:CHAPTERS:5"))
    }

    @Test
    fun `weekly chapters count distinct chapters finished since the week began`() {
        val sessions = listOf(
            testSession(LocalDateTime.of(2026, 11, 16, 9, 0), 10, chapter = "c1", completed = true),
            testSession(LocalDateTime.of(2026, 11, 17, 9, 0), 10, chapter = "c1", completed = true), // reread
            testSession(LocalDateTime.of(2026, 11, 18, 9, 0), 10, chapter = "c2", completed = true),
            testSession(LocalDateTime.of(2026, 11, 18, 10, 0), 10, chapter = "c3"), // not finished
            testSession(LocalDateTime.of(2026, 11, 15, 9, 0), 10, chapter = "c4", completed = true), // Sunday before
        )
        val progress = ReadingGoal(GoalPeriod.WEEK, GoalMeasure.CHAPTERS, 4).progress(sessions, now, zone, DayOfWeek.MONDAY)

        assertEquals(2, progress.done)
        assertEquals(LocalDate.of(2026, 11, 16), progress.periodStart)
        assertEquals(LocalDate.of(2026, 11, 23), progress.periodEnd)
        assertEquals(2, progress.remaining)
        assertFalse(progress.isMet)
        // 2.5 days into a 7 day week, steady progress would be about 1.4 chapters
        assertTrue(progress.isOnTrack)
    }

    @Test
    fun `weeks can start on Sunday`() {
        val sessions = listOf(
            testSession(LocalDateTime.of(2026, 11, 15, 9, 0), 10, chapter = "c4", completed = true),
        )
        val progress = ReadingGoal(GoalPeriod.WEEK, GoalMeasure.CHAPTERS, 4).progress(sessions, now, zone, DayOfWeek.SUNDAY)
        assertEquals(1, progress.done)
        assertEquals(LocalDate.of(2026, 11, 15), progress.periodStart)
    }

    @Test
    fun `monthly minutes fall behind when reading stalls`() {
        val sessions = listOf(testSession(LocalDateTime.of(2026, 11, 2, 9, 0), 30))
        val progress = ReadingGoal(GoalPeriod.MONTH, GoalMeasure.MINUTES, 600).progress(sessions, now, zone, DayOfWeek.MONDAY)

        assertEquals(30, progress.done)
        assertEquals(0.05f, progress.fraction, 0.001f)
        assertFalse(progress.isOnTrack)
    }

    @Test
    fun `a daily goal is met once today's reading reaches it`() {
        val sessions = listOf(
            testSession(LocalDateTime.of(2026, 11, 18, 8, 0), 20),
            testSession(LocalDateTime.of(2026, 11, 18, 11, 0), 15),
            testSession(LocalDateTime.of(2026, 11, 17, 23, 0), 50), // yesterday
        )
        val progress = ReadingGoal(GoalPeriod.DAY, GoalMeasure.MINUTES, 30).progress(sessions, now, zone, DayOfWeek.MONDAY)

        assertEquals(35, progress.done)
        assertTrue(progress.isMet)
        assertEquals(1f, progress.fraction)
    }
}
