package yokai.domain.stats

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil

/** What the reader shows between chapters: progress towards the goal, the streak, and a nudge to keep going. */
data class ChapterEndStatus(
    /** Progress including the chapter just read, or null without a goal. */
    val goal: GoalProgress?,
    /** The chapter just read is what met the goal. */
    val goalJustMet: Boolean,
    val nudge: Nudge?,
    /** Days in a row with reading, counting today. */
    val streak: Int,
    /** Milestones reached since the reader was opened, newest first. */
    val milestones: List<Milestone>,
) {
    val isEmpty: Boolean get() = goal == null && streak == 0 && milestones.isEmpty()

    sealed interface Nudge {
        /**
         * A few more chapters to go, one meaning the next chapter should do it. [estimated] when the goal is in
         * minutes, and the count comes from the pace of reading.
         */
        data class Chapters(val count: Int, val estimated: Boolean) : Nudge

        /** A few more minutes to go, but more than a chapter's worth. */
        data class Minutes(val minutes: Int) : Nudge
    }

    companion object {
        /** Chapters left that still count as nearly there. */
        const val NUDGE_CHAPTERS = 3

        /** Minutes left that still count as nearly there. */
        const val NUDGE_MINUTES = 15

        /**
         * [saved] is every session recorded so far, and [current] the one still going in the reader, if any.
         * [mangaSource] and [mangaUrl] pick the series whose pace says whether one more chapter would meet a goal in minutes.
         * Milestones count from [since], such as when the reader opened.
         */
        fun calculate(
            saved: List<ReadingSession>,
            current: ReadingSession?,
            goal: ReadingGoal?,
            mangaSource: Long,
            mangaUrl: String,
            since: Long,
            now: Instant,
            zone: ZoneId,
            firstDayOfWeek: DayOfWeek,
        ): ChapterEndStatus {
            val sessions = saved + listOfNotNull(current)
            val progress = goal?.progress(sessions, now, zone, firstDayOfWeek)
            val before = current?.let { goal?.progress(saved, now, zone, firstDayOfWeek) }
            val goalJustMet = progress?.isMet == true && before?.isMet == false

            val seriesSessions = sessions.filter { it.mangaSource == mangaSource && it.mangaUrl == mangaUrl }
            val nudge = progress?.takeIf { !it.isMet }?.let { nudge(it, seriesSessions, sessions) }

            val today = now.atZone(zone).toLocalDate()
            val days = sessions.filter { it.duration > 0 }.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }.toSet()
            val streak = if (today in days) ReadingStats.streaks(days, today).first else 0

            val milestones = Milestones.calculate(sessions, today, zone).reached.filter { it.reachedAt >= since }

            return ChapterEndStatus(progress, goalJustMet, nudge, streak, milestones)
        }

        internal fun nudge(progress: GoalProgress, seriesSessions: List<ReadingSession>, allSessions: List<ReadingSession>): Nudge? {
            val remaining = progress.remaining
            return when (progress.goal.measure) {
                GoalMeasure.CHAPTERS -> when {
                    remaining <= NUDGE_CHAPTERS -> Nudge.Chapters(remaining.toInt(), estimated = false)
                    else -> null
                }
                GoalMeasure.MINUTES -> {
                    val perChapter = ReadingPace.perChapter(seriesSessions) ?: ReadingPace.perChapter(allSessions)
                    when {
                        perChapter != null && remaining * MINUTE <= perChapter -> Nudge.Chapters(1, estimated = true)
                        remaining <= NUDGE_MINUTES -> Nudge.Minutes(remaining.toInt())
                        // A goal in minutes is met by chapters too, so say how many at this pace when it's only a few
                        perChapter != null -> ceil(remaining * MINUTE / perChapter).toInt()
                            .takeIf { it <= NUDGE_CHAPTERS }
                            ?.let { Nudge.Chapters(it, estimated = true) }
                        else -> null
                    }
                }
            }
        }

        private const val MINUTE = 60_000L
    }
}
