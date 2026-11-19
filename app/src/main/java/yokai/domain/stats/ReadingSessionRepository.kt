package yokai.domain.stats

interface ReadingSessionRepository {
    suspend fun insert(session: ReadingSession)
    suspend fun insertAll(sessions: List<ReadingSession>)
    suspend fun getAll(): List<ReadingSession>
    suspend fun getBetween(start: Long, end: Long): List<ReadingSession>
    suspend fun getByManga(mangaSource: Long, mangaUrl: String): List<ReadingSession>
    suspend fun deleteAll()
}
