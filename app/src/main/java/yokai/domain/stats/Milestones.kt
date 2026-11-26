package yokai.domain.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Something counted towards milestones, with the round numbers worth marking. */
enum class MilestoneKind(val thresholds: List<Long>) {
    CHAPTERS(listOf(10, 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000, 25_000)),
    HOURS(listOf(1, 10, 24, 50, 100, 250, 500, 1_000, 2_500, 5_000)),
    PAGES(listOf(1_000, 5_000, 10_000, 25_000, 50_000, 100_000, 250_000, 500_000, 1_000_000)),
    SERIES(listOf(5, 10, 25, 50, 100, 250, 500, 1_000)),
    STREAK(listOf(3, 7, 14, 30, 60, 100, 200, 365, 500, 1_000)),
    FINISHED(listOf(1, 5, 10, 25, 50, 100, 250, 500)),
}

/** A round number reached, such as the 1,000th chapter finished, and when it was reached. */
data class Milestone(val kind: MilestoneKind, val value: Long, val reachedAt: Long) {
    /** Kept in preferences to remember which milestones have been announced. */
    val key: String get() = "${kind.name}:$value"
}

/** The next round number for a kind, and how far along it is. */
data class NextMilestone(val kind: MilestoneKind, val value: Long, val current: Long) {
    val fraction: Float get() = (current.toFloat() / value).coerceIn(0f, 1f)
}

/** Personal bests, each with when it happened. */
data class ReadingRecords(
    val longestSession: ReadingSession?,
    /** The day with the most time read, and how much. */
    val busiestDay: Pair<LocalDate, Long>?,
    /** The day with the most chapters finished, and how many. */
    val mostChaptersInADay: Pair<LocalDate, Int>?,
    /** The longest run of days in a row with reading, and the day it ended. */
    val longestStreak: Pair<Int, LocalDate>?,
)

data class Milestones(
    /** Newest first. */
    val reached: List<Milestone>,
    /** One per kind that still has a round number to go, closest to being reached first. */
    val next: List<NextMilestone>,
    val records: ReadingRecords,
) {
    companion object {
        /**
         * Works out milestones from every session, oldest first, so each one is dated by the session that
         * reached it. A chapter finished again doesn't count twice, and a streak is reached on its last day, by the
         * session that makes that day's reading enough to count. A series in [finished] is counted by the session
         * that finished it.
         */
        fun calculate(
            allSessions: List<ReadingSession>,
            today: LocalDate,
            zone: ZoneId,
            finished: List<FinishedSeries> = emptyList(),
        ): Milestones {
            fun ReadingSession.day(): LocalDate = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()

            val sessions = allSessions.sortedBy { it.startedAt }
            val reached = mutableListOf<Milestone>()
            val counts = mutableMapOf<MilestoneKind, Long>()

            fun count(kind: MilestoneKind, value: Long, at: Long) {
                val before = counts[kind] ?: 0L
                counts[kind] = value
                kind.thresholds.filter { it in (before + 1)..value }.forEach { reached += Milestone(kind, it, at) }
            }

            val chapters = mutableSetOf<Triple<Long, String, String>>()
            val series = mutableSetOf<Pair<Long, String>>()
            val finishedBy = finished.associateBy { it.mangaSource to it.mangaUrl }
            val seriesFinished = mutableSetOf<Pair<Long, String>>()
            var time = 0L
            var pages = 0L
            var streak = 0
            var longestStreak = 0
            var longestStreakEnd: LocalDate? = null
            var lastDay: LocalDate? = null
            // Reading so far on the day being added up, which counts towards the streak once it's enough
            var day: LocalDate? = null
            var dayTime = 0L

            sessions.forEach { session ->
                val at = session.endedAt
                if (session.completed) {
                    chapters += Triple(session.mangaSource, session.mangaUrl, session.chapterUrl)
                    count(MilestoneKind.CHAPTERS, chapters.size.toLong(), at)
                }
                time += session.duration
                count(MilestoneKind.HOURS, time / HOUR, at)
                pages += session.pagesRead
                count(MilestoneKind.PAGES, pages, at)
                series += session.mangaSource to session.mangaUrl
                count(MilestoneKind.SERIES, series.size.toLong(), at)
                val key = session.mangaSource to session.mangaUrl
                if (finishedBy[key]?.finishedBy(session) == true && seriesFinished.add(key)) {
                    count(MilestoneKind.FINISHED, seriesFinished.size.toLong(), at)
                }

                val sessionDay = session.day()
                if (sessionDay != day) {
                    day = sessionDay
                    dayTime = 0L
                }
                dayTime += session.duration
                if (Streak.counts(dayTime) && sessionDay != lastDay) {
                    streak = if (lastDay?.plusDays(1) == sessionDay) streak + 1 else 1
                    lastDay = sessionDay
                    // A streak milestone can be reached again after a break, but counts from the first time
                    if (streak > (counts[MilestoneKind.STREAK] ?: 0L)) {
                        count(MilestoneKind.STREAK, streak.toLong(), session.startedAt)
                    }
                    if (streak > longestStreak) {
                        longestStreak = streak
                        longestStreakEnd = sessionDay
                    }
                }
            }

            // The streak to beat is the one going now, which still counts until today is over
            val currentStreak = lastDay?.takeIf { !it.isBefore(today.minusDays(1)) }?.let { streak } ?: 0
            val current = mapOf(
                MilestoneKind.CHAPTERS to chapters.size.toLong(),
                MilestoneKind.HOURS to time / HOUR,
                MilestoneKind.PAGES to pages,
                MilestoneKind.SERIES to series.size.toLong(),
                MilestoneKind.STREAK to currentStreak.toLong(),
                MilestoneKind.FINISHED to seriesFinished.size.toLong(),
            )
            val reachedKeys = reached.map { it.key }.toSet()
            val next = MilestoneKind.entries.mapNotNull { kind ->
                val now = current.getValue(kind)
                kind.thresholds.firstOrNull { it > now && Milestone(kind, it, 0).key !in reachedKeys }
                    ?.let { NextMilestone(kind, it, now) }
            }.sortedByDescending { it.fraction }

            val byDay = sessions.groupBy { it.day() }
            val records = ReadingRecords(
                longestSession = sessions.maxByOrNull { it.duration }?.takeIf { it.duration > 0 },
                busiestDay = byDay.mapValues { (_, day) -> day.sumOf { it.duration } }
                    .filterValues { it > 0 }
                    .maxByOrNull { it.value }
                    ?.toPair(),
                mostChaptersInADay = byDay.mapValues { (_, day) -> day.filter { it.completed }.distinctBy { it.mangaUrl to it.chapterUrl }.size }
                    .filterValues { it > 0 }
                    .maxByOrNull { it.value }
                    ?.toPair(),
                longestStreak = longestStreakEnd?.let { longestStreak to it },
            )

            return Milestones(
                reached = reached.sortedWith(compareByDescending<Milestone> { it.reachedAt }.thenByDescending { it.kind.ordinal }),
                next = next,
                records = records,
            )
        }

        private const val HOUR = 60 * 60 * 1000L
    }
}
