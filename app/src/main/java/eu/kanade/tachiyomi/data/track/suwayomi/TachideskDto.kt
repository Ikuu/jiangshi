package eu.kanade.tachiyomi.data.track.suwayomi

import kotlinx.serialization.Serializable

@Serializable
data class TrackedMangaData(val manga: TrackedManga)

@Serializable
data class TrackedManga(
    val title: String,
    val description: String?,
    val status: String,
    val unreadCount: Long,
    val lastReadChapter: LastReadChapter?,
    val chapters: ChapterCount,
)

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

@Serializable
data class ChapterNode(
    val id: Int,
    val chapterNumber: Float,
    val isRead: Boolean,
    val lastPageRead: Int,
    val pageCount: Int,
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
