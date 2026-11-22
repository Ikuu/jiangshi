package eu.kanade.tachiyomi.data.track.suwayomi

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.ServerStatus
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.withIOContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
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

/** Errors a GraphQL request came back with. */
class GraphQLException(val messages: List<String>) : IllegalStateException(messages.firstOrNull() ?: "GraphQL request failed") {
    /** The server doesn't have this field, such as one added in a later version. */
    fun isUndefinedField(field: String) = messages.any { "FieldUndefined" in it && "'$field'" in it }
}

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

        val manga = graphqlPerUser<TrackedMangaData>(url, ::trackedMangaQuery, mangaIdVariables(url)).manga

        TrackSearch.create(TrackManager.SUWAYOMI).apply {
            title = manga.title
            cover_url = "$url/thumbnail"
            summary = manga.description.orEmpty()
            tracking_url = url
            total_chapters = manga.chapters.totalCount
            publishing_status = manga.status
            last_chapter_read = manga.lastReadChapterNumber ?: 0F
            status = when (manga.unreadChapters) {
                manga.chapters.totalCount -> Suwayomi.UNREAD
                0L -> Suwayomi.COMPLETED
                else -> Suwayomi.READING
            }
        }
    }

    suspend fun updateProgress(track: Track): Track {
        val url = track.tracking_url
        val chapters = mangaChapters(url)
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
        mangaChapters(url)
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
        val chapters = mangaChapters(url)
        val ids = chapters.filter { it.chapterNumber in chapterNumbers && it.isRead != read }.map { it.id }
        if (ids.isEmpty()) return@withIOContext

        val mutation = if (read) MARK_CHAPTERS_READ_MUTATION else MARK_CHAPTERS_UNREAD_MUTATION
        graphql<JsonObject>(url, mutation, idsVariables(ids))
        if (read && updateServerTrackers) graphql<JsonObject>(url, TRACK_PROGRESS_MUTATION, mangaIdVariables(url))
    }

    /** Bookmarks or unbookmarks the server's chapters with these numbers. */
    suspend fun setChaptersBookmarked(trackUrl: String, chapterNumbers: Set<Float>, bookmarked: Boolean) = withIOContext {
        val url = resolveMangaUrl(trackUrl)
        val chapters = mangaChapters(url)
        val ids = chapters.filter { it.chapterNumber in chapterNumbers && it.isBookmarked != bookmarked }.map { it.id }
        if (ids.isEmpty()) return@withIOContext
        graphql<JsonObject>(url, SET_BOOKMARKED_MUTATION, idsVariables(ids) { put("bookmarked", bookmarked) })
    }

    /**
     * IDs of the server's chapters with this number. They're remembered so that page turns don't
     * fetch a long series' whole chapter list each time. The server can't look up a chapter by its
     * number directly, as that query fails on it with a type error.
     */
    private suspend fun chapterIds(url: String, chapterNumber: Float): List<Int> {
        chapterIdCache[url]?.get(chapterNumber)?.let { return it }
        val ids = mangaChapters(url)
            .groupBy({ it.chapterNumber }, { it.id })
        chapterIdCache[url] = ids
        return ids[chapterNumber].orEmpty()
    }

    /**
     * Each chapter on the server, with whether it's downloaded there or waiting in the server's queue, by
     * chapter number. Several chapters can share a number, such as from different scanlators, so a number
     * counts as downloaded if any of them is.
     */
    suspend fun getServerDownloads(trackUrl: String): Map<Float, ServerDownload> = withIOContext {
        val url = resolveMangaUrl(trackUrl)
        val chapters = mangaChapters(url)
        val queue = graphql<DownloadStatusData>(url, DOWNLOAD_QUEUE_QUERY, buildJsonObject { }).downloadStatus.queue
            .associateBy { it.chapter.id }
        chapters.filter { it.chapterNumber >= 0f }.groupBy { it.chapterNumber }.mapValues { (_, matches) ->
            val queued = matches.mapNotNull { queue[it.id] }.filter { it.state != DOWNLOAD_FINISHED }
            ServerDownload(
                isDownloaded = matches.any { it.isDownloaded },
                isQueued = queued.isNotEmpty(),
                progress = queued.maxOfOrNull { it.progress } ?: 0f,
                hasError = queued.any { it.state == DOWNLOAD_ERROR },
            )
        }
    }

    /** Asks the server to download its chapters with these numbers that it doesn't have yet. Returns how many it queued. */
    suspend fun downloadOnServer(trackUrl: String, chapterNumbers: Set<Float>): Int = withIOContext {
        val url = resolveMangaUrl(trackUrl)
        val ids = mangaChapters(url).filter { it.chapterNumber in chapterNumbers && !it.isDownloaded }.map { it.id }
        if (ids.isNotEmpty()) graphql<JsonObject>(url, ENQUEUE_DOWNLOADS_MUTATION, idsVariables(ids))
        ids.size
    }

    /**
     * Removes the server's downloads of these chapters, and takes any still waiting off its queue.
     * Returns how many chapters it removed or took off.
     */
    suspend fun removeFromServer(trackUrl: String, chapterNumbers: Set<Float>): Int = withIOContext {
        val url = resolveMangaUrl(trackUrl)
        val chapters = mangaChapters(url).filter { it.chapterNumber in chapterNumbers }
        val downloaded = chapters.filter { it.isDownloaded }.map { it.id }
        val queued = graphql<DownloadStatusData>(url, DOWNLOAD_QUEUE_QUERY, buildJsonObject { }).downloadStatus.queue
            .map { it.chapter.id }.toSet()
        val waiting = chapters.map { it.id }.filter { it in queued && it !in downloaded }
        if (waiting.isNotEmpty()) graphql<JsonObject>(url, DEQUEUE_DOWNLOADS_MUTATION, idsVariables(waiting))
        if (downloaded.isNotEmpty()) graphql<JsonObject>(url, DELETE_DOWNLOADS_MUTATION, idsVariables(downloaded))
        downloaded.size + waiting.size
    }

    /**
     * Checks the Suwayomi server the extension is set up for: whether it answers, whether it takes the
     * extension's sign-in, and which version it runs.
     */
    suspend fun checkServer(): ServerStatus = withIOContext {
        try {
            val baseUrl = source.baseUrl.trimEnd('/')
            if (baseUrl.isBlank() || !baseUrl.startsWith("http")) return@withIOContext ServerStatus.Problem(ServerStatus.Reason.NOT_SET_UP)
            val about = graphqlAt<AboutServerData>(baseUrl, ABOUT_SERVER_QUERY, buildJsonObject { }).aboutServer
            ServerStatus.Connected(about.version.takeIf { it.isNotBlank() }, username = null)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(e) { "Suwayomi server check failed" }
            ServerStatus.of(e)
        }
    }

    private suspend fun mangaChapters(url: String): List<ChapterNode> =
        graphqlPerUser<MangaChaptersData>(url, ::mangaChaptersQuery, mangaIdVariables(url)).manga.chapters.nodes

    /** Series are identified by either their numeric ID or their full API URL. */
    private fun resolveMangaUrl(trackUrl: String): String =
        trackUrl.toLongOrNull()?.let { "${source.baseUrl.trimEnd('/')}/api/v1/manga/$it" } ?: trackUrl

    private fun mangaId(mangaUrl: String): Int = mangaUrl.trimEnd('/').substringAfterLast('/').toInt()

    private fun mangaIdVariables(mangaUrl: String): JsonObject = buildJsonObject { put("id", mangaId(mangaUrl)) }

    private fun idsVariables(ids: List<Int>, extra: JsonObjectBuilder.() -> Unit = {}): JsonObject = buildJsonObject {
        putJsonArray("ids") { ids.forEach { add(it) } }
        extra()
    }

    /**
     * Whether the server keeps read status and the like per user, under `user`. Servers from before user
     * accounts only have the older fields, and newer ones have deprecated those, so whichever works is
     * remembered. Null until a query has found out.
     */
    @Volatile
    private var perUserFields: Boolean? = null

    /** Runs a query written both ways, as [perUserFields] says, finding out which the server takes the first time. */
    private suspend inline fun <reified T> graphqlPerUser(
        mangaUrl: String,
        query: (perUser: Boolean) -> String,
        variables: JsonObject,
    ): T {
        if (perUserFields != false) {
            try {
                return graphql<T>(mangaUrl, query(true), variables).also { perUserFields = true }
            } catch (e: GraphQLException) {
                if (!e.isUndefinedField("user")) throw e
                perUserFields = false
            }
        }
        return graphql(mangaUrl, query(false), variables)
    }

    /** Tracking URLs keep their REST form, so links made by earlier versions still work. */
    private suspend inline fun <reified T> graphql(mangaUrl: String, query: String, variables: JsonObject): T =
        graphqlAt(mangaUrl.substringBefore("/api/v1/manga/"), query, variables)

    /** Runs a query on the server at [baseUrl], signed in as the extension is. */
    private suspend inline fun <reified T> graphqlAt(baseUrl: String, query: String, variables: JsonObject): T {
        val body = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val endpoint = "$baseUrl/api/graphql"
        val extension = source
        val response = extension.client.newCall(POST(endpoint, extension.headers, body)).awaitSuccess().parseAs<JsonObject>()

        // GraphQL reports failures with a 200 response
        (response["errors"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { errors ->
            throw GraphQLException(errors.mapNotNull { it.jsonObject["message"]?.jsonPrimitive?.content })
        }
        return json.decodeFromJsonElement(response.getValue("data"))
    }

    companion object {
        const val SOURCE_CLASS = "eu.kanade.tachiyomi.extension.all.tachidesk.Tachidesk"

        private const val DOWNLOAD_FINISHED = "FINISHED"
        private const val DOWNLOAD_ERROR = "ERROR"

        private fun trackedMangaQuery(perUser: Boolean): String {
            val fields = "unreadCount lastReadChapter { chapterNumber }"
            return """
                query TrackedManga(${'$'}id: Int!) {
                    manga(id: ${'$'}id) {
                        title
                        description
                        status
                        ${if (perUser) "user { $fields }" else fields}
                        chapters { totalCount }
                    }
                }
            """
        }

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

        private fun mangaChaptersQuery(perUser: Boolean): String {
            val fields = "isRead isBookmarked lastPageRead isDownloaded"
            return """
                query MangaChapters(${'$'}id: Int!) {
                    manga(id: ${'$'}id) {
                        chapters { nodes { id chapterNumber pageCount ${if (perUser) "user { $fields }" else fields} } }
                    }
                }
            """
        }

        // The chapter count needs signing in on servers that ask for it, so this also checks the sign-in
        private const val ABOUT_SERVER_QUERY = """
            query AboutServer {
                aboutServer { version }
                chapters(first: 1) { totalCount }
            }
        """

        private const val DOWNLOAD_QUEUE_QUERY = """
            query DownloadQueue {
                downloadStatus { queue { chapter { id } state progress } }
            }
        """

        private const val ENQUEUE_DOWNLOADS_MUTATION = """
            mutation EnqueueDownloads(${'$'}ids: [Int!]!) {
                enqueueChapterDownloads(input: { ids: ${'$'}ids }) {
                    downloadStatus { state }
                }
            }
        """

        private const val DEQUEUE_DOWNLOADS_MUTATION = """
            mutation DequeueDownloads(${'$'}ids: [Int!]!) {
                dequeueChapterDownloads(input: { ids: ${'$'}ids }) {
                    downloadStatus { state }
                }
            }
        """

        private const val DELETE_DOWNLOADS_MUTATION = """
            mutation DeleteDownloads(${'$'}ids: [Int!]!) {
                deleteDownloadedChapters(input: { ids: ${'$'}ids }) {
                    chapters { id }
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

        private const val SET_BOOKMARKED_MUTATION = """
            mutation SetBookmarked(${'$'}ids: [Int!]!, ${'$'}bookmarked: Boolean!) {
                updateChapters(input: { ids: ${'$'}ids, patch: { isBookmarked: ${'$'}bookmarked } }) {
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
