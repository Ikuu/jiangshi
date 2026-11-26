package yokai.presentation.stats

import eu.kanade.tachiyomi.data.track.kavita.KavitaChapterInfo
import eu.kanade.tachiyomi.data.track.kavita.KavitaEntryType
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.EntryKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EntryKindsTest {
    private fun info(type: KavitaEntryType) = KavitaChapterInfo(volume = 1f, number = null, pages = 20, pagesRead = 0, type = type)

    @Test
    fun `Kavita's entry types are matched to chapters by their ID`() {
        val urls = listOf("/Chapter/11_1", "/Chapter/12_1", "/Chapter/99", "/other")
        val chapters = mapOf(11 to info(KavitaEntryType.Volume), 12 to info(KavitaEntryType.Issue))

        assertEquals(
            mapOf("/Chapter/11_1" to EntryKind.Volume, "/Chapter/12_1" to EntryKind.Issue),
            kavitaEntryKinds(urls, chapters),
        )
    }

    @Test
    fun `what Kavita says takes over from the guesses, and the usual kind is the most common`() {
        val local = mapOf("a" to EntryKind.Chapter, "b" to EntryKind.Chapter, "c" to EntryKind.Chapter)
        val kinds = EntryKinds.from(local, mapOf("a" to EntryKind.Volume, "b" to EntryKind.Volume))

        assertEquals(EntryKind.Volume, kinds.of("a"))
        assertEquals(EntryKind.Chapter, kinds.of("c"))
        assertEquals(EntryKind.Volume, kinds.usual)
        assertEquals(EntryKind.Volume, kinds.of("unknown"))
    }
}
