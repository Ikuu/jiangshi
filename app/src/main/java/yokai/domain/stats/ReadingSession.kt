package yokai.domain.stats

/**
 * A stretch of reading one chapter. [duration] is active reading time in milliseconds, with long gaps
 * between page turns capped (see [ReadingSessionTimer]), so it can be shorter than [endedAt] - [startedAt].
 */
data class ReadingSession(
    val mangaSource: Long,
    val mangaUrl: String,
    val mangaTitle: String,
    val seriesType: Int,
    val chapterUrl: String,
    val chapterNumber: Double,
    val startedAt: Long,
    val endedAt: Long,
    val duration: Long,
    val pagesRead: Int,
    val completed: Boolean,
    val id: Long? = null,
) {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun mapper(
            id: Long,
            mangaSource: Long,
            mangaUrl: String,
            mangaTitle: String,
            seriesType: Long,
            chapterUrl: String,
            chapterNumber: Double,
            startedAt: Long,
            endedAt: Long,
            duration: Long,
            pagesRead: Long,
            completed: Long,
        ) = ReadingSession(
            mangaSource = mangaSource,
            mangaUrl = mangaUrl,
            mangaTitle = mangaTitle,
            seriesType = seriesType.toInt(),
            chapterUrl = chapterUrl,
            chapterNumber = chapterNumber,
            startedAt = startedAt,
            endedAt = endedAt,
            duration = duration,
            pagesRead = pagesRead.toInt(),
            completed = completed != 0L,
            id = id,
        )
    }
}
