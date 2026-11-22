package yokai.domain.stats.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import yokai.domain.stats.ReadingSession
import yokai.domain.stats.ReadingSessionRepository

class InsertReadingSession(private val repository: ReadingSessionRepository) {
    private val lock = Mutex()

    /**
     * Saves a session from the reader, adding it to the chapter's last session instead when that ended only a
     * little before (see [ReadingSession.canMergeWith]), so pausing the reader or flicking to another chapter
     * and back doesn't leave a list of short sessions.
     */
    suspend fun await(session: ReadingSession) = lock.withLock {
        val latest = repository.getLatestForChapter(session.mangaSource, session.mangaUrl, session.chapterUrl)
        if (latest != null && latest.canMergeWith(session)) {
            repository.update(latest.mergedWith(session))
        } else {
            repository.insert(session)
        }
    }

    /** Saves sessions as they are, such as from a backup. */
    suspend fun awaitAll(sessions: List<ReadingSession>) = repository.insertAll(sessions)
}
