package yokai.domain.stats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadingSessionTimerTest {
    private val minute = 60_000L
    private val timer = ReadingSessionTimer(idleCutoff = 5 * minute)

    private fun chapter(url: String) = ReadingSessionTimer.Target(
        mangaSource = 1L,
        mangaUrl = "/series/1",
        mangaTitle = "Series",
        seriesType = 1,
        chapterUrl = url,
        chapterNumber = 1.0,
    )

    @Test
    fun `counts time between page turns`() {
        timer.onActivity(chapter("c1"), null, 0)
        timer.onActivity(chapter("c1"), 0, 1 * minute)
        timer.onActivity(chapter("c1"), 1, 3 * minute)
        val session = timer.stop(4 * minute)!!

        assertEquals(4 * minute, session.duration)
        assertEquals(0L, session.startedAt)
        assertEquals(4 * minute, session.endedAt)
        assertEquals(2, session.pagesRead)
        assertFalse(session.completed)
    }

    @Test
    fun `the session so far carries on afterwards`() {
        timer.onActivity(chapter("c1"), 0, 0)
        timer.onActivity(chapter("c1"), 1, 2 * minute)
        timer.markCompleted("c1")

        val soFar = timer.current(3 * minute)!!
        assertEquals(3 * minute, soFar.duration)
        assertEquals(2, soFar.pagesRead)
        assertTrue(soFar.completed)

        // Looking doesn't stop the timer, and a long wait still counts only up to the cutoff
        assertEquals(7 * minute, timer.current(20 * minute)!!.duration)
        assertEquals(4 * minute, timer.stop(4 * minute)!!.duration)
        assertNull(timer.current(5 * minute))
    }

    @Test
    fun `caps long gaps at the idle cutoff`() {
        timer.onActivity(chapter("c1"), 0, 0)
        timer.onActivity(chapter("c1"), 1, 30 * minute)
        val session = timer.stop(31 * minute)!!

        assertEquals(6 * minute, session.duration)
        assertEquals(31 * minute, session.endedAt)
    }

    @Test
    fun `moving to another chapter closes the previous session`() {
        timer.onActivity(chapter("c1"), 0, 0)
        timer.onActivity(chapter("c1"), 1, 1 * minute)
        timer.markCompleted("c1")
        val first = timer.onActivity(chapter("c2"), 0, 2 * minute)

        assertNotNull(first)
        assertEquals("c1", first!!.chapterUrl)
        assertEquals(2 * minute, first.duration)
        assertTrue(first.completed)

        val second = timer.stop(3 * minute)!!
        assertEquals("c2", second.chapterUrl)
        assertEquals(2 * minute, second.startedAt)
        assertEquals(1 * minute, second.duration)
        assertEquals(1, second.pagesRead)
        assertFalse(second.completed)
    }

    @Test
    fun `counts each page once`() {
        timer.onActivity(chapter("c1"), 0, 0)
        timer.onActivity(chapter("c1"), 1, 1000)
        timer.onActivity(chapter("c1"), 0, 2000)
        assertEquals(2, timer.stop(3000)!!.pagesRead)
    }

    @Test
    fun `ignores completion of another chapter`() {
        timer.onActivity(chapter("c1"), 0, 0)
        timer.markCompleted("c2")
        assertFalse(timer.stop(minute)!!.completed)
    }

    @Test
    fun `drops sessions too short to count`() {
        timer.onActivity(chapter("c1"), null, 0)
        assertNull(timer.stop(500))
    }

    @Test
    fun `stopping without a session does nothing`() {
        assertNull(timer.stop(minute))
        timer.onActivity(chapter("c1"), 0, 0)
        timer.stop(minute)
        assertNull(timer.stop(2 * minute))
    }

    @Test
    fun `a paused session starts fresh on resume`() {
        timer.onActivity(chapter("c1"), 0, 0)
        timer.stop(minute)
        timer.onActivity(chapter("c1"), null, 60 * minute)
        val session = timer.stop(61 * minute)!!

        assertEquals(60 * minute, session.startedAt)
        assertEquals(1 * minute, session.duration)
        assertEquals(0, session.pagesRead)
    }
}
