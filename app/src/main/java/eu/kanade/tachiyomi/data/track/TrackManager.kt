package eu.kanade.tachiyomi.data.track

import android.content.Context
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.kavita.Kavita
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.komga.Komga
import eu.kanade.tachiyomi.data.track.mangaupdates.MangaUpdates
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.suwayomi.Suwayomi
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import uy.kohesive.injekt.injectLazy

class TrackManager(context: Context) {

    companion object {
        const val MYANIMELIST = 1L
        const val ANILIST = 2L
        const val KITSU = 3L
        const val SHIKIMORI = 4L
        const val BANGUMI = 5L
        const val KOMGA = 6L
        const val MANGA_UPDATES = 7L
        const val KAVITA = 8L
        const val SUWAYOMI = 9L
    }

    val myAnimeList = MyAnimeList(context, MYANIMELIST)
    val aniList = Anilist(context, ANILIST)
    val kitsu = Kitsu(context, KITSU)
    val shikimori = Shikimori(context, SHIKIMORI)
    val bangumi = Bangumi(context, BANGUMI)
    val komga = Komga(context, KOMGA)
    val mangaUpdates = MangaUpdates(context, MANGA_UPDATES)
    val kavita = Kavita(context, KAVITA)
    val suwayomi = Suwayomi(context, SUWAYOMI)

    val services = listOf(myAnimeList, aniList, kitsu, shikimori, bangumi, komga, mangaUpdates, kavita, suwayomi)

    internal val sourceManager: SourceManager by injectLazy()

    /** Series linked across Kavita and Suwayomi, so reading one marks the other. */
    val linkedSeries = LinkedSeries(this)

    fun getService(id: Long) = services.find { it.id == id }

    /**
     * Sends chapters marked read or unread in the app to the Suwayomi or Kavita server they came from, and
     * marks them the same in a linked series on the other server.
     */
    suspend fun updateServerChaptersRead(manga: Manga, chapters: List<Chapter>, read: Boolean) {
        suwayomi.updateChaptersRead(manga, chapters.map { it.chapter_number }, read)
        kavita.updateChaptersRead(manga, chapters, read)
        linkedSeries.markLinked(manga, chapters, read)
    }

    /** Sends chapters bookmarked or unbookmarked in the app to the Suwayomi server they came from. */
    suspend fun updateServerChaptersBookmarked(manga: Manga, chapters: List<Chapter>, bookmarked: Boolean) {
        suwayomi.updateChaptersBookmarked(manga, chapters.map { it.chapter_number }, bookmarked)
    }

    fun hasLoggedServices() = services.any { it.isLogged }
}
