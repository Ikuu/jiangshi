package yokai.domain.stats

import java.time.Instant
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
     * The streak that ends tonight unless something's read: its length in days if there's a streak going and
     * nothing has been read yet today, or null.
     */
    fun atRisk(sessions: List<ReadingSession>, now: Instant, zone: ZoneId): Int? {
        val days = sessions
            .filter { it.duration > 0 }
            .map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            .toSet()
        val today = now.atZone(zone).toLocalDate()
        if (today in days) return null
        return ReadingStats.streaks(days, today).first.takeIf { it > 0 }
    }
}
