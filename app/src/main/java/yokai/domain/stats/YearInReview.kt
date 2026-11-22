package yokai.domain.stats

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import yokai.domain.stats.ReadingStats.Companion.finishedChapters

/** A summary of one calendar year's reading. */
data class YearInReview(
    val year: Int,
    val totalTime: Long,
    val chaptersFinished: Int,
    val pagesRead: Int,
    val sessionCount: Int,
    val daysRead: Int,
    /** The longest run of days in a row with reading, within the year. */
    val longestStreak: Int,
    /** Time read in each month, January first. */
    val months: List<TimelineBar>,
    val busiestMonth: TimelineBar?,
    val busiestDay: Pair<LocalDate, Long>?,
    /** The hour of the day with the most reading, 0 to 23. */
    val favouriteHour: Int?,
    /** Most read first. */
    val series: List<SeriesStats>,
    /** Series first read this year, rather than carried on from before. */
    val seriesStarted: Int,
    /** Most read first. */
    val types: List<TypeStats>,
    /** Time read the year before, to compare with, or null if nothing was recorded then. */
    val previousYearTime: Long?,
) {
    val isEmpty: Boolean get() = sessionCount == 0

    companion object {
        /** Years with any reading, newest first. */
        fun years(sessions: List<ReadingSession>, zone: ZoneId): List<Int> =
            sessions.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).year }.distinct().sortedDescending()

        fun calculate(allSessions: List<ReadingSession>, year: Int, zone: ZoneId): YearInReview {
            fun ReadingSession.day(): LocalDate = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()

            val sessions = allSessions.filter { it.day().year == year }
            val byDay = sessions.groupBy { it.day() }.mapValues { (_, day) -> day.sumOf { it.duration } }.filterValues { it > 0 }

            val months = (1..12).map { month ->
                val start = YearMonth.of(year, month).atDay(1)
                TimelineBar(start, byDay.filterKeys { it.monthValue == month }.values.sum())
            }

            val hours = LongArray(24)
            sessions.forEach { hours[Instant.ofEpochMilli(it.startedAt).atZone(zone).hour] += it.duration }

            val firstReadBySeries = allSessions
                .groupBy { it.mangaSource to it.mangaUrl }
                .mapValues { (_, seriesSessions) -> seriesSessions.minOf { it.startedAt } }
            val seriesStarted = firstReadBySeries.values.count { Instant.ofEpochMilli(it).atZone(zone).year == year }

            val previousYearTime = allSessions.filter { it.day().year == year - 1 }.sumOf { it.duration }.takeIf { it > 0 }

            return YearInReview(
                year = year,
                totalTime = sessions.sumOf { it.duration },
                chaptersFinished = sessions.finishedChapters(),
                pagesRead = sessions.sumOf { it.pagesRead },
                sessionCount = sessions.size,
                daysRead = byDay.size,
                longestStreak = ReadingStats.streaks(byDay.keys, LocalDate.of(year, 12, 31)).second,
                months = months,
                busiestMonth = months.filter { it.time > 0 }.maxByOrNull { it.time },
                busiestDay = byDay.maxByOrNull { it.value }?.toPair(),
                favouriteHour = hours.indices.filter { hours[it] > 0 }.maxByOrNull { hours[it] },
                series = ReadingStats.seriesStats(sessions),
                seriesStarted = seriesStarted,
                types = ReadingStats.typeStats(sessions),
                previousYearTime = previousYearTime,
            )
        }
    }
}
