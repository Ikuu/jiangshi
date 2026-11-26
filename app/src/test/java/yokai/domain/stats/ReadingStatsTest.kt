package yokai.domain.stats

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadingStatsTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val minute = 60_000L

    // Wednesday 2026-11-18, 20:00
    private val now: Instant = LocalDateTime.of(2026, 11, 18, 20, 0).toInstant(ZoneOffset.UTC)

    private fun session(
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
            endedAt = start + minutes * minute,
            duration = minutes * minute,
            pagesRead = pages,
            completed = completed,
        )
    }

    private fun day(d: Int, hour: Int = 12) = LocalDateTime.of(2026, 11, d, hour, 0)

    @Test
    fun `week totals only count the last 7 days`() {
        val sessions = listOf(
            session(day(18), 10, completed = true),
            session(day(12), 20, series = "b", chapter = "c9", completed = true),
            session(day(11), 40), // 8 days ago, outside the week
        )
        val stats = ReadingStats.calculate(sessions, StatsPeriod.WEEK, now, zone)

        assertEquals(30 * minute, stats.totalTime)
        assertEquals(2, stats.chaptersFinished)
        assertEquals(20, stats.pagesRead)
        assertEquals(2, stats.seriesRead)
        assertEquals(2, stats.sessionCount)
        assertEquals(15 * minute, stats.averageSession)
        assertEquals(20 * minute, stats.longestSession)
        assertEquals(7, stats.timeline.size)
        assertEquals(LocalDate.of(2026, 11, 12), stats.timeline.first().start)
        assertEquals(20 * minute, stats.timeline.first().time)
        assertEquals(10 * minute, stats.timeline.last().time)
    }

    @Test
    fun `a chapter finished twice counts once`() {
        val sessions = listOf(
            session(day(17), 5, completed = true),
            session(day(18), 5, completed = true),
        )
        assertEquals(1, ReadingStats.calculate(sessions, StatsPeriod.WEEK, now, zone).chaptersFinished)
    }

    @Test
    fun `series and types are ranked by time`() {
        val sessions = listOf(
            session(day(18), 10, series = "a", type = 1),
            session(day(18), 30, series = "b", type = 4, completed = true),
            session(day(17), 15, series = "a", type = 1, chapter = "c2", completed = true),
        )
        val stats = ReadingStats.calculate(sessions, StatsPeriod.MONTH, now, zone)

        assertEquals(listOf("b", "a"), stats.series.map { it.mangaUrl })
        assertEquals(30 * minute, stats.series[0].time)
        assertEquals(25 * minute, stats.series[1].time)
        assertEquals(listOf(ReadingCategory.COMIC, ReadingCategory.MANGA), stats.types.map { it.category })
        assertEquals(25 * minute, stats.types[1].time)
        assertEquals(25 * minute, stats.types[1].timePerChapter)
    }

    @Test
    fun `manhwa, manhua and webtoons count as webtoons, and unread categories are left out`() {
        val sessions = listOf(
            session(day(18), 10, series = "a", type = 2), // manhwa
            session(day(18), 5, series = "b", type = 3), // manhua
            session(day(18), 20, series = "c", type = 5), // webtoon
            session(day(18), 15, series = "d", type = 4), // comic
        )
        val stats = ReadingStats.calculate(sessions, StatsPeriod.WEEK, now, zone)

        assertEquals(listOf(ReadingCategory.WEBTOON, ReadingCategory.COMIC), stats.types.map { it.category })
        assertEquals(35 * minute, stats.types[0].time)
        assertEquals(ReadingCategory.WEBTOON, stats.series.first { it.mangaUrl == "a" }.category)
    }

    @Test
    fun `time of day and day of week`() {
        val sessions = listOf(
            session(day(18, hour = 21), 10), // Wednesday
            session(day(16, hour = 7), 20), // Monday
        )
        val stats = ReadingStats.calculate(sessions, StatsPeriod.WEEK, now, zone)

        assertEquals(10 * minute, stats.hourOfDay[21])
        assertEquals(20 * minute, stats.hourOfDay[7])
        assertEquals(20 * minute, stats.dayOfWeek[0])
        assertEquals(10 * minute, stats.dayOfWeek[2])
    }

    @Test
    fun `year groups by month`() {
        val sessions = listOf(
            session(LocalDateTime.of(2025, 12, 3, 12, 0), 10),
            session(LocalDateTime.of(2026, 11, 1, 12, 0), 5),
            session(LocalDateTime.of(2025, 11, 30, 12, 0), 99), // before the 12 months
        )
        val stats = ReadingStats.calculate(sessions, StatsPeriod.YEAR, now, zone)

        assertEquals(TimelineUnit.MONTH, stats.timelineUnit)
        assertEquals(12, stats.timeline.size)
        assertEquals(LocalDate.of(2025, 12, 1), stats.timeline.first().start)
        assertEquals(10 * minute, stats.timeline.first().time)
        assertEquals(5 * minute, stats.timeline.last().time)
        assertEquals(15 * minute, stats.totalTime)
    }

    @Test
    fun `all time switches to years when there are too many months`() {
        val sessions = listOf(
            session(LocalDateTime.of(2023, 5, 1, 12, 0), 10),
            session(day(18), 5),
        )
        val stats = ReadingStats.calculate(sessions, StatsPeriod.ALL, now, zone)

        assertEquals(TimelineUnit.YEAR, stats.timelineUnit)
        assertEquals(listOf(2023, 2024, 2025, 2026), stats.timeline.map { it.start.year })
        assertEquals(15 * minute, stats.totalTime)
    }

    @Test
    fun `streaks`() {
        val today = LocalDate.of(2026, 11, 18)
        fun d(day: Int) = LocalDate.of(2026, 11, day)

        assertEquals(0 to 0, ReadingStats.streaks(emptySet(), today))
        assertEquals(3 to 3, ReadingStats.streaks(setOf(d(16), d(17), d(18)), today))
        // Nothing yet today, but yesterday keeps the streak alive
        assertEquals(2 to 4, ReadingStats.streaks(setOf(d(1), d(2), d(3), d(4), d(16), d(17)), today))
        assertEquals(0 to 1, ReadingStats.streaks(setOf(d(10)), today))
    }

    @Test
    fun `a day with under a minute of reading shows on the calendar but doesn't count towards the streak`() {
        val sessions = listOf(session(day(16), 10), session(day(17), 10)) + briefSession(day(18), 20)
        val stats = ReadingStats.calculate(sessions, StatsPeriod.ALL, now, zone)

        assertEquals(2, stats.currentStreak)
        assertEquals(20_000L, stats.readingDays[LocalDate.of(2026, 11, 18)])
    }

    @Test
    fun `empty when nothing was read`() {
        val stats = ReadingStats.calculate(emptyList(), StatsPeriod.ALL, now, zone)
        assertTrue(stats.isEmpty)
        assertEquals(1, stats.timeline.size)
    }
}
