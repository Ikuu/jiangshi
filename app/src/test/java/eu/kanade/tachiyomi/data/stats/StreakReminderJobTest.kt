package eu.kanade.tachiyomi.data.stats

import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StreakReminderJobTest {
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, ZoneOffset.UTC)

    @Test
    fun `the reminder runs this evening if it's still to come`() {
        assertEquals(at(18, 20), StreakReminderJob.nextRun(at(18, 9, 30), 20))
    }

    @Test
    fun `the reminder runs tomorrow once this evening's time has passed`() {
        assertEquals(at(19, 20), StreakReminderJob.nextRun(at(18, 20), 20))
        assertEquals(at(19, 20), StreakReminderJob.nextRun(at(18, 20, 5), 20))
    }
}
