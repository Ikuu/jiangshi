package yokai.presentation.stats

import android.content.Context
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.kavita.KavitaChapterInfo
import eu.kanade.tachiyomi.data.track.kavita.KavitaEntryType
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.EntryKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import yokai.domain.stats.ReadingCategory
import yokai.domain.stats.SeriesStats
import yokai.i18n.MR
import yokai.presentation.stats.StatsScreenModel.SeriesKey
import yokai.util.lang.getString

/** Whether a series' entries are chapters, issues or volumes, so counts use the right word. */
data class EntryKinds(val byUrl: Map<String, EntryKind>, val usual: EntryKind) {
    fun of(url: String): EntryKind = byUrl[url] ?: usual

    companion object {
        /** The app's guesses from chapter names in [local], with what the Kavita server said in [kavita] taking over. */
        fun from(local: Map<String, EntryKind>, kavita: Map<String, EntryKind>): EntryKinds {
            val kinds = local + kavita
            return EntryKinds(
                byUrl = kinds,
                usual = kinds.values.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: EntryKind.Chapter,
            )
        }
    }
}

/**
 * Asks the Kavita server how each of the series with [keys] that come from Kavita is laid out, such as which
 * entries are whole volumes, keyed by Kavita chapter ID. Series that can't be fetched are left out.
 */
internal suspend fun fetchKavitaLayouts(
    keys: Collection<SeriesKey>,
    trackManager: TrackManager,
    sourceManager: SourceManager,
): Map<SeriesKey, Map<Int, KavitaChapterInfo>> {
    val kavita = trackManager.kavita
    val fromKavita = keys.filter { kavita.accept(sourceManager.getOrStub(it.source)) }
    if (fromKavita.isEmpty()) return emptyMap()
    return coroutineScope {
        fromKavita.map { key ->
            async {
                try {
                    key to kavita.api.getSeriesLayout(key.url).chapters
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Logger.w(e) { "Could not get Kavita volumes for ${key.url}" }
                    null
                }
            }
        }.awaitAll().filterNotNull().toMap()
    }
}

/** The kinds the Kavita server gives the entries with [urls], from a series' [chapters] by Kavita chapter ID. */
internal fun kavitaEntryKinds(urls: Collection<String>, chapters: Map<Int, KavitaChapterInfo>): Map<String, EntryKind> =
    urls.mapNotNull { url ->
        val id = url.substringAfter("/Chapter/").substringBefore("_").substringBefore("?").toIntOrNull()
        val type = id?.let { chapters[it]?.type } ?: return@mapNotNull null
        url to when (type) {
            KavitaEntryType.Volume -> EntryKind.Volume
            KavitaEntryType.Chapter -> EntryKind.Chapter
            KavitaEntryType.Issue -> EntryKind.Issue
            KavitaEntryType.Special -> EntryKind.Special
        }
    }.toMap()

internal fun Map<SeriesKey, EntryKinds>.entryKind(series: SeriesStats, url: String): EntryKind =
    (this[series.key]?.of(url) ?: EntryKind.Chapter).forCategory(series.category)

/** A numbered chapter of a comic is an issue, even where Kavita files it under a volume. */
private fun EntryKind.forCategory(category: ReadingCategory): EntryKind =
    if (this == EntryKind.Chapter && category == ReadingCategory.COMIC) EntryKind.Issue else this

/** "3 issues read", or "0 volumes read" in the series' usual kind of entry when none were finished. */
internal fun Context.entriesRead(entryKinds: Map<SeriesKey, EntryKinds>, series: SeriesStats): String {
    val kinds = series.finishedChapterUrls.map { entryKinds.entryKind(series, it) }
    val count = if (kinds.isEmpty()) {
        val usual = (entryKinds[series.key]?.usual ?: EntryKind.Chapter).forCategory(series.category)
        getString(usual.plural, 0, 0)
    } else {
        ChapterUtil.entryCountText(this, kinds)
    }
    return getString(MR.strings.stats_entries_read, count)
}
