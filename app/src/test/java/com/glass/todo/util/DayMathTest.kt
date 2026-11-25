package com.glass.todo.util

import com.glass.todo.data.EventEntity
import com.glass.todo.data.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayMathTest {

    private fun event(
        target: LocalDate,
        repeat: String = RepeatRule.NONE,
        countUpWhenPast: Boolean = true
    ) = EventEntity(
        title = "test",
        targetEpochDay = target.toEpochDay(),
        repeat = repeat,
        countUpWhenPast = countUpWhenPast
    )

    @Test
    fun `non repeating event keeps its original date even when past`() {
        val target = LocalDate.of(2026, 11, 20)
        val from = LocalDate.of(2026, 11, 25)

        assertEquals(target, DayMath.nextOccurrence(event(target), from))
        assertEquals(-5L, DayMath.daysFor(event(target), from))
    }

    @Test
    fun `today tomorrow and overdue captions use natural days`() {
        val from = LocalDate.of(2026, 11, 25)

        val today = DayMath.display(event(from), from)
        assertEquals("0", today.number)
        assertEquals("就是今天", today.caption)
        assertEquals(Urgency.TODAY, today.urgency)

        val tomorrow = DayMath.display(event(from.plusDays(1)), from)
        assertEquals("1", tomorrow.number)
        assertEquals("就是明天", tomorrow.caption)
        assertFalse(tomorrow.isCountUp)

        val past = DayMath.display(event(from.minusDays(3)), from)
        assertEquals("3", past.number)
        assertEquals("已经 3 天", past.caption)
        assertEquals(Urgency.PAST, past.urgency)
        assertTrue(past.isCountUp)
    }

    @Test
    fun `past event can show a negative countdown`() {
        val from = LocalDate.of(2026, 11, 25)
        val display = DayMath.display(event(from.minusDays(2), countUpWhenPast = false), from)

        assertEquals("-2", display.number)
        assertEquals("已过 2 天", display.caption)
        assertTrue(display.isCountUp)
    }

    @Test
    fun `monthly repetition never drifts after February clamps day 31`() {
        val target = LocalDate.of(2026, 1, 31)
        val repeated = event(target, RepeatRule.MONTHLY)

        assertEquals(
            LocalDate.of(2026, 2, 28),
            DayMath.nextOccurrence(repeated, LocalDate.of(2026, 2, 1))
        )
        // 关键回归：不能从 2 月 28 日继续累加得到 3 月 28 日。
        assertEquals(
            LocalDate.of(2026, 3, 31),
            DayMath.nextOccurrence(repeated, LocalDate.of(2026, 3, 1))
        )
        assertEquals(
            LocalDate.of(2026, 4, 30),
            DayMath.nextOccurrence(repeated, LocalDate.of(2026, 4, 1))
        )
    }

    @Test
    fun `yearly repetition clamps leap day but restores it in leap years`() {
        val target = LocalDate.of(2024, 2, 29)
        val repeated = event(target, RepeatRule.YEARLY)

        assertEquals(
            LocalDate.of(2025, 2, 28),
            DayMath.nextOccurrence(repeated, LocalDate.of(2025, 1, 1))
        )
        assertEquals(
            LocalDate.of(2028, 2, 29),
            DayMath.nextOccurrence(repeated, LocalDate.of(2027, 3, 1))
        )
    }

    @Test
    fun `weekly repetition advances from original target`() {
        val target = LocalDate.of(2026, 11, 1)
        val repeated = event(target, RepeatRule.WEEKLY)

        assertEquals(
            LocalDate.of(2026, 11, 29),
            DayMath.nextOccurrence(repeated, LocalDate.of(2026, 11, 25))
        )
        assertEquals(4L, DayMath.daysFor(repeated, LocalDate.of(2026, 11, 25)))
    }

    @Test
    fun `urgency and todo badges have expected boundaries`() {
        assertEquals(Urgency.PAST, DayMath.urgencyOf(-1))
        assertEquals(Urgency.TODAY, DayMath.urgencyOf(0))
        assertEquals(Urgency.SOON, DayMath.urgencyOf(7))
        assertEquals(Urgency.LATER, DayMath.urgencyOf(8))

        val from = LocalDate.of(2026, 11, 25)
        assertNull(DayMath.badgeFor(null, from))
        assertEquals("今天", DayMath.badgeFor(from.toEpochDay(), from))
        assertEquals("明天", DayMath.badgeFor(from.plusDays(1).toEpochDay(), from))
        assertEquals("昨天", DayMath.badgeFor(from.minusDays(1).toEpochDay(), from))
        assertEquals("剩 9 天", DayMath.badgeFor(from.plusDays(9).toEpochDay(), from))
        assertEquals("超 2 天", DayMath.badgeFor(from.minusDays(2).toEpochDay(), from))
    }
}
