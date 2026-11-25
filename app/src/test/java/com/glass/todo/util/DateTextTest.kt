package com.glass.todo.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DateTextTest {

    @Test
    fun `epoch day and UTC midnight millis round trip`() {
        val day = LocalDate.of(2026, 11, 25)
        val millis = DateText.epochDayToUtcMillis(day.toEpochDay())

        assertEquals(Instant.parse("2026-11-25T00:00:00Z").toEpochMilli(), millis)
        assertEquals(day.toEpochDay(), DateText.utcMillisToEpochDay(millis))
    }

    @Test
    fun `picker UTC midnight is not shifted by a negative local timezone`() {
        val original = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"))
            // MaterialDatePicker 选择 11 月 25 日时返回 UTC 11 月 25 日 00:00。
            // 若按纽约本地时区解析会得到 11 月 24 日，这正是必须走 UTC 的原因。
            val utcMillis = Instant.parse("2026-11-25T00:00:00Z").toEpochMilli()

            assertEquals(
                LocalDate.of(2026, 11, 25).toEpochDay(),
                DateText.utcMillisToEpochDay(utcMillis)
            )
        } finally {
            java.util.TimeZone.setDefault(original)
        }
    }

    @Test
    fun `calendar conversion is independent of leap days`() {
        val leapDay = LocalDate.of(2028, 2, 29)
        val dayBefore = LocalDate.of(2028, 2, 28)

        assertEquals(
            leapDay.toEpochDay(),
            DateText.utcMillisToEpochDay(DateText.epochDayToUtcMillis(leapDay.toEpochDay()))
        )
        assertEquals(1L, leapDay.toEpochDay() - dayBefore.toEpochDay())
    }

    @Test
    fun `exact deadline keeps absolute date and zero padded time`() {
        val day = LocalDate.of(2026, 11, 9).toEpochDay()

        assertEquals("11 月 9 日 08:05", DateText.formatExactDateTime(day, 8 * 60 + 5, LocalDate.of(2026, 11, 25)))
        assertEquals("11 月 9 日\n08:05", DateText.formatExactDateTime(day, 485, LocalDate.of(2026, 11, 25), multiline = true))
        assertEquals("00:00", DateText.formatTime(0))
        assertEquals("23:59", DateText.formatTime(1439))
    }

    @Test
    fun `exact deadline includes year outside current year`() {
        val day = LocalDate.of(2027, 1, 2).toEpochDay()

        assertEquals(
            "2027 年 1 月 2 日 18:30",
            DateText.formatExactDateTime(day, 18 * 60 + 30, LocalDate.of(2026, 11, 25))
        )
    }

    @Test
    fun `deadline minute normalization preserves disabled state`() {
        assertEquals(null, DateText.normalizeMinuteOfDay(null))
        assertEquals(0, DateText.normalizeMinuteOfDay(-10))
        assertEquals(1439, DateText.normalizeMinuteOfDay(2000))
        assertEquals(540, DateText.normalizeMinuteOfDay(540))
    }
}
