package yokai.domain.stats

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class StatsPeriod { WEEK, MONTH, YEAR, ALL }

/** How long the bars on the timeline are. */
enum class TimelineUnit { DAY, MONTH, YEAR }

data class TimelineBar(val start: LocalDate, val time: Long)

data class SeriesStats(
    val mangaSource: Long,
    val mangaUrl: String,
    val title: String,
    val seriesType: Int,
    val time: Long,
    val chaptersFinished: Int,
    val pagesRead: Int,
    val lastReadAt: Long,
) {
    val category: ReadingCategory get() = ReadingCategory.of(seriesType)
}

data class TypeStats(
    val category: ReadingCategory,
    val time: Long,
    val chaptersFinished: Int,
    val pagesRead: Int,
) {
    /** Average time per finished chapter, or null with nothing finished. */
    val timePerChapter: Long? get() = if (chaptersFinished > 0) time / chaptersFinished else null

    /** Pages per minute, or null with too little time to say. */
    val pagesPerMinute: Double? get() = if (time >= 60_000L && pagesRead > 0) pagesRead / (time / 60_000.0) else null
}

/** Stats for one period. Streaks and [readingDays] always cover all time. */
data class ReadingStats(
    val period: StatsPeriod,
    val totalTime: Long,
    val chaptersFinished: Int,
    val pagesRead: Int,
    val seriesRead: Int,
    val sessionCount: Int,
    val averageSession: Long,
    val longestSession: Long,
    val currentStreak: Int,
    val longestStreak: Int,
    val timelineUnit: TimelineUnit,
    val timeline: List<TimelineBar>,
    /** Time read in each hour of the day, 0 to 23. */
    val hourOfDay: List<Long>,
    /** Time read on each day of the week, Monday first. */
    val dayOfWeek: List<Long>,
    /** Time read on each day with any reading, all time. */
    val readingDays: Map<LocalDate, Long>,
    /** Most read first. */
    val series: List<SeriesStats>,
    /** Only the categories read in the period, most read first. */
    val types: List<TypeStats>,
) {
    val isEmpty: Boolean get() = sessionCount == 0

    companion object {
        fun calculate(
            allSessions: List<ReadingSession>,
            period: StatsPeriod,
            now: Instant,
            zone: ZoneId,
        ): ReadingStats {
            val today = now.atZone(zone).toLocalDate()
            fun ReadingSession.day() = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()

            val periodStart: LocalDate? = when (period) {
                StatsPeriod.WEEK -> today.minusDays(6)
                StatsPeriod.MONTH -> today.minusDays(29)
                StatsPeriod.YEAR -> YearMonth.from(today).minusMonths(11).atDay(1)
                StatsPeriod.ALL -> null
            }
            val sessions = allSessions.filter { session ->
                val day = session.day()
                (periodStart == null || !day.isBefore(periodStart)) && !day.isAfter(today)
            }

            val readingDays = allSessions
                .groupBy { it.day() }
                .mapValues { (_, daySessions) -> daySessions.sumOf { it.duration } }
                .filterValues { it > 0 }

            val (timelineUnit, timeline) = timeline(sessions, period, periodStart, today) { it.day() }

            val hourOfDay = MutableList(24) { 0L }
            val dayOfWeek = MutableList(7) { 0L }
            sessions.forEach { session ->
                val start = Instant.ofEpochMilli(session.startedAt).atZone(zone)
                hourOfDay[start.hour] += session.duration
                dayOfWeek[start.dayOfWeek.value - 1] += session.duration
            }

            val series = sessions
                .groupBy { it.mangaSource to it.mangaUrl }
                .map { (_, seriesSessions) ->
                    val latest = seriesSessions.maxBy { it.startedAt }
                    SeriesStats(
                        mangaSource = latest.mangaSource,
                        mangaUrl = latest.mangaUrl,
                        title = latest.mangaTitle,
                        seriesType = latest.seriesType,
                        time = seriesSessions.sumOf { it.duration },
                        chaptersFinished = seriesSessions.finishedChapters(),
                        pagesRead = seriesSessions.sumOf { it.pagesRead },
                        lastReadAt = latest.endedAt,
                    )
                }
                .sortedWith(compareByDescending<SeriesStats> { it.time }.thenBy { it.title })

            val types = sessions
                .groupBy { ReadingCategory.of(it.seriesType) }
                .map { (category, typeSessions) ->
                    TypeStats(
                        category = category,
                        time = typeSessions.sumOf { it.duration },
                        chaptersFinished = typeSessions.finishedChapters(),
                        pagesRead = typeSessions.sumOf { it.pagesRead },
                    )
                }
                .sortedByDescending { it.time }

            val (currentStreak, longestStreak) = streaks(readingDays.keys, today)
            val totalTime = sessions.sumOf { it.duration }

            return ReadingStats(
                period = period,
                totalTime = totalTime,
                chaptersFinished = sessions.finishedChapters(),
                pagesRead = sessions.sumOf { it.pagesRead },
                seriesRead = series.size,
                sessionCount = sessions.size,
                averageSession = if (sessions.isEmpty()) 0L else totalTime / sessions.size,
                longestSession = sessions.maxOfOrNull { it.duration } ?: 0L,
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                timelineUnit = timelineUnit,
                timeline = timeline,
                hourOfDay = hourOfDay,
                dayOfWeek = dayOfWeek,
                readingDays = readingDays,
                series = series,
                types = types,
            )
        }

        /** A chapter finished more than once in the period counts once. */
        private fun List<ReadingSession>.finishedChapters(): Int =
            filter { it.completed }.distinctBy { Triple(it.mangaSource, it.mangaUrl, it.chapterUrl) }.size

        private fun timeline(
            sessions: List<ReadingSession>,
            period: StatsPeriod,
            periodStart: LocalDate?,
            today: LocalDate,
            day: (ReadingSession) -> LocalDate,
        ): Pair<TimelineUnit, List<TimelineBar>> {
            val byDay = sessions.groupBy(day).mapValues { (_, daySessions) -> daySessions.sumOf { it.duration } }
            fun bars(starts: List<LocalDate>, end: (LocalDate) -> LocalDate) = starts.map { start ->
                val next = end(start)
                TimelineBar(start, byDay.filterKeys { !it.isBefore(start) && it.isBefore(next) }.values.sum())
            }

            return when (period) {
                StatsPeriod.WEEK, StatsPeriod.MONTH -> {
                    val start = periodStart!!
                    val days = ChronoUnit.DAYS.between(start, today).toInt()
                    TimelineUnit.DAY to (0..days).map { start.plusDays(it.toLong()) }.map { TimelineBar(it, byDay[it] ?: 0L) }
                }
                StatsPeriod.YEAR -> TimelineUnit.MONTH to bars(months(periodStart!!, today)) { it.plusMonths(1) }
                StatsPeriod.ALL -> {
                    val first = byDay.keys.minOrNull() ?: today
                    val months = months(first.withDayOfMonth(1), today)
                    if (months.size <= MAX_MONTH_BARS) {
                        TimelineUnit.MONTH to bars(months) { it.plusMonths(1) }
                    } else {
                        val years = (first.year..today.year).map { LocalDate.of(it, 1, 1) }
                        TimelineUnit.YEAR to bars(years) { it.plusYears(1) }
                    }
                }
            }
        }

        private fun months(start: LocalDate, today: LocalDate): List<LocalDate> {
            val first = YearMonth.from(start)
            val count = ChronoUnit.MONTHS.between(first, YearMonth.from(today)).toInt()
            return (0..count).map { first.plusMonths(it.toLong()).atDay(1) }
        }

        /**
         * The current streak still counts if today has no reading yet but yesterday did,
         * so it doesn't drop to zero first thing in the morning.
         */
        internal fun streaks(days: Set<LocalDate>, today: LocalDate): Pair<Int, Int> {
            if (days.isEmpty()) return 0 to 0

            var current = 0
            var day = if (today in days) today else today.minusDays(1)
            while (day in days) {
                current++
                day = day.minusDays(1)
            }

            var longest = 0
            var run = 0
            var previous: LocalDate? = null
            days.sorted().forEach {
                run = if (previous?.plusDays(1) == it) run + 1 else 1
                longest = maxOf(longest, run)
                previous = it
            }
            return current to longest
        }

        private const val MAX_MONTH_BARS = 24

        /** Monday first, matching [ReadingStats.dayOfWeek]. */
        val daysOfWeek: List<DayOfWeek> = DayOfWeek.entries
    }
}
