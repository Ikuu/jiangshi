package yokai.domain.stats

/**
 * Times reading in the reader, one chapter at a time.
 *
 * Time between two bits of activity (opening the reader, turning a page, leaving) counts as reading, but each gap
 * is capped at [idleCutoff]. Leaving the reader open on one page with the screen kept on adds at most [idleCutoff].
 */
class ReadingSessionTimer(private val idleCutoff: Long = DEFAULT_IDLE_CUTOFF) {

    data class Target(
        val mangaSource: Long,
        val mangaUrl: String,
        val mangaTitle: String,
        val seriesType: Int,
        val chapterUrl: String,
        val chapterNumber: Double,
    )

    private var target: Target? = null
    private var startedAt = 0L
    private var lastActivity = 0L
    private var activeTime = 0L
    private val pages = mutableSetOf<Int>()
    private var completed = false

    /**
     * Records activity in [target]'s chapter: a page turn to [page], or opening/resuming the reader when [page] is null.
     * Returns the previous chapter's finished session if this moved to a different chapter.
     */
    @Synchronized
    fun onActivity(target: Target, page: Int?, now: Long): ReadingSession? {
        val current = this.target
        val finished = if (current != null && current.chapterUrl != target.chapterUrl) stop(now) else null

        if (this.target == null) {
            this.target = target
            startedAt = now
            lastActivity = now
        } else {
            addGap(now)
        }
        page?.let(pages::add)
        return finished
    }

    /** Marks the chapter with [chapterUrl] as finished in this session, if it's the one being timed. */
    @Synchronized
    fun markCompleted(chapterUrl: String) {
        if (target?.chapterUrl == chapterUrl) completed = true
    }

    /** The session so far, as [stop] would return it at [now], while it carries on. */
    @Synchronized
    fun current(now: Long): ReadingSession? {
        if (target == null) return null
        val gap = (now - lastActivity).coerceIn(0L, idleCutoff)
        return session(now, activeTime + gap)
    }

    /** Ends the current session, returning it unless nothing worth keeping happened. */
    @Synchronized
    fun stop(now: Long): ReadingSession? {
        if (target == null) return null
        addGap(now)
        val session = session(now, activeTime)
        reset()
        return session.takeIf { it.duration >= MIN_SESSION_DURATION || it.completed }
    }

    private fun session(now: Long, duration: Long): ReadingSession {
        val target = target!!
        return ReadingSession(
            mangaSource = target.mangaSource,
            mangaUrl = target.mangaUrl,
            mangaTitle = target.mangaTitle,
            seriesType = target.seriesType,
            chapterUrl = target.chapterUrl,
            chapterNumber = target.chapterNumber,
            startedAt = startedAt,
            endedAt = now,
            duration = duration,
            pagesRead = pages.size,
            completed = completed,
        )
    }

    private fun addGap(now: Long) {
        activeTime += (now - lastActivity).coerceIn(0L, idleCutoff)
        lastActivity = now
    }

    private fun reset() {
        target = null
        startedAt = 0L
        lastActivity = 0L
        activeTime = 0L
        pages.clear()
        completed = false
    }

    companion object {
        const val DEFAULT_IDLE_CUTOFF = 5 * 60 * 1000L
        const val MIN_SESSION_DURATION = 1000L
    }
}
