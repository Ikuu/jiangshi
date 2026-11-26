package yokai.domain.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How strong a reading streak is, shown with emoji that grow with it. */
enum class StreakTier(val minDays: Int, val emoji: String) {
    SPARK(1, "✨"),
    FLAME(3, "🔥"),
    BLAZE(7, "🔥🔥"),
    INFERNO(14, "🔥🔥🔥"),
    GEM(30, "💎"),
    CROWN(100, "👑"),
    ;

    companion object {
        /** Null without a streak. */
        fun of(days: Int): StreakTier? = if (days <= 0) null else entries.last { days >= it.minDays }
    }
}

object Streak {
    /**
     * Reading needed in a day for it to count towards a streak, so a moment in the reader, like waking the
     * tablet with a chapter still open, doesn't keep a streak going.
     */
    const val MIN_DAY_TIME = 60_000L

    /** Whether [time] read in a day is enough for the day to count towards a streak. */
    fun counts(time: Long): Boolean = time >= MIN_DAY_TIME

    /** The days with enough reading to count towards a streak. */
    fun days(sessions: List<ReadingSession>, zone: ZoneId): Set<LocalDate> = sessions
        .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        .filterValues { daySessions -> counts(daySessions.sumOf { it.duration }) }
        .keys

    /**
     * The streak that ends tonight unless something's read: its length in days if there's a streak going and
     * not enough has been read yet today to keep it, or null.
     */
    fun atRisk(sessions: List<ReadingSession>, now: Instant, zone: ZoneId): Int? {
        val days = days(sessions, zone)
        val today = now.atZone(zone).toLocalDate()
        if (today in days) return null
        return ReadingStats.streaks(days, today).first.takeIf { it > 0 }
    }
}
