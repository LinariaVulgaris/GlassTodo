package com.glass.todo.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DayMathTest {

    @Test
    fun `daily trigger stays at local 0005 after spring daylight saving change`() {
        val zone = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 3, 8, 0, 6, 0, 0, zone)

        assertEquals(
            Instant.parse("2026-03-09T04:05:00Z").toEpochMilli(),
            DayMath.nextDailyTriggerMillis(now)
        )
    }

    @Test
    fun `daily trigger stays at local 0005 after fall daylight saving change`() {
        val zone = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 11, 1, 0, 6, 0, 0, zone)

        assertEquals(
            Instant.parse("2026-11-02T05:05:00Z").toEpochMilli(),
            DayMath.nextDailyTriggerMillis(now)
        )
    }

    @Test
    fun `daily trigger advances to tomorrow after today's local 0005`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = ZonedDateTime.of(2026, 11, 25, 0, 5, 0, 0, zone)

        assertEquals(
            Instant.parse("2026-11-25T16:05:00Z").toEpochMilli(),
            DayMath.nextDailyTriggerMillis(now)
        )
    }

    @Test
    fun `urgent date threshold includes boundary and overdue dates`() {
        assertTrue(DayMath.isUrgent(-1, 3))
        assertTrue(DayMath.isUrgent(0, 0))
        assertTrue(DayMath.isUrgent(3, 3))
        assertFalse(DayMath.isUrgent(4, 3))

        val from = LocalDate.of(2026, 11, 25)
        assertNull(DayMath.badgeFor(null, from))
        assertEquals("今天", DayMath.badgeFor(from.toEpochDay(), from))
        assertEquals("明天", DayMath.badgeFor(from.plusDays(1).toEpochDay(), from))
        assertEquals("昨天", DayMath.badgeFor(from.minusDays(1).toEpochDay(), from))
        assertEquals("剩 9 天", DayMath.badgeFor(from.plusDays(9).toEpochDay(), from))
        assertEquals("超 2 天", DayMath.badgeFor(from.minusDays(2).toEpochDay(), from))
    }
}
