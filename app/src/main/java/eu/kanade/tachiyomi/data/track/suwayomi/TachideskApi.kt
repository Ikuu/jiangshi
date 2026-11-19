package eu.kanade.tachiyomi.data.track.suwayomi

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.withIOContext
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.injectLazy

class TachideskApi {
    private val json: Json by injectLazy()
    private val sourceManager: SourceManager by injectLazy()

    /**
     * The installed Suwayomi extension. Requests go through its client so they're signed in the
     * same way as the extension, whichever login mode the server uses.
     */
    private val source: HttpSource
        get() = sourceManager.getOnlineSources().find { it::class.qualifiedName == SOURCE_CLASS }
            ?: throw IllegalStateException("The Suwayomi extension isn't installed")

    private val chapterIdCache = ConcurrentHashMap<String, Map<Float, List<Int>>>()

    suspend fun getTrackSearch(trackUrl: String): TrackSearch = withIOContext {
        val url = resolveMangaUrl(trackUrl)

        val manga = graphql<TrackedMangaData>(url, TRACKED_MANGA_QUERY, mangaIdVariables(url)).manga

        TrackSearch.create(TrackManager.SUWAYOMI).apply {
            title = manga.title
            cover_url = "$url/thumbnail"
            summary = manga.description.orEmpty()
            tracking_url = url
            total_chapters = manga.chapters.totalCount
            publishing_status = manga.status
            last_chapter_read = manga.lastReadChapter?.chapterNumber ?: 0F
            status = when (manga.unreadCount) {
                manga.chapters.totalCount -> Suwayomi.UNREAD
                0L -> Suwayomi.COMPLETED
                else -> Suwayomi.READING
            }
        }
    }

    suspend fun updateProgress(track: Track): Track {
        val url = track.tracking_url
        val chapters = graphql<MangaChaptersData>(url, MANGA_CHAPTERS_QUERY, mangaIdVariables(url)).manga.chapters.nodes
        // Chapters with an unknown number are -1, so they aren't "before" the one just read
        val unreadIds = chapters
            .filter { !it.isRead && it.chapterNumber in 0f..track.last_chapter_read }
            .map { it.id }

        if (unreadIds.isNotEmpty()) {
            graphql<JsonObject>(url, MARK_CHAPTERS_READ_MUTATION, idsVariables(unreadIds))
        }
        // Marking chapters read over GraphQL doesn't update the trackers linked on the server
        graphql<JsonObject>(url, TRACK_PROGRESS_MUTATION, mangaIdVariables(url))

        return getTrackSearch(track.tracking_url)
    }

    /** Trackers linked to the series on the server, and whether they have the latest chapter read there. */
    suspend fun getServerTrackers(trackUrl: String): List<ServerTrackerInfo> = withIOContext {
        val url = resolveMangaUrl(trackUrl)
        val data = graphql<ServerTrackersData>(url, SERVER_TRACKERS_QUERY, mangaIdVariables(url))
        // Trackers only take whole chapters, so 10.5 read on the server is 10 on the tracker
        val lastRead = data.chapters.nodes.firstOrNull()?.chapterNumber?.toInt() ?: 0
        data.manga.trackRecords.nodes.map { record ->
            ServerTrackerInfo(
                name = record.tracker.name,
                status = record.tracker.statuses.find { it.value == record.status }?.name,
                lastChapterRead = record.lastChapterRead,
                isBehind = record.lastChapterRead < lastRead,
                isSignedOut = !record.tracker.isLoggedIn || record.tracker.isTokenExpired,
            )
        }
    }

    suspend fun getChapterProgress(trackUrl: String): List<ChapterNode> = withIOContext {
        val url = resolveMangaUrl(trackUrl)
        graphql<MangaChaptersData>(url, MANGA_CHAPTERS_QUERY, mangaIdVariables(url)).manga.chapters.nodes
    }

    suspend fun setPageProgress(trackUrl: String, chapterNumber: Float, page: Int) = withIOContext {
        val url = resolveMangaUrl(trackUrl)
        val ids = chapterIds(url, chapterNumber)
        if (ids.isNotEmpty()) {
            graphql<JsonObject>(url, SET_PAGE_MUTATION, idsVariables(ids) { put("page", page) })
        }
    }

    /** Marks chapters read or unread, and asks the server to update its own trackers if [updateServerTrackers]. */
    suspend fun setChaptersRead(
        trackUrl: String,
        chapterNumbers: Set<Float>,
        read: Boolean,
        updateServerTrackers: Boolean,
    ) = withIOContext {
        val url = resolveMangaUrl(trackUrl)
        val chapters = graphql<MangaChaptersData>(url, MANGA_CHAPTERS_QUERY, mangaIdVariables(url)).manga.chapters.nodes
        val ids = chapters.filter { it.chapterNumber in chapterNumbers && it.isRead != read }.map { it.id }
        if (ids.isEmpty()) return@withIOContext

        val mutation = if (read) MARK_CHAPTERS_READ_MUTATION else MARK_CHAPTERS_UNREAD_MUTATION
        graphql<JsonObject>(url, mutation, idsVariables(ids))
        if (read && updateServerTrackers) graphql<JsonObject>(url, TRACK_PROGRESS_MUTATION, mangaIdVariables(url))
    }

    /**
     * IDs of the server's chapters with this number. They're remembered so that page turns don't
     * fetch a long series' whole chapter list each time. The server can't look up a chapter by its
     * number directly, as that query fails on it with a type error.
     */
    private suspend fun chapterIds(url: String, chapterNumber: Float): List<Int> {
        chapterIdCache[url]?.get(chapterNumber)?.let { return it }
        val ids = graphql<MangaChaptersData>(url, MANGA_CHAPTERS_QUERY, mangaIdVariables(url)).manga.chapters.nodes
            .groupBy({ it.chapterNumber }, { it.id })
        chapterIdCache[url] = ids
        return ids[chapterNumber].orEmpty()
    }

    /** Series are identified by either their numeric ID or their full API URL. */
    private fun resolveMangaUrl(trackUrl: String): String =
        trackUrl.toLongOrNull()?.let { "${source.baseUrl.trimEnd('/')}/api/v1/manga/$it" } ?: trackUrl

    private fun mangaId(mangaUrl: String): Int = mangaUrl.trimEnd('/').substringAfterLast('/').toInt()

    private fun mangaIdVariables(mangaUrl: String): JsonObject = buildJsonObject { put("id", mangaId(mangaUrl)) }

    private fun idsVariables(ids: List<Int>, extra: JsonObjectBuilder.() -> Unit = {}): JsonObject = buildJsonObject {
        putJsonArray("ids") { ids.forEach { add(it) } }
        extra()
    }

    /** Tracking URLs keep their REST form, so links made by earlier versions still work. */
    private suspend inline fun <reified T> graphql(mangaUrl: String, query: String, variables: JsonObject): T {
        val body = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val endpoint = "${mangaUrl.substringBefore("/api/v1/manga/")}/api/graphql"
        val extension = source
        val response = extension.client.newCall(POST(endpoint, extension.headers, body)).awaitSuccess().parseAs<JsonObject>()

        // GraphQL reports failures with a 200 response
        (response["errors"] as? JsonArray)?.firstOrNull()?.let { error ->
            throw IllegalStateException(error.jsonObject["message"]?.jsonPrimitive?.content ?: "GraphQL request failed")
        }
        return json.decodeFromJsonElement(response.getValue("data"))
    }

    companion object {
        const val SOURCE_CLASS = "eu.kanade.tachiyomi.extension.all.tachidesk.Tachidesk"

        private const val TRACKED_MANGA_QUERY = """
            query TrackedManga(${'$'}id: Int!) {
                manga(id: ${'$'}id) {
                    title
                    description
                    status
                    unreadCount
                    lastReadChapter { chapterNumber }
                    chapters { totalCount }
                }
            }
        """

        private const val SERVER_TRACKERS_QUERY = """
            query ServerTrackers(${'$'}id: Int!) {
                manga(id: ${'$'}id) {
                    trackRecords {
                        nodes {
                            lastChapterRead
                            status
                            tracker { name isLoggedIn isTokenExpired statuses { name value } }
                        }
                    }
                }
                chapters(
                    condition: { mangaId: ${'$'}id, isRead: true }
                    orderBy: CHAPTER_NUMBER
                    orderByType: DESC
                    first: 1
                ) {
                    nodes { chapterNumber }
                }
            }
        """

        private const val MANGA_CHAPTERS_QUERY = """
            query MangaChapters(${'$'}id: Int!) {
                manga(id: ${'$'}id) {
                    chapters { nodes { id chapterNumber isRead lastPageRead pageCount } }
                }
            }
        """

        private const val SET_PAGE_MUTATION = """
            mutation SetPage(${'$'}ids: [Int!]!, ${'$'}page: Int!) {
                updateChapters(input: { ids: ${'$'}ids, patch: { lastPageRead: ${'$'}page } }) {
                    chapters { id }
                }
            }
        """

        private const val MARK_CHAPTERS_UNREAD_MUTATION = """
            mutation MarkChaptersUnread(${'$'}ids: [Int!]!) {
                updateChapters(input: { ids: ${'$'}ids, patch: { isRead: false, lastPageRead: 0 } }) {
                    chapters { id }
                }
            }
        """

        private const val TRACK_PROGRESS_MUTATION = """
            mutation TrackProgress(${'$'}id: Int!) {
                trackProgress(input: { mangaId: ${'$'}id }) {
                    trackRecords { id }
                }
            }
        """

        private const val MARK_CHAPTERS_READ_MUTATION = """
            mutation MarkChaptersRead(${'$'}ids: [Int!]!) {
                updateChapters(input: { ids: ${'$'}ids, patch: { isRead: true } }) {
                    chapters { id }
                }
            }
        """
    }
}
