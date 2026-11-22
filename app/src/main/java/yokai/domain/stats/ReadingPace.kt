package yokai.domain.stats

import yokai.domain.stats.ReadingStats.Companion.finishedChapters

/** What's left of an unread chapter, issue or volume. */
data class UnreadEntry(
    /** How much is left, from 0 to 1, so a half-read chapter is 0.5. */
    val fractionLeft: Double,
    /** Pages left, or null when the entry's length isn't known. */
    val pagesLeft: Int?,
) {
    companion object {
        /**
         * From a chapter's progress. [lastPageRead] and [pagesLeft] are as the app keeps them, and [pageCount]
         * is the length from the server where it's known, such as from Kavita. A started chapter's own count
         * of pages left is used first, as it's what the reader saw.
         */
        fun of(lastPageRead: Int, pagesLeft: Int, pageCount: Int?): UnreadEntry {
            val started = lastPageRead > 0
            val total = pageCount?.takeIf { it > 0 } ?: (lastPageRead + pagesLeft).takeIf { started && pagesLeft > 0 }
            val left = when {
                total == null -> null
                started && pagesLeft > 0 -> pagesLeft
                started -> (total - lastPageRead).coerceAtLeast(0)
                else -> total
            }
            val fraction = if (started && total != null && left != null) left.toDouble() / total else 1.0
            return UnreadEntry(fraction.coerceIn(0.0, 1.0), left)
        }
    }
}

/** How long reading takes: per page, and per chapter for entries whose length isn't known. */
data class Pace(val perPage: Double?, val perChapter: Double?) {

    /**
     * Time to read [entries]: pages left times the time per page where the length is known, and the time
     * per chapter for the rest. Null if there's nothing to read or any entry can't be estimated.
     */
    fun timeLeft(entries: List<UnreadEntry>): Long? {
        if (entries.isEmpty()) return null
        return entries.sumOf { entry ->
            val pages = entry.pagesLeft
            when {
                pages != null && perPage != null -> pages * perPage
                perChapter != null -> entry.fractionLeft * perChapter
                else -> return null
            }
        }.toLong()
    }
}

/** How long reading takes, from the reading recorded so far. */
object ReadingPace {

    /** Fewer finished chapters than this say too little about a pace per chapter. */
    const val MIN_FINISHED_CHAPTERS = 3

    /** Fewer pages than this say too little about a pace per page. */
    const val MIN_PAGES = 50

    /**
     * Average time per finished chapter, counting all the time read, including chapters not finished.
     * Null with too few finished chapters to go on.
     */
    fun perChapter(sessions: List<ReadingSession>): Double? {
        val finished = sessions.finishedChapters()
        if (finished < MIN_FINISHED_CHAPTERS) return null
        return sessions.sumOf { it.duration }.toDouble() / finished
    }

    /** Average time per page seen. Null with too few pages to go on. */
    fun perPage(sessions: List<ReadingSession>): Double? {
        val pages = sessions.sumOf { it.pagesRead }
        if (pages < MIN_PAGES) return null
        return sessions.sumOf { it.duration }.toDouble() / pages
    }

    /**
     * The pace for a series: its own once it has enough reading, then the pace for its kind of series, then
     * the pace overall. Pages and chapters each fall back on their own.
     */
    fun forSeries(seriesSessions: List<ReadingSession>, category: ReadingCategory, allSessions: List<ReadingSession>): Pace {
        val sameKind by lazy { allSessions.filter { ReadingCategory.of(it.seriesType) == category } }
        return Pace(
            perPage = perPage(seriesSessions) ?: perPage(sameKind) ?: perPage(allSessions),
            perChapter = perChapter(seriesSessions) ?: perChapter(sameKind) ?: perChapter(allSessions),
        )
    }

    /** Time left to read [entries] of a series, at [forSeries]' pace. */
    fun timeLeft(
        entries: List<UnreadEntry>,
        seriesSessions: List<ReadingSession>,
        category: ReadingCategory,
        allSessions: List<ReadingSession>,
    ): Long? = forSeries(seriesSessions, category, allSessions).timeLeft(entries)
}
