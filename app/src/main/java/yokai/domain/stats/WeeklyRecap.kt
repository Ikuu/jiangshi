package yokai.domain.stats

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import yokai.domain.stats.ReadingStats.Companion.finishedChapters

/** The week's reading so far, against the whole of the week before, for the weekly recap. */
data class WeeklyRecap(
    val time: Long,
    val chaptersFinished: Int,
    val daysRead: Int,
    val previousTime: Long,
) {
    companion object {
        /** The recap for the week containing [now], with weeks starting on [firstDayOfWeek]. Null if nothing was read. */
        fun of(sessions: List<ReadingSession>, now: Instant, zone: ZoneId, firstDayOfWeek: DayOfWeek): WeeklyRecap? {
            val start = now.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
            val previousStartMillis = start.minusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val endMillis = start.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val week = sessions.filter { it.startedAt in startMillis until endMillis && it.duration > 0 }
            if (week.isEmpty()) return null
            return WeeklyRecap(
                time = week.sumOf { it.duration },
                chaptersFinished = week.finishedChapters(),
                daysRead = week.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }.distinct().size,
                previousTime = sessions.filter { it.startedAt in previousStartMillis until startMillis }.sumOf { it.duration },
            )
        }
    }
}
