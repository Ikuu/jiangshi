package yokai.domain.stats

import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import yokai.domain.stats.ReadingStats.Companion.finishedChapters

/** The stretch of time a [ReadingReview] covers: a calendar year or month. Serializable to go in a screen's state. */
sealed interface ReviewPeriod : Serializable {
    /** The first day. */
    val start: LocalDate

    /** The day after the last. */
    val end: LocalDate

    /** The year or month before this one. */
    val previous: ReviewPeriod

    operator fun contains(day: LocalDate): Boolean = !day.isBefore(start) && day.isBefore(end)

    data class Year(val year: Int) : ReviewPeriod {
        override val start: LocalDate get() = LocalDate.of(year, 1, 1)
        override val end: LocalDate get() = start.plusYears(1)
        override val previous: Year get() = Year(year - 1)
    }

    data class Month(val month: YearMonth) : ReviewPeriod {
        override val start: LocalDate get() = month.atDay(1)
        override val end: LocalDate get() = start.plusMonths(1)
        override val previous: Month get() = Month(month.minusMonths(1))
    }
}

/** A summary of one year's or month's reading. */
data class ReadingReview(
    val period: ReviewPeriod,
    val totalTime: Long,
    val chaptersFinished: Int,
    val pagesRead: Int,
    val sessionCount: Int,
    val daysRead: Int,
    /** The longest run of days in a row with reading, within the period, counting only days per [Streak.counts]. */
    val longestStreak: Int,
    /** Time read in each month of a year, January first, or each day of a month. */
    val timeline: List<TimelineBar>,
    /** Null for a month, or with nothing read. */
    val busiestMonth: TimelineBar?,
    val busiestDay: Pair<LocalDate, Long>?,
    /** The hour of the day with the most reading, 0 to 23. */
    val favouriteHour: Int?,
    /** Most read first. */
    val series: List<SeriesStats>,
    /** Series first read in the period, rather than carried on from before. */
    val seriesStarted: Int,
    /** Series read to the end in the period. */
    val seriesFinished: Int,
    /** Most read first. */
    val types: List<TypeStats>,
    /** Time read in the period before, to compare with, or null if nothing was recorded then. */
    val previousTime: Long?,
) {
    val isEmpty: Boolean get() = sessionCount == 0

    companion object {
        /** Years with any reading, newest first. */
        fun years(sessions: List<ReadingSession>, zone: ZoneId): List<ReviewPeriod.Year> = sessions
            .map { ReviewPeriod.Year(Instant.ofEpochMilli(it.startedAt).atZone(zone).year) }
            .distinct()
            .sortedByDescending { it.year }

        /** Months with any reading, newest first. */
        fun months(sessions: List<ReadingSession>, zone: ZoneId): List<ReviewPeriod.Month> = sessions
            .map { ReviewPeriod.Month(YearMonth.from(Instant.ofEpochMilli(it.startedAt).atZone(zone))) }
            .distinct()
            .sortedByDescending { it.month }

        /** Periods of the same kind as [period] with any reading, newest first. */
        fun periodsLike(period: ReviewPeriod, sessions: List<ReadingSession>, zone: ZoneId): List<ReviewPeriod> = when (period) {
            is ReviewPeriod.Year -> years(sessions, zone)
            is ReviewPeriod.Month -> months(sessions, zone)
        }

        /** [finished] is every series read to the end, of which those finished in the period are counted. */
        fun calculate(
            allSessions: List<ReadingSession>,
            period: ReviewPeriod,
            zone: ZoneId,
            finished: List<FinishedSeries> = emptyList(),
        ): ReadingReview {
            fun ReadingSession.day(): LocalDate = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()

            val sessions = allSessions.filter { it.day() in period }
            val byDay = sessions.groupBy { it.day() }.mapValues { (_, day) -> day.sumOf { it.duration } }.filterValues { it > 0 }

            val timeline = when (period) {
                is ReviewPeriod.Year -> (1..12).map { month ->
                    val start = YearMonth.of(period.year, month).atDay(1)
                    TimelineBar(start, byDay.filterKeys { it.monthValue == month }.values.sum())
                }
                is ReviewPeriod.Month -> (1..period.month.lengthOfMonth()).map { day ->
                    val date = period.month.atDay(day)
                    TimelineBar(date, byDay[date] ?: 0L)
                }
            }

            val hours = LongArray(24)
            sessions.forEach { hours[Instant.ofEpochMilli(it.startedAt).atZone(zone).hour] += it.duration }

            val firstReadBySeries = allSessions
                .groupBy { it.mangaSource to it.mangaUrl }
                .mapValues { (_, seriesSessions) -> seriesSessions.minOf { it.startedAt } }
            val seriesStarted = firstReadBySeries.values.count { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() in period }

            val previous = period.previous
            val previousTime = allSessions.filter { it.day() in previous }.sumOf { it.duration }.takeIf { it > 0 }

            return ReadingReview(
                period = period,
                totalTime = sessions.sumOf { it.duration },
                chaptersFinished = sessions.finishedChapters(),
                pagesRead = sessions.sumOf { it.pagesRead },
                sessionCount = sessions.size,
                daysRead = byDay.size,
                longestStreak = ReadingStats.streaks(byDay.filterValues(Streak::counts).keys, period.end.minusDays(1)).second,
                timeline = timeline,
                busiestMonth = timeline.takeIf { period is ReviewPeriod.Year }?.filter { it.time > 0 }?.maxByOrNull { it.time },
                busiestDay = byDay.maxByOrNull { it.value }?.toPair(),
                favouriteHour = hours.indices.filter { hours[it] > 0 }.maxByOrNull { hours[it] },
                series = ReadingStats.seriesStats(sessions),
                seriesStarted = seriesStarted,
                seriesFinished = finished.count { Instant.ofEpochMilli(it.finishedAt).atZone(zone).toLocalDate() in period },
                types = ReadingStats.typeStats(sessions),
                previousTime = previousTime,
            )
        }
    }
}
