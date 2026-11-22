package eu.kanade.tachiyomi.data.track.suwayomi

import eu.kanade.tachiyomi.data.database.models.Chapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SuwayomiDownloadAheadTest {
    private fun chapter(number: Float, read: Boolean = false) = Chapter.create().apply {
        chapter_number = number
        this.read = read
    }

    @Test
    fun `takes the next unread chapters in order`() {
        val chapters = listOf(chapter(5f), chapter(3f), chapter(4f), chapter(2f), chapter(1f, read = true))
        assertEquals(listOf(3f, 4f), Suwayomi.nextUnreadNumbers(chapters, 2f, 2).toList())
    }

    @Test
    fun `skips chapters already read further on`() {
        val chapters = listOf(chapter(2f, read = true), chapter(3f), chapter(4f, read = true), chapter(5f))
        assertEquals(listOf(3f, 5f), Suwayomi.nextUnreadNumbers(chapters, 1f, 2).toList())
    }

    @Test
    fun `counts chapters sharing a number once`() {
        val chapters = listOf(chapter(2f), chapter(2f), chapter(3f))
        assertEquals(listOf(2f, 3f), Suwayomi.nextUnreadNumbers(chapters, 1f, 2).toList())
    }

    @Test
    fun `leaves out chapters without a number`() {
        val chapters = listOf(chapter(-1f), chapter(2f))
        assertEquals(setOf(2f), Suwayomi.nextUnreadNumbers(chapters, 1f, 3))
    }
}
