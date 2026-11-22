package eu.kanade.tachiyomi.data.track.suwayomi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrackedMangaData(val manga: TrackedManga)

/**
 * Servers with user accounts keep the reader's own counts under [user], and older ones on the series itself,
 * so only one of the two is filled in.
 */
@Serializable
data class TrackedManga(
    val title: String,
    val description: String?,
    val status: String,
    val chapters: ChapterCount,
    val unreadCount: Long? = null,
    val lastReadChapter: LastReadChapter? = null,
    val user: TrackedMangaUser? = null,
) {
    val unreadChapters: Long get() = user?.unreadCount ?: unreadCount ?: 0L
    val lastReadChapterNumber: Float? get() = (user?.lastReadChapter ?: lastReadChapter)?.chapterNumber
}

@Serializable
data class TrackedMangaUser(val unreadCount: Long? = null, val lastReadChapter: LastReadChapter? = null)

@Serializable
data class LastReadChapter(val chapterNumber: Float)

@Serializable
data class ChapterCount(val totalCount: Long)

@Serializable
data class MangaChaptersData(val manga: MangaChapters)

@Serializable
data class MangaChapters(val chapters: ChapterNodes)

@Serializable
data class ChapterNodes(val nodes: List<ChapterNode>)

/** Like [TrackedManga], the reader's own state is either under [user] or on the chapter itself. */
@Serializable
data class ChapterNode(
    val id: Int,
    val chapterNumber: Float,
    val pageCount: Int,
    @SerialName("isRead") private val ownIsRead: Boolean = false,
    @SerialName("isBookmarked") private val ownIsBookmarked: Boolean = false,
    @SerialName("lastPageRead") private val ownLastPageRead: Int = 0,
    val user: ChapterUser? = null,
) {
    val isRead: Boolean get() = user?.isRead ?: ownIsRead
    val isBookmarked: Boolean get() = user?.isBookmarked ?: ownIsBookmarked
    val lastPageRead: Int get() = user?.lastPageRead ?: ownLastPageRead
}

@Serializable
data class ChapterUser(
    val isRead: Boolean = false,
    val isBookmarked: Boolean = false,
    val lastPageRead: Int = 0,
)

@Serializable
data class ServerTrackersData(val manga: ServerTrackersManga, val chapters: ReadChapterNodes)

@Serializable
data class ServerTrackersManga(val trackRecords: ServerTrackRecordNodes)

@Serializable
data class ServerTrackRecordNodes(val nodes: List<ServerTrackRecord>)

@Serializable
data class ServerTrackRecord(val lastChapterRead: Float, val status: Int, val tracker: ServerTracker)

@Serializable
data class ServerTracker(
    val name: String,
    val isLoggedIn: Boolean,
    val isTokenExpired: Boolean,
    val statuses: List<ServerTrackStatus>,
)

@Serializable
data class ServerTrackStatus(val name: String, val value: Int)

@Serializable
data class ReadChapterNodes(val nodes: List<LastReadChapter>)

/** A tracker linked to the series on the Suwayomi server, as shown in the tracking sheet. */
data class ServerTrackerInfo(
    val name: String,
    val status: String?,
    val lastChapterRead: Float,
    /** The server didn't pass on the latest chapter read there. */
    val isBehind: Boolean,
    val isSignedOut: Boolean,
)
