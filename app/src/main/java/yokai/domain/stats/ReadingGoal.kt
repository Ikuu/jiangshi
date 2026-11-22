package yokai.domain.stats

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import yokai.domain.stats.ReadingStats.Companion.finishedChapters

enum class GoalPeriod { DAY, WEEK, MONTH, YEAR }

enum class GoalMeasure { MINUTES, CHAPTERS }

/** A target such as 20 chapters a week or 30 minutes a day. */
data class ReadingGoal(val period: GoalPeriod, val measure: GoalMeasure, val target: Int) {

    /** Kept in preferences as "WEEK:CHAPTERS:20". */
    fun encode(): String = "${period.name}:${measure.name}:$target"

    /**
     * How far along the goal is in the period containing [now]. Weeks start on [firstDayOfWeek], and
     * months and years are calendar ones, so progress starts again from zero when a new one begins.
     */
    fun progress(sessions: List<ReadingSession>, now: Instant, zone: ZoneId, firstDayOfWeek: DayOfWeek): GoalProgress {
        val today = now.atZone(zone).toLocalDate()
        val start = when (period) {
            GoalPeriod.DAY -> today
            GoalPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            GoalPeriod.MONTH -> today.withDayOfMonth(1)
            GoalPeriod.YEAR -> today.withDayOfYear(1)
        }
        val end = when (period) {
            GoalPeriod.DAY -> start.plusDays(1)
            GoalPeriod.WEEK -> start.plusWeeks(1)
            GoalPeriod.MONTH -> start.plusMonths(1)
            GoalPeriod.YEAR -> start.plusYears(1)
        }
        val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = end.atStartOfDay(zone).toInstant().toEpochMilli()
        val inPeriod = sessions.filter { it.startedAt in startMillis until endMillis }
        val done = when (measure) {
            GoalMeasure.MINUTES -> inPeriod.sumOf { it.duration } / 60_000L
            GoalMeasure.CHAPTERS -> inPeriod.finishedChapters().toLong()
        }
        val elapsed = ((now.toEpochMilli() - startMillis).toDouble() / (endMillis - startMillis)).coerceIn(0.0, 1.0)
        return GoalProgress(
            goal = this,
            done = done,
            periodStart = start,
            periodEnd = end,
            expectedByNow = target * elapsed,
        )
    }

    companion object {
        fun decode(value: String): ReadingGoal? {
            val (period, measure, target) = value.split(":").takeIf { it.size == 3 } ?: return null
            return ReadingGoal(
                period = GoalPeriod.entries.find { it.name == period } ?: return null,
                measure = GoalMeasure.entries.find { it.name == measure } ?: return null,
                target = target.toIntOrNull()?.takeIf { it > 0 } ?: return null,
            )
        }
    }
}

data class GoalProgress(
    val goal: ReadingGoal,
    /** Minutes read or chapters finished so far in the period. */
    val done: Long,
    val periodStart: LocalDate,
    /** The day after the period ends. */
    val periodEnd: LocalDate,
    /** Where steady progress through the period would be by now. */
    val expectedByNow: Double,
) {
    val fraction: Float get() = (done.toFloat() / goal.target).coerceIn(0f, 1f)
    val isMet: Boolean get() = done >= goal.target
    val remaining: Long get() = (goal.target - done).coerceAtLeast(0)

    /** Keeping up with the period so far. A day is too short to say, so a daily goal is never behind. */
    val isOnTrack: Boolean get() = isMet || goal.period == GoalPeriod.DAY || done + 0.5 >= expectedByNow
}
