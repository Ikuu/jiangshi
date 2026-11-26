package yokai.domain.stats

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StreakTest {
    private val zone = ZoneOffset.UTC

    // Wednesday 2026-11-18, 20:00
    private val now = LocalDateTime.of(2026, 11, 18, 20, 0).toInstant(ZoneOffset.UTC)

    private fun readOn(vararg days: Int) = days.map { testSession(LocalDateTime.of(2026, 11, it, 21, 0), 10) }

    @Test
    fun `tiers grow with the streak`() {
        assertNull(StreakTier.of(0))
        assertEquals(StreakTier.SPARK, StreakTier.of(1))
        assertEquals(StreakTier.SPARK, StreakTier.of(2))
        assertEquals(StreakTier.FLAME, StreakTier.of(3))
        assertEquals(StreakTier.BLAZE, StreakTier.of(7))
        assertEquals(StreakTier.INFERNO, StreakTier.of(29))
        assertEquals(StreakTier.GEM, StreakTier.of(30))
        assertEquals(StreakTier.CROWN, StreakTier.of(365))
    }

    @Test
    fun `a streak up to yesterday is at risk until today's reading`() {
        assertEquals(3, Streak.atRisk(readOn(15, 16, 17), now, zone))
    }

    @Test
    fun `reading today means nothing is at risk`() {
        assertNull(Streak.atRisk(readOn(16, 17, 18), now, zone))
    }

    @Test
    fun `a moment in the reader today doesn't keep the streak`() {
        val sessions = readOn(15, 16, 17) + briefSession(LocalDateTime.of(2026, 11, 18, 0, 30), 20)
        assertEquals(3, Streak.atRisk(sessions, now, zone))
    }

    @Test
    fun `short sessions that add up to a minute keep the streak`() {
        val sessions = readOn(15, 16, 17) +
            briefSession(LocalDateTime.of(2026, 11, 18, 9, 0), 40) +
            briefSession(LocalDateTime.of(2026, 11, 18, 12, 0), 20)
        assertNull(Streak.atRisk(sessions, now, zone))
    }

    @Test
    fun `only days with a minute of reading count`() {
        val sessions = readOn(15) + briefSession(LocalDateTime.of(2026, 11, 16, 9, 0), 59)
        assertEquals(setOf(LocalDate.of(2026, 11, 15)), Streak.days(sessions, zone))
    }

    @Test
    fun `no streak means nothing is at risk`() {
        assertNull(Streak.atRisk(readOn(14, 15), now, zone))
        assertNull(Streak.atRisk(emptyList(), now, zone))
    }
}
