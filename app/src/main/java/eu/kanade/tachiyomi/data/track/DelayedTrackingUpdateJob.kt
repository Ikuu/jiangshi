package eu.kanade.tachiyomi.data.track

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.interactor.GetManga
import yokai.domain.track.interactor.GetTrack
import yokai.domain.track.interactor.InsertTrack

class DelayedTrackingUpdateJob(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val getManga: GetManga by injectLazy()
    private val getTrack: GetTrack by injectLazy()
    private val insertTrack: InsertTrack by injectLazy()

    override suspend fun doWork(): Result {
        val preferences = Injekt.get<PreferencesHelper>()
        val trackManager = Injekt.get<TrackManager>()
        val queued = preferences.trackingsToAddOnline().get()
        // Entries that were sent or can't ever be sent, and so can leave the queue
        val handled = queued.toMutableSet()
        val trackings = queued.mapNotNull {
            val items = it.split(":")
            if (items.size != 3) {
                null
            } else {
                val mangaId = items[0].toLongOrNull() ?: return@mapNotNull null
                val trackId = items[1].toLongOrNull() ?: return@mapNotNull null
                val chapterNumber = items[2].toFloatOrNull() ?: return@mapNotNull null
                mangaId to (trackId to chapterNumber) to it
            }
        }.groupBy { it.first.first }
        return withContext(Dispatchers.IO) {
            trackings.forEach {
                val mangaId = it.key
                val manga = getManga.awaitById(mangaId) ?: return@forEach
                val trackList = getTrack.awaitAllByMangaId(manga.id)
                it.value.forEach { (tC, entry) ->
                    val trackChapter = tC.second
                    val service = trackManager.getService(trackChapter.first)
                    val track = trackList.find { track -> track.sync_id == trackChapter.first }
                    if (service != null && track != null) {
                        try {
                            track.last_chapter_read = trackChapter.second
                            service.update(track, true)
                            insertTrack.await(track)
                        } catch (e: Exception) {
                            Logger.e(e) { "Unable to update tracker [tracker id ${track.sync_id}]" }
                            // Network being up doesn't mean the tracker is reachable (e.g. a VPN still
                            // connecting), so keep it for the next attempt
                            handled.remove(entry)
                        }
                    }
                }
            }
            // Re-read so entries queued while this ran aren't lost
            val remaining = preferences.trackingsToAddOnline().get() - handled
            preferences.trackingsToAddOnline().set(remaining)
            if (remaining.isEmpty()) {
                Result.success()
            } else if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                // Stop retrying, but leave them queued for when the next update schedules this job
                Result.success()
            }
        }
    }

    companion object {
        private const val TAG = "DelayedTrackingUpdate"
        private const val MAX_ATTEMPTS = 8

        fun setupTask(context: Context) {
            val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = OneTimeWorkRequestBuilder<DelayedTrackingUpdateJob>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
