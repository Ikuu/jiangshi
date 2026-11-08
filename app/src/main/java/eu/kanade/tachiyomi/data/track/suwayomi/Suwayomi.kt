package eu.kanade.tachiyomi.data.track.suwayomi

import android.content.Context
import android.graphics.Color
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.updateNewTrackInfo
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import uy.kohesive.injekt.injectLazy
import yokai.domain.track.interactor.GetTrack
import yokai.i18n.MR
import yokai.util.lang.getString

class Suwayomi(private val context: Context, id: Long) : TrackService(id), EnhancedTrackService {
    val api by lazy { TachideskApi() }
    private val preferences: PreferencesHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val getTrack: GetTrack by injectLazy()

    override fun nameRes() = MR.strings.suwayomi

    override fun getLogo() = R.drawable.ic_tracker_suwayomi

    override fun getTrackerColor() = Color.rgb(255, 214, 0)

    override fun getLogoColor() = Color.TRANSPARENT

    companion object {
        const val UNREAD = 1
        const val READING = 2
        const val COMPLETED = 3
    }

    override fun getStatusList() = listOf(UNREAD, READING, COMPLETED)

    override fun isCompletedStatus(index: Int): Boolean = getStatusList()[index] == COMPLETED

    override fun getStatus(status: Int): String = with(context) {
        when (status) {
            UNREAD -> getString(MR.strings.unread)
            READING -> getString(MR.strings.reading)
            COMPLETED -> getString(MR.strings.completed)
            else -> ""
        }
    }

    override fun getGlobalStatus(status: Int): String = with(context) {
        when (status) {
            UNREAD -> getString(MR.strings.plan_to_read)
            READING -> getString(MR.strings.reading)
            COMPLETED -> getString(MR.strings.completed)
            else -> ""
        }
    }

    override fun completedStatus(): Int = COMPLETED
    override fun readingStatus() = READING
    override fun planningStatus() = UNREAD

    override fun getScoreList(): ImmutableList<String> = persistentListOf()

    override fun displayScore(track: Track): String = ""

    override suspend fun add(track: Track): Track {
        track.status = READING
        updateNewTrackInfo(track)
        return api.updateProgress(track)
    }

    override suspend fun update(track: Track, setToRead: Boolean): Track {
        updateTrackStatus(track, setToRead)
        return api.updateProgress(track)
    }

    /** The series on the server, from its tracker link if it has one. */
    fun seriesUrl(manga: Manga, tracks: List<Track>): String =
        tracks.find { it.sync_id == id }?.tracking_url ?: manga.url

    /** Sends a change made in the app to the server, so the next sync doesn't undo it. */
    suspend fun updateChaptersRead(manga: Manga, chapterNumbers: List<Float>, read: Boolean) {
        if (!preferences.trackMarkedAsRead().get() || !accept(sourceManager.getOrStub(manga.source))) return
        val numbers = chapterNumbers.filter { it >= 0f }.toSet()
        if (numbers.isEmpty()) return
        try {
            api.setChaptersRead(seriesUrl(manga, getTrack.awaitAllByMangaId(manga.id)), numbers, read)
        } catch (e: Exception) {
            Logger.w(e) { "Could not update read chapters on Suwayomi for ${manga.url}" }
        }
    }

    override suspend fun bind(track: Track): Track {
        return track
    }

    override suspend fun search(query: String): List<TrackSearch> {
        TODO("Not yet implemented")
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getTrackSearch(track.tracking_url)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(username: String, password: String): Boolean {
        saveCredentials("user", "pass")
        return true
    }

    override fun loginNoop() {
        saveCredentials("user", "pass")
    }

    override fun getAcceptedSources(): List<String> = listOf("eu.kanade.tachiyomi.extension.all.tachidesk.Tachidesk")

    override suspend fun match(manga: Manga): TrackSearch? =
        try {
            api.getTrackSearch(manga.url)
        } catch (e: Exception) {
            Logger.e(e) { "Could not match ${manga.title} (url: ${manga.url}) with Suwayomi" }
            null
        }
}
