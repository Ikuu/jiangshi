package yokai.domain.stats

/** One series' reading, shown on its page. */
data class SeriesReadingSummary(
    val time: Long,
    val sessions: Int,
    val firstReadAt: Long,
    val lastReadAt: Long,
) {
    companion object {
        /** Null when the series hasn't been read since stats began. */
        fun from(sessions: List<ReadingSession>): SeriesReadingSummary? {
            if (sessions.isEmpty()) return null
            return SeriesReadingSummary(
                time = sessions.sumOf { it.duration },
                sessions = sessions.size,
                firstReadAt = sessions.minOf { it.startedAt },
                lastReadAt = sessions.maxOf { it.endedAt },
            )
        }
    }
}
