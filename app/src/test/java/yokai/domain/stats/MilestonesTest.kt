package yokai.domain.stats

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MilestonesTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 20)

    private fun chapters(count: Int, from: LocalDateTime, series: String = "a") = (1..count).map {
        testSession(from.plusMinutes(it * 10L), 5, series = series, chapter = "c$it", pages = 20, completed = true)
    }

    @Test
    fun `the 10th chapter finished is dated by the session that finished it`() {
        val sessions = chapters(12, LocalDateTime.of(2026, 11, 20, 9, 0))
        val milestones = Milestones.calculate(sessions, today, zone)

        val tenth = milestones.reached.single { it.kind == MilestoneKind.CHAPTERS }
        assertEquals(10L, tenth.value)
        assertEquals(sessions[9].endedAt, tenth.reachedAt)
    }

    @Test
    fun `a chapter finished again doesn't count twice`() {
        val start = LocalDateTime.of(2026, 11, 20, 9, 0)
        val sessions = chapters(9, start) +
            testSession(start.plusHours(3), 5, chapter = "c1", completed = true)
        val milestones = Milestones.calculate(sessions, today, zone)

        assertTrue(milestones.reached.none { it.kind == MilestoneKind.CHAPTERS })
        assertEquals(NextMilestone(MilestoneKind.CHAPTERS, 10, 9), milestones.next.single { it.kind == MilestoneKind.CHAPTERS })
    }

    @Test
    fun `hours and pages add up across sessions`() {
        val start = LocalDateTime.of(2026, 11, 20, 9, 0)
        val sessions = listOf(
            testSession(start, 40, pages = 600),
            testSession(start.plusHours(1), 30, chapter = "c2", pages = 500),
        )
        val milestones = Milestones.calculate(sessions, today, zone)

        assertEquals(
            listOf(MilestoneKind.HOURS to 1L, MilestoneKind.PAGES to 1_000L),
            milestones.reached.map { it.kind to it.value }.sortedBy { it.first },
        )
        assertTrue(milestones.reached.all { it.reachedAt == sessions[1].endedAt })
    }

    @Test
    fun `streaks are reached on the day they get long enough, and only once`() {
        val days = (0L..3L).map { LocalDateTime.of(2026, 11, 1, 20, 0).plusDays(it) } +
            (0L..2L).map { LocalDateTime.of(2026, 11, 10, 20, 0).plusDays(it) }
        val sessions = days.mapIndexed { i, at -> testSession(at, 10, chapter = "c$i") }
        val milestones = Milestones.calculate(sessions, today, zone)

        val streaks = milestones.reached.filter { it.kind == MilestoneKind.STREAK }
        assertEquals(listOf(3L), streaks.map { it.value })
        assertEquals(sessions[2].startedAt, streaks.single().reachedAt)
        assertEquals(4 to LocalDate.of(2026, 11, 4), milestones.records.longestStreak)
    }

    @Test
    fun `a day with under a minute of reading breaks the streak`() {
        val sessions = listOf(
            testSession(LocalDateTime.of(2026, 11, 1, 20, 0), 10, chapter = "c1"),
            testSession(LocalDateTime.of(2026, 11, 2, 20, 0), 10, chapter = "c2"),
            briefSession(LocalDateTime.of(2026, 11, 3, 0, 30), 20, chapter = "c2"),
            testSession(LocalDateTime.of(2026, 11, 4, 20, 0), 10, chapter = "c3"),
        )
        val milestones = Milestones.calculate(sessions, today, zone)

        assertTrue(milestones.reached.none { it.kind == MilestoneKind.STREAK })
        assertEquals(2 to LocalDate.of(2026, 11, 2), milestones.records.longestStreak)
    }

    @Test
    fun `a streak is reached by the session that makes the day's reading enough`() {
        val sessions = listOf(
            testSession(LocalDateTime.of(2026, 11, 1, 20, 0), 10, chapter = "c1"),
            testSession(LocalDateTime.of(2026, 11, 2, 20, 0), 10, chapter = "c2"),
            briefSession(LocalDateTime.of(2026, 11, 3, 9, 0), 30, chapter = "c3"),
            briefSession(LocalDateTime.of(2026, 11, 3, 12, 0), 30, chapter = "c3"),
        )
        val streak = Milestones.calculate(sessions, today, zone).reached.single { it.kind == MilestoneKind.STREAK }

        assertEquals(3L, streak.value)
        assertEquals(sessions[3].startedAt, streak.reachedAt)
    }

    @Test
    fun `the next streak counts from the streak going now`() {
        // A 3 day streak ended a while ago, and a 2 day streak runs up to yesterday
        val sessions = listOf(1L, 2L, 3L, 18L, 19L).mapIndexed { i, day ->
            testSession(LocalDateTime.of(2026, 11, day.toInt(), 20, 0), 10, chapter = "c$i")
        }
        val next = Milestones.calculate(sessions, today, zone).next.single { it.kind == MilestoneKind.STREAK }

        assertEquals(NextMilestone(MilestoneKind.STREAK, 7, 2), next)
    }

    @Test
    fun `newest milestones come first`() {
        val sessions = chapters(10, LocalDateTime.of(2026, 11, 19, 9, 0)) +
            testSession(LocalDateTime.of(2026, 11, 20, 9, 0), 60, chapter = "long")
        val reached = Milestones.calculate(sessions, today, zone).reached

        assertEquals(MilestoneKind.HOURS, reached.first().kind)
    }

    @Test
    fun `records pick the longest session and the busiest day`() {
        val sessions = listOf(
            testSession(LocalDateTime.of(2026, 11, 18, 9, 0), 30, chapter = "c1", completed = true),
            testSession(LocalDateTime.of(2026, 11, 19, 9, 0), 20, chapter = "c2", completed = true),
            testSession(LocalDateTime.of(2026, 11, 19, 10, 0), 20, chapter = "c3", completed = true),
        )
        val records = Milestones.calculate(sessions, today, zone).records

        assertEquals(sessions[0], records.longestSession)
        assertEquals(LocalDate.of(2026, 11, 19) to 40 * MINUTE, records.busiestDay)
        assertEquals(LocalDate.of(2026, 11, 19) to 2, records.mostChaptersInADay)
    }

    @Test
    fun `nothing read means no milestones or records`() {
        val milestones = Milestones.calculate(emptyList(), today, zone)

        assertTrue(milestones.reached.isEmpty())
        assertNull(milestones.records.longestSession)
        assertNull(milestones.records.longestStreak)
        assertEquals(MilestoneKind.entries.size, milestones.next.size)
    }
}
