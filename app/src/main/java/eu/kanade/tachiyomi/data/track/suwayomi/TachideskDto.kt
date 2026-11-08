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
)
