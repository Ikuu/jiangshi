package yokai.domain.stats

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import yokai.domain.stats.ChapterEndStatus.Nudge

class ChapterEndStatusTest {
    private val zone = ZoneOffset.UTC
    private val now = LocalDateTime.of(2026, 11, 18, 21, 0)

    private fun status(saved: List<ReadingSession>, current: ReadingSession?, goal: ReadingGoal?, since: LocalDateTime = now.minusHours(1)) =
        ChapterEndStatus.calculate(
            saved = saved,
            current = current,
            goal = goal,
            mangaSource = 1L,
            mangaUrl = "a",
            since = since.toInstant(zone).toEpochMilli(),
            now = now.toInstant(zone),
            zone = zone,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

    @Test
    fun `the chapter being read counts towards the goal`() {
        val goal = ReadingGoal(GoalPeriod.DAY, GoalMeasure.CHAPTERS, 3)
        val saved = listOf(testSession(now.minusHours(2), 10, chapter = "c1", completed = true))
        val current = testSession(now.minusMinutes(10), 10, chapter = "c2", completed = true)

        val status = status(saved, current, goal)

        assertEquals(2, status.goal!!.done)
        assertEquals(Nudge.Chapters(1, estimated = false), status.nudge)
        assertFalse(status.goalJustMet)
    }

    @Test
    fun `finishing the last chapter needed says the goal was just met`() {
        val goal = ReadingGoal(GoalPeriod.DAY, GoalMeasure.CHAPTERS, 2)
        val saved = listOf(testSession(now.minusHours(2), 10, chapter = "c1", completed = true))
        val current = testSession(now.minusMinutes(10), 10, chapter = "c2", completed = true)

        val status = status(saved, current, goal)

        assertTrue(status.goalJustMet)
        assertNull(status.nudge)
        // Already met before this chapter isn't news
        assertFalse(status(saved + current, testSession(now.minusMinutes(5), 5, chapter = "c3", completed = true), goal).goalJustMet)
    }

    @Test
    fun `a goal in minutes nudges for one more chapter when a chapter's worth is left`() {
        val goal = ReadingGoal(GoalPeriod.DAY, GoalMeasure.MINUTES, 30)
        // This series takes 10 minutes a chapter
        val saved = (1..3).map { testSession(now.minusDays(2).plusHours(it.toLong()), 10, chapter = "old$it", completed = true) } +
            testSession(now.minusHours(1), 12, chapter = "c1", completed = true)
        val current = testSession(now.minusMinutes(10), 10, chapter = "c2", completed = true)

        assertEquals(Nudge.Chapters(1, estimated = true), status(saved, current, goal).nudge)
    }

    @Test
    fun `a goal in minutes says how many chapters when a few are left`() {
        val goal = ReadingGoal(GoalPeriod.DAY, GoalMeasure.MINUTES, 60)
        val saved = (1..3).map { testSession(now.minusDays(2).plusHours(it.toLong()), 10, chapter = "old$it", completed = true) }
        val current = testSession(now.minusMinutes(30), 30, chapter = "c1", completed = true)

        // 30 minutes left at 15 minutes a chapter, an hour over four chapters
        assertEquals(Nudge.Chapters(2, estimated = true), status(saved, current, goal).nudge)
    }

    @Test
    fun `a goal in minutes without a pace counts down the last few minutes`() {
        val goal = ReadingGoal(GoalPeriod.DAY, GoalMeasure.MINUTES, 30)
        val current = testSession(now.minusMinutes(20), 20, chapter = "c1")

        assertEquals(Nudge.Minutes(10), status(emptyList(), current, goal).nudge)
    }

    @Test
    fun `far from the goal there's no nudge`() {
        val goal = ReadingGoal(GoalPeriod.WEEK, GoalMeasure.CHAPTERS, 20)
        val current = testSession(now.minusMinutes(10), 10, chapter = "c1", completed = true)

        assertNull(status(emptyList(), current, goal).nudge)
    }

    @Test
    fun `the streak counts today's reading`() {
        val saved = listOf(
            testSession(now.minusDays(2), 10, chapter = "c1"),
            testSession(now.minusDays(1), 10, chapter = "c2"),
        )
        val current = testSession(now.minusMinutes(10), 10, chapter = "c3")

        val status = status(saved, current, goal = null)

        assertEquals(3, status.streak)
        assertNull(status.goal)
        assertFalse(status.isEmpty)
    }

    @Test
    fun `only milestones reached since the reader opened are shown`() {
        val saved = (1..9).map { testSession(now.minusDays(1).plusMinutes(it * 10L), 5, chapter = "c$it", completed = true) }
        val current = testSession(now.minusMinutes(10), 10, chapter = "c10", completed = true)

        val status = status(saved, current, goal = null)

        assertEquals(listOf(MilestoneKind.CHAPTERS to 10L), status.milestones.map { it.kind to it.value })
    }
}
