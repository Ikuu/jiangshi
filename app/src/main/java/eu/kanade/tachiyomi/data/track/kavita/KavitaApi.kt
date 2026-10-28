package eu.kanade.tachiyomi.data.track.kavita

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.withIOContext
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.injectLazy

class KavitaApi(private val client: OkHttpClient, interceptor: KavitaInterceptor) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder()
        .dns(Dns.SYSTEM)
        .addInterceptor(interceptor)
        .build()

    fun getApiFromUrl(url: String): String {
        return url.split("/api/").first() + "/api"
    }

    /*
     * Uses url to compare against each source APIURL's to get the correct custom source preference.
     * Now having source preference we can do getString("APIKEY")
     * Authenticates to get the token
     * Saves the token in the var jwtToken
     */
    fun getNewToken(apiUrl: String, apiKey: String): String? {
        val request = POST(
            "$apiUrl/Plugin/authenticate?apiKey=$apiKey&pluginName=Tachiyomi-Kavita",
            body = "{}".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()),
        )
        try {
            client.newCall(request).execute().use {
                when (it.code) {
                    200 -> return it.parseAs<AuthenticationDto>().token
                    401 -> {
                        Logger.w { "Unauthorized / api key not valid: Cleaned api URL: $apiUrl, Api key is empty: ${apiKey.isEmpty()}" }
                        throw IOException("Unauthorized / api key not valid")
                    }
                    500 -> {
                        Logger.w { "Error fetching JWT token. Cleaned api URL: $apiUrl, Api key is empty: ${apiKey.isEmpty()}" }
                        throw IOException("Error fetching JWT token")
                    }
                    else -> {}
                }
            }
            // Not sure which one to catch
        } catch (e: SocketTimeoutException) {
            Logger.w {
                "Could not fetch JWT token. Probably due to connectivity issue or the url '$apiUrl' is not available, skipping"
            }
            return null
        } catch (e: Exception) {
            Logger.e {
                "Unhandled exception fetching JWT token for url: '$apiUrl'"
            }
            throw IOException(e)
        }

        return null
    }

    private fun getApiVolumesUrl(url: String): String {
        return "${getApiFromUrl(url)}/Series/volumes?seriesId=${getIdFromUrl(url)}"
    }

    /* Strips serie id from URL */
    private fun getIdFromUrl(url: String): Int {
        return url.substringAfterLast("/").toInt()
    }

    /*
     * Returns total chapters in the series.
     * Ignores volumes.
     * Volumes consisting of 1 file treated as chapter
     */
    private fun getTotalChapters(url: String): Long {
        val requestUrl = getApiVolumesUrl(url)
        try {
            val listVolumeDto =
                authClient.newCall(GET(requestUrl))
                    .execute()
                    .parseAs<List<VolumeDto>>()
            var volumeNumber = 0L
            var maxChapterNumber = 0L
            for (volume in listVolumeDto) {
                if (volume.chapters.maxOf { it.number!!.toFloat() } == 0f) {
                    volumeNumber++
                } else if (maxChapterNumber < volume.chapters.maxOf { it.number!!.toFloat() }) {
                    maxChapterNumber = volume.chapters.maxOf { it.number!!.toFloat().toLong() }
                }
            }

            return if (maxChapterNumber > volumeNumber) maxChapterNumber else volumeNumber
        } catch (e: Exception) {
            Logger.w(e) { "Exception fetching Total Chapters. Request:$requestUrl" }
            throw e
        }
    }

    private fun getLatestChapterRead(url: String): Float {
        val serieId = getIdFromUrl(url)
        val requestUrl = "${getApiFromUrl(url)}/Tachiyomi/latest-chapter?seriesId=$serieId"
        try {
            authClient.newCall(GET(requestUrl)).execute().use {
                if (it.code == 200) {
                    return it.parseAs<ChapterDto>().number!!.replace(",", ".").toFloat()
                }
                if (it.code == 204) {
                    return 0F
                }
            }
        } catch (e: Exception) {
            Logger.w(e) { "Exception getting latest chapter read. Could not get itemRequest: $requestUrl" }
            throw e
        }
        return 0F
    }

    suspend fun getTrackSearch(url: String): TrackSearch = withIOContext {
        try {
            val serieDto: SeriesDto =
                authClient.newCall(GET(url))
                    .awaitSuccess()
                    .parseAs()

            val track = serieDto.toTrack()
            track.apply {
                cover_url = serieDto.thumbnail_url.toString()
                tracking_url = url
                total_chapters = getTotalChapters(url)

                title = serieDto.name
                status = when (serieDto.pagesRead) {
                    serieDto.pages -> Kavita.COMPLETED
                    0 -> Kavita.UNREAD
                    else -> Kavita.READING
                }
                last_chapter_read = getLatestChapterRead(url)
            }
        } catch (e: Exception) {
            Logger.w(e) { "Could not get item: $url" }
            throw e
        }
    }

    private suspend fun getLibraries(apiUrl: String): List<LibraryDto> {
        return authClient.newCall(GET("$apiUrl/Library/libraries")).awaitSuccess().parseAs()
    }

    /** Library type for each series URL, looking up each server's libraries once */
    suspend fun getLibraryTypes(urls: List<String>): Map<String, Int> = withIOContext {
        urls.groupBy { getApiFromUrl(it) }.flatMap { (apiUrl, seriesUrls) ->
            val libraries = getLibraries(apiUrl)
            seriesUrls.mapNotNull { url ->
                try {
                    val series = authClient.newCall(GET(url)).awaitSuccess().parseAs<SeriesDto>()
                    libraries.find { it.id == series.libraryId }?.let { url to it.type }
                } catch (e: Exception) {
                    Logger.w(e) { "Could not get Kavita library type for $url" }
                    null
                }
            }
        }.toMap()
    }

    suspend fun getSeriesLayout(url: String): KavitaSeriesLayout = withIOContext {
        val series = authClient.newCall(GET(url)).awaitSuccess().parseAs<SeriesDto>()
        val libraryType = getLibraries(getApiFromUrl(url)).find { it.id == series.libraryId }?.type
        val volumes = authClient.newCall(GET(getApiVolumesUrl(url))).awaitSuccess().parseAs<List<VolumeDto>>()
        val isComic = libraryType == KavitaLibraryTypes.COMIC || libraryType == KavitaLibraryTypes.COMIC_VINE
        KavitaSeriesLayout(
            libraryType = libraryType,
            isComic = isComic,
            chapters = volumes.flatMap { volume ->
                val volumeNumber = volume.minNumber ?: volume.number.toFloat()
                volume.chapters.mapNotNull { chapter ->
                    val id = chapter.id ?: return@mapNotNull null
                    // Same rules as the extension's ChapterType: loose chapters sit in volume -100000,
                    // specials in volume 100000, and a whole-volume file has chapter number -100000
                    val type = when {
                        chapter.isSpecial == true || volumeNumber >= SPECIAL_NUMBER -> KavitaEntryType.Special
                        volumeNumber <= UNNUMBERED_NUMBER -> if (isComic) KavitaEntryType.Issue else KavitaEntryType.Chapter
                        chapter.number == UNNUMBERED_NUMBER.toInt().toString() -> KavitaEntryType.Volume
                        else -> KavitaEntryType.Chapter
                    }
                    id to KavitaChapterInfo(
                        volume = volumeNumber.takeIf { it > UNNUMBERED_NUMBER && it < SPECIAL_NUMBER },
                        number = (chapter.minNumber ?: chapter.number?.toFloatOrNull())
                            .takeIf { type == KavitaEntryType.Chapter || type == KavitaEntryType.Issue },
                        pages = chapter.pages ?: 0,
                        type = type,
                    )
                }
            }.toMap(),
        )
    }

    suspend fun updateProgress(track: Track): Track {
        val requestUrl = "${getApiFromUrl(track.tracking_url)}/Tachiyomi/mark-chapter-until-as-read?seriesId=${getIdFromUrl(track.tracking_url)}&chapterNumber=${track.last_chapter_read}"
        authClient.newCall(POST(requestUrl, body = "{}".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())))
            .awaitSuccess()
        return getTrackSearch(track.tracking_url)
    }

    companion object {
        private const val UNNUMBERED_NUMBER = -100_000f
        private const val SPECIAL_NUMBER = 100_000f
    }
}
