package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.database.models.Chapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LinkedSeriesTest {
    private fun chapter(number: Float, name: String = "Chapter $number") = Chapter.create().apply {
        chapter_number = number
        this.name = name
    }

    @Test
    fun `chapters match by number`() {
        val chapters = listOf(chapter(1f), chapter(2.5f))
        assertEquals(listOf(1f, 2.5f), LinkedSeries.matchable(chapters).map { it.chapter_number })
    }

    @Test
    fun `whole volumes and chapters without a number are left out`() {
        val chapters = listOf(chapter(3f, "Volume 3"), chapter(-1f, "Extra"), chapter(4f))
        assertEquals(listOf(4f), LinkedSeries.matchable(chapters).map { it.chapter_number })
    }

    @Test
    fun `library series are offered only when their title matches the search`() {
        assertTrue(LinkedSeries.titleMatches("One Piece", "one piece"))
        assertTrue(LinkedSeries.titleMatches("One Piece (Digital Colored Comics)", "One Piece"))
        assertTrue(LinkedSeries.titleMatches("Kingdom", "Kingdon"))
        assertTrue(LinkedSeries.titleMatches("Spy x Family", "Spy×Family"))
        assertFalse(LinkedSeries.titleMatches("Kingdom", "One Piece"))
        assertFalse(LinkedSeries.titleMatches("Absolute Batman", "One Piece"))
        assertFalse(LinkedSeries.titleMatches("One Piece", "  "))
    }
}
