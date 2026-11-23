package yokai.presentation.stats

import android.content.Context
import dev.icerock.moko.resources.PluralsResource
import dev.icerock.moko.resources.StringResource
import java.text.NumberFormat
import yokai.domain.stats.GoalPeriod
import yokai.domain.stats.Milestone
import yokai.domain.stats.MilestoneKind
import yokai.i18n.MR
import yokai.util.lang.getString

/** Trophy put in front of a milestone where it stands on its own, such as in a notification. */
const val MILESTONE_EMOJI = "🏆"

private fun MilestoneKind.plural(): PluralsResource = when (this) {
    MilestoneKind.CHAPTERS -> MR.plurals.milestone_chapters
    MilestoneKind.HOURS -> MR.plurals.milestone_hours
    MilestoneKind.PAGES -> MR.plurals.milestone_pages
    MilestoneKind.SERIES -> MR.plurals.milestone_series
    MilestoneKind.STREAK -> MR.plurals.milestone_streak
}

/** What's counted, as a label for progress towards the next milestone. */
fun MilestoneKind.label(): StringResource = when (this) {
    MilestoneKind.CHAPTERS -> MR.strings.stats_chapters_finished
    MilestoneKind.HOURS -> MR.strings.stats_hours_read
    MilestoneKind.PAGES -> MR.strings.stats_pages_read
    MilestoneKind.SERIES -> MR.strings.stats_series_read
    MilestoneKind.STREAK -> MR.strings.stats_days_in_a_row
}

/** "1,000 chapters finished" or "30 day streak". */
fun Context.milestoneText(milestone: Milestone): String {
    val value = milestone.value
    return getString(milestone.kind.plural(), value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), NumberFormat.getIntegerInstance().format(value))
}

/** "today's goal" or "this week's goal", to finish a sentence such as "One more chapter to reach …". */
fun GoalPeriod.goalPhrase(): StringResource = when (this) {
    GoalPeriod.DAY -> MR.strings.chapter_end_goal_day
    GoalPeriod.WEEK -> MR.strings.chapter_end_goal_week
    GoalPeriod.MONTH -> MR.strings.chapter_end_goal_month
    GoalPeriod.YEAR -> MR.strings.chapter_end_goal_year
}
