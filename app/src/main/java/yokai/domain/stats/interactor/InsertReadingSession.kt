package yokai.domain.stats.interactor

import yokai.domain.stats.ReadingSession
import yokai.domain.stats.ReadingSessionRepository

class InsertReadingSession(private val repository: ReadingSessionRepository) {
    suspend fun await(session: ReadingSession) = repository.insert(session)
    suspend fun awaitAll(sessions: List<ReadingSession>) = repository.insertAll(sessions)
}
