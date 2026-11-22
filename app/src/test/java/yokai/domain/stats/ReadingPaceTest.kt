package yokai.domain.stats

import eu.kanade.tachiyomi.domain.manga.models.Manga
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReadingPaceTest {
    private fun day(d: Int) = LocalDateTime.of(2026, 11, d, 12, 0)

    /** [count] finished chapters of [pages] pages, [minutesEach] minutes each. */
    private fun finished(series: String, count: Int, minutesEach: Long, pages: Int = 20, type: Int = Manga.TYPE_MANGA) =
        (1..count).map {
            testSession(day(it), minutesEach, series = series, chapter = "$series$it", type = type, pages = pages, completed = true)
        }

    private fun whole(pages: Int?) = UnreadEntry(1.0, pages)

    @Test
    fun `too little reading gives no pace`() {
        assertNull(ReadingPace.perChapter(finished("a", 2, 10)))
        assertEquals(600_000.0, ReadingPace.perChapter(finished("a", 3, 10)))
        // 2 chapters of 20 pages is under the 50 pages needed
        assertNull(ReadingPace.perPage(finished("a", 2, 10)))
        assertEquals(30_000.0, ReadingPace.perPage(finished("a", 3, 10)))
    }

    @Test
    fun `unfinished reading counts towards the pace`() {
        val sessions = finished("a", 3, 10) + testSession(day(5), 30, chapter = "a4", pages = 15)
        assertEquals(1_200_000.0, ReadingPace.perChapter(sessions))
        assertEquals(48_000.0, ReadingPace.perPage(sessions))
    }

    @Test
    fun `long volumes take as long as their pages`() {
        // Single issues of 20 pages at 4 minutes each, then two 1,000 page volumes left
        val issues = finished("issues", 5, 4, type = Manga.TYPE_COMIC)
        val left = ReadingPace.timeLeft(listOf(whole(1000), whole(1000)), emptyList(), ReadingCategory.COMIC, issues)
        assertEquals(400 * MINUTE, left)
    }

    @Test
    fun `entries without a length use the pace per chapter`() {
        val all = finished("b", 4, 10)
        assertEquals((100 + 5) * MINUTE, ReadingPace.timeLeft(listOf(whole(200), UnreadEntry(0.5, null)), emptyList(), ReadingCategory.MANGA, all))
    }

    @Test
    fun `the series' own pace comes first, then its kind, then all reading`() {
        val series = finished("a", 4, 5, pages = 25)
        val comics = finished("c", 5, 20, pages = 20, type = Manga.TYPE_COMIC)
        val webtoons = finished("w", 5, 4, pages = 40, type = Manga.TYPE_WEBTOON)
        val all = series + comics + webtoons

        assertEquals(Pace(perPage = 12_000.0, perChapter = 300_000.0), ReadingPace.forSeries(series, ReadingCategory.MANGA, all))
        assertEquals(Pace(perPage = 6_000.0, perChapter = 240_000.0), ReadingPace.forSeries(emptyList(), ReadingCategory.WEBTOON, all))
        // Nothing for manga besides the series itself, so a new manga goes by everything
        val overall = ReadingPace.forSeries(emptyList(), ReadingCategory.MANGA, comics + webtoons)
        // 120 minutes over 300 pages
        assertEquals(24_000.0, overall.perPage)
    }

    @Test
    fun `no pace or nothing left gives no estimate`() {
        assertNull(ReadingPace.timeLeft(listOf(whole(10)), emptyList(), ReadingCategory.MANGA, emptyList()))
        assertNull(ReadingPace.timeLeft(emptyList(), emptyList(), ReadingCategory.MANGA, finished("b", 5, 20)))
        // Only a page pace, and an entry with no length
        assertNull(Pace(perPage = 1000.0, perChapter = null).timeLeft(listOf(whole(null))))
    }

    @Test
    fun `unread entries come from the chapter's progress`() {
        // Untouched, with Kavita's page count
        assertEquals(UnreadEntry(1.0, 24), UnreadEntry.of(lastPageRead = 0, pagesLeft = 0, pageCount = 24))
        // Untouched with no length known
        assertEquals(UnreadEntry(1.0, null), UnreadEntry.of(lastPageRead = 0, pagesLeft = 0, pageCount = null))
        // Started in the reader, which knows the pages left
        assertEquals(UnreadEntry(0.25, 5), UnreadEntry.of(lastPageRead = 15, pagesLeft = 5, pageCount = null))
        // Started, with only the server's page count
        assertEquals(UnreadEntry(0.5, 12), UnreadEntry.of(lastPageRead = 12, pagesLeft = 0, pageCount = 24))
    }
}
