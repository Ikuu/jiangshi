package eu.kanade.tachiyomi.data.stats

import java.time.DayOfWeek
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WeeklyRecapJobTest {
    // 22 November 2026 is a Sunday
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, ZoneOffset.UTC)

    @Test
    fun `the recap runs on the coming last day of the week`() {
        assertEquals(at(22, 20), WeeklyRecapJob.nextRun(at(18, 9), DayOfWeek.SUNDAY, 20))
        assertEquals(at(22, 20), WeeklyRecapJob.nextRun(at(22, 19, 59), DayOfWeek.SUNDAY, 20))
    }

    @Test
    fun `the recap runs next week once this week's has passed`() {
        assertEquals(at(29, 20), WeeklyRecapJob.nextRun(at(22, 20), DayOfWeek.SUNDAY, 20))
        assertEquals(at(29, 20), WeeklyRecapJob.nextRun(at(22, 20, 5), DayOfWeek.SUNDAY, 20))
    }

    @Test
    fun `weeks ending on Saturday recap on Saturday`() {
        assertEquals(at(28, 20), WeeklyRecapJob.nextRun(at(22, 9), DayOfWeek.SATURDAY, 20))
    }
}
