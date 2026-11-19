package yokai.domain.stats.interactor

import yokai.domain.stats.ReadingSessionRepository

class GetReadingSessions(private val repository: ReadingSessionRepository) {
    suspend fun awaitAll() = repository.getAll()
    suspend fun awaitBetween(start: Long, end: Long) = repository.getBetween(start, end)
    suspend fun awaitByManga(mangaSource: Long, mangaUrl: String) = repository.getByManga(mangaSource, mangaUrl)
}
