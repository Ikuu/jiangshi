package yokai.domain.stats

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadingSessionMergeTest {
    private val utc = ZoneOffset.UTC
    private val evening = LocalDateTime.of(2026, 11, 22, 20, 0)

    @Test
    fun `the same chapter read again soon after is merged`() {
        val first = testSession(evening, minutes = 10)
        val again = testSession(evening.plusMinutes(20), minutes = 5)
        assertTrue(first.canMergeWith(again, utc))
    }

    @Test
    fun `a long break keeps the sessions apart`() {
        val first = testSession(evening, minutes = 10)
        val later = testSession(evening.plusMinutes(10 + 16), minutes = 5)
        assertFalse(first.canMergeWith(later, utc))
    }

    @Test
    fun `other chapters and series aren't merged`() {
        val first = testSession(evening, minutes = 10)
        assertFalse(first.canMergeWith(testSession(evening.plusMinutes(11), minutes = 5, chapter = "c2"), utc))
        assertFalse(first.canMergeWith(testSession(evening.plusMinutes(11), minutes = 5, series = "b"), utc))
    }

    @Test
    fun `sessions either side of midnight stay on their own days`() {
        val late = testSession(LocalDateTime.of(2026, 11, 22, 23, 50), minutes = 5)
        val afterMidnight = testSession(LocalDateTime.of(2026, 11, 23, 0, 2), minutes = 5)
        assertFalse(late.canMergeWith(afterMidnight, utc))
    }

    @Test
    fun `merging adds up time and pages and keeps the chapter finished`() {
        val first = testSession(evening, minutes = 10, pages = 12, completed = false)
        val again = testSession(evening.plusMinutes(12), minutes = 4, pages = 6, completed = true)
        val merged = first.mergedWith(again)
        assertEquals(first.startedAt, merged.startedAt)
        assertEquals(again.endedAt, merged.endedAt)
        assertEquals(14 * MINUTE, merged.duration)
        assertEquals(18, merged.pagesRead)
        assertTrue(merged.completed)
    }
}
