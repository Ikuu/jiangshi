package eu.kanade.tachiyomi.data.track.kavita

import android.content.Context
import android.graphics.Color
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.PendingReadMarks
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.updateNewTrackInfo
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.sourcePreferences
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import uy.kohesive.injekt.injectLazy
import yokai.i18n.MR
import yokai.util.lang.getString

class Kavita(private val context: Context, id: Long) : TrackService(id), EnhancedTrackService {

    companion object {
        const val UNREAD = 1
        const val READING = 2
        const val COMPLETED = 3
    }

    var authentications: OAuth? = null

    private val interceptor by lazy { KavitaInterceptor(this) }
    val api by lazy { KavitaApi(client, interceptor) }

    private val sourceManager: SourceManager by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    override fun nameRes() = MR.strings.kavita

    override fun getLogo(): Int = R.drawable.ic_tracker_kavita

    override fun getTrackerColor() = Color.rgb(85, 199, 148)

    override fun getLogoColor() = Color.argb(0, 85, 199, 148)

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

    private val pending by lazy { PendingReadMarks(preferences.kavitaPendingMarks()) }

    /**
     * Sends chapters marked read or unread in the app to the server. Opening a series brings in what's read on
     * the server, so without this a finished chapter marked unread would turn back to read. A change that can't
     * be sent is kept and sent before the series next syncs, which leaves those chapters alone until it's through.
     */
    suspend fun updateChaptersRead(manga: Manga, chapters: List<Chapter>, read: Boolean) {
        if (!accept(sourceManager.getOrStub(manga.source))) return
        val mangaId = manga.id ?: return
        val ids = chapters.mapNotNull { KavitaApi.chapterId(it.url) }.distinct()
        if (ids.isEmpty()) return
        val keys = ids.map { it.toString() }
        pending.add(mangaId, keys, read)
        try {
            api.setChaptersRead(manga.url, ids, read)
            pending.remove(mangaId, keys, read)
        } catch (e: Exception) {
            Logger.w(e) { "Could not update read chapters on Kavita for ${manga.url}, will try again" }
        }
    }

    /** Read marks still to send, keyed by Kavita chapter ID. */
    fun allPendingMarks(): List<PendingReadMarks.Mark> = pending.all()

    /** Kavita chapter IDs in a series with a mark still to send, which syncing should leave alone. */
    fun pendingChapterIds(mangaId: Long): Set<Int> = pending.forManga(mangaId).keys.mapNotNull { it.toIntOrNull() }.toSet()

    suspend fun discardPendingMarks(marks: Collection<PendingReadMarks.Mark>) = pending.discard(marks)

    /** Tries to send the waiting marks for one series. Ones that still fail stay waiting. */
    suspend fun sendPendingMarks(manga: Manga) {
        val mangaId = manga.id ?: return
        pending.forManga(mangaId).entries.groupBy({ it.value }, { it.key }).forEach { (read, keys) ->
            val ids = keys.mapNotNull { it.toIntOrNull() }
            try {
                if (ids.isNotEmpty()) api.setChaptersRead(manga.url, ids, read)
                pending.remove(mangaId, keys, read)
            } catch (e: Exception) {
                Logger.w(e) { "Could not send waiting read marks to Kavita for ${manga.url}" }
            }
        }
    }

    /** Tries to send every waiting mark now, rather than on each series' next sync. */
    suspend fun sendAllPendingMarks() {
        pending.all().map { it.mangaId }.distinct().forEach { mangaId ->
            getManga.awaitById(mangaId)?.let { sendPendingMarks(it) }
        }
    }

    /** Sends the page reached in a chapter to the server, so reading there picks up from it */
    suspend fun updatePageProgress(manga: Manga, chapter: Chapter, page: Int) {
        val chapterId = KavitaApi.chapterId(chapter.url) ?: return
        try {
            api.setPageProgress(manga.url, chapterId, page)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(e) { "Could not update page progress on Kavita for ${manga.url}" }
        }
    }

    override suspend fun bind(track: Track): Track {
        return track
    }

    override suspend fun search(query: String): List<TrackSearch> {
        TODO("Not yet implemented: search")
    }

    override suspend fun refresh(track: Track): Track {
        // Waiting marks go first, so the server's progress already includes them
        getManga.awaitById(track.manga_id)?.let { sendPendingMarks(it) }
        val remoteTrack = api.getTrackSearch(track.tracking_url)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(username: String, password: String): Boolean {
        saveCredentials("user", "pass")
        return true
    }

    // TrackService.isLogged works by checking that credentials are saved.
    // By saving dummy, unused credentials, we can activate the tracker simply by login/logout
    override fun loginNoop() {
        saveCredentials("user", "pass")
    }

    override fun getAcceptedSources() = listOf("eu.kanade.tachiyomi.extension.all.kavita.Kavita")

    override suspend fun match(manga: Manga): TrackSearch? =
        try {
            api.getTrackSearch(manga.url)
        } catch (e: Exception) {
            null
        }

    override fun isTrackFrom(track: Track, manga: Manga, source: Source?): Boolean =
        track.tracking_url == manga.url && source?.let { accept(it) } == true

    /** The ID of the extension's [instance]th Kavita source, from 1 to 3. */
    private fun sourceId(instance: Int): Long {
        val key = "kavita_$instance/all/1" // Hardcoded versionID to 1
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
            .reduce(Long::or) and Long.MAX_VALUE
    }

    /** A Kavita source from the extension, with the server it's set up for, if any. */
    data class Server(val name: String, val apiUrl: String, val apiKey: String) {
        val isSetUp get() = apiUrl.isNotEmpty() && apiKey.isNotEmpty()
    }

    /**
     * The extension's Kavita sources that are set up with a server. If none are, the first source is
     * listed anyway, so it can say it isn't set up. Empty without the extension.
     */
    fun servers(): List<Server> {
        val sources = (1..3).mapNotNull { instance -> sourceManager.get(sourceId(instance)) as? ConfigurableSource }
        val servers = sources.map { source ->
            val preferences = source.sourcePreferences()
            Server(source.name, preferences.getString("APIURL", "").orEmpty(), preferences.getString("APIKEY", "").orEmpty())
        }
        return servers.filter { it.isSetUp }.ifEmpty { servers.take(1) }
    }

    fun loadOAuth() {
        val oauth = OAuth()
        for (id in 1..3) {
            val authentication = oauth.authentications[id - 1]
            val sourceId by lazy { sourceId(id) }
            val preferences = (sourceManager.get(sourceId) as ConfigurableSource).sourcePreferences()

            val prefApiUrl = preferences.getString("APIURL", "")
            val prefApiKey = preferences.getString("APIKEY", "")
            if (prefApiUrl.isNullOrEmpty() || prefApiKey.isNullOrEmpty()) {
                // Source not configured. Skip
                continue
            }

            val token = api.getNewToken(apiUrl = prefApiUrl, apiKey = prefApiKey)
            if (token.isNullOrEmpty()) {
                // Source is not accessible. Skip
                continue
            }

            authentication.apiUrl = prefApiUrl
            authentication.jwtToken = token.toString()
        }
        authentications = oauth
    }
}
