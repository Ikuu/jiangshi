package yokai.domain.stats

/**
 * A series read to its end, and when. Reading sessions don't say when a series is complete, so it's worked out
 * from series that are complete now: finished publishing, with every chapter read.
 */
data class FinishedSeries(
    val mangaSource: Long,
    val mangaUrl: String,
    val title: String,
    /** The chapter whose finish completed the series. */
    val chapterUrl: String,
    /** When the session that finished [chapterUrl] ended. */
    val finishedAt: Long,
) {
    /** Whether [session] is the one that finished this series. */
    fun finishedBy(session: ReadingSession): Boolean =
        session.completed &&
            session.mangaSource == mangaSource &&
            session.mangaUrl == mangaUrl &&
            session.chapterUrl == chapterUrl &&
            session.endedAt == finishedAt

    companion object {
        /**
         * The series in [complete], as source and URL, dated by the sessions: each chapter counts from the first time
         * it was finished, and the series from the last of those, when nothing was left to read. A chapter read again
         * later doesn't move the date. Series without a chapter finished in a session aren't counted, as they were
         * read before sessions were recorded. Newest first.
         */
        fun calculate(sessions: List<ReadingSession>, complete: Set<Pair<Long, String>>): List<FinishedSeries> = sessions
            .filter { it.completed && (it.mangaSource to it.mangaUrl) in complete }
            .groupBy { it.mangaSource to it.mangaUrl }
            .map { (_, seriesSessions) ->
                // The first finish of each chapter, then the latest of those
                val last = seriesSessions
                    .groupBy { it.chapterUrl }
                    .map { (_, chapterSessions) -> chapterSessions.minBy { it.endedAt } }
                    .maxBy { it.endedAt }
                FinishedSeries(last.mangaSource, last.mangaUrl, last.mangaTitle, last.chapterUrl, last.endedAt)
            }
            .sortedByDescending { it.finishedAt }
    }
}
