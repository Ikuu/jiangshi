package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import yokai.domain.stats.ReadingSession

@Serializable
data class BackupReadingSession(
    @ProtoNumber(1) var mangaSource: Long,
    @ProtoNumber(2) var mangaUrl: String,
    @ProtoNumber(3) var mangaTitle: String,
    @ProtoNumber(4) var seriesType: Int,
    @ProtoNumber(5) var chapterUrl: String,
    @ProtoNumber(6) var chapterNumber: Double,
    @ProtoNumber(7) var startedAt: Long,
    @ProtoNumber(8) var endedAt: Long,
    @ProtoNumber(9) var duration: Long,
    @ProtoNumber(10) var pagesRead: Int = 0,
    @ProtoNumber(11) var completed: Boolean = false,
) {
    fun toReadingSession() = ReadingSession(
        mangaSource = mangaSource,
        mangaUrl = mangaUrl,
        mangaTitle = mangaTitle,
        seriesType = seriesType,
        chapterUrl = chapterUrl,
        chapterNumber = chapterNumber,
        startedAt = startedAt,
        endedAt = endedAt,
        duration = duration,
        pagesRead = pagesRead,
        completed = completed,
    )

    companion object {
        fun from(session: ReadingSession) = BackupReadingSession(
            mangaSource = session.mangaSource,
            mangaUrl = session.mangaUrl,
            mangaTitle = session.mangaTitle,
            seriesType = session.seriesType,
            chapterUrl = session.chapterUrl,
            chapterNumber = session.chapterNumber,
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            duration = session.duration,
            pagesRead = session.pagesRead,
            completed = session.completed,
        )
    }
}
