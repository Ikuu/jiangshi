package yokai.data.stats

import yokai.data.Database
import yokai.data.DatabaseHandler
import yokai.domain.stats.ReadingSession
import yokai.domain.stats.ReadingSessionRepository

class ReadingSessionRepositoryImpl(private val handler: DatabaseHandler) : ReadingSessionRepository {
    override suspend fun insert(session: ReadingSession) {
        handler.await { insertSession(session) }
    }

    override suspend fun insertAll(sessions: List<ReadingSession>) {
        handler.await(inTransaction = true) { sessions.forEach { insertSession(it) } }
    }

    override suspend fun getAll(): List<ReadingSession> =
        handler.awaitList { reading_sessionsQueries.getAll(ReadingSession::mapper) }

    override suspend fun getBetween(start: Long, end: Long): List<ReadingSession> =
        handler.awaitList { reading_sessionsQueries.getBetween(start, end, ReadingSession::mapper) }

    override suspend fun getByManga(mangaSource: Long, mangaUrl: String): List<ReadingSession> =
        handler.awaitList { reading_sessionsQueries.getByManga(mangaSource, mangaUrl, ReadingSession::mapper) }

    override suspend fun delete(id: Long) {
        handler.await { reading_sessionsQueries.deleteById(id) }
    }

    override suspend fun deleteAll() {
        handler.await { reading_sessionsQueries.deleteAll() }
    }

    private fun Database.insertSession(session: ReadingSession) {
        reading_sessionsQueries.insert(
            mangaSource = session.mangaSource,
            mangaUrl = session.mangaUrl,
            mangaTitle = session.mangaTitle,
            seriesType = session.seriesType.toLong(),
            chapterUrl = session.chapterUrl,
            chapterNumber = session.chapterNumber,
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            duration = session.duration,
            pagesRead = session.pagesRead.toLong(),
            completed = if (session.completed) 1L else 0L,
        )
    }
}
