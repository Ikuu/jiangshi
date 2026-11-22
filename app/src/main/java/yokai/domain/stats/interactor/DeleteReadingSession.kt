package yokai.domain.stats.interactor

import yokai.domain.stats.ReadingSessionRepository

/** Takes a session out of the stats, such as one from a reader left open. */
class DeleteReadingSession(private val repository: ReadingSessionRepository) {
    suspend fun await(id: Long) = repository.delete(id)
}
