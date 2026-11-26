package com.glass.todo.util

import com.glass.todo.data.TodoDateDisplayMode
import com.glass.todo.data.TodoTimePrecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TodoDateTextTest {

    private val today = LocalDate.of(2026, 11, 25)
    private val dueDay = LocalDate.of(2026, 11, 28).toEpochDay()

    @Test
    fun `ddl mode shows absolute date without remaining days`() {
        val labels = TodoDateText.labels(
            dueDay, 485, TodoDateDisplayMode.DDL, TodoTimePrecision.MINUTE, today
        )

        assertEquals("11 月 28 日 08:05", labels.deadline)
        assertNull(labels.remaining)
    }

    @Test
    fun `days mode shows remaining days and optional time`() {
        val labels = TodoDateText.labels(
            dueDay, 485, TodoDateDisplayMode.DAYS, TodoTimePrecision.MINUTE, today
        )

        assertNull(labels.deadline)
        assertEquals("剩 3 天 · 08:05", labels.remaining)
    }

    @Test
    fun `both mode shows deadline and remaining days`() {
        val labels = TodoDateText.labels(
            dueDay, 485, TodoDateDisplayMode.BOTH, TodoTimePrecision.HOUR, today
        )

        assertEquals("11 月 28 日 8 时", labels.deadline)
        assertEquals("剩 3 天", labels.remaining)
    }

    @Test
    fun `time can be hidden and date only todo never invents a time`() {
        val hidden = TodoDateText.labels(
            dueDay, 485, TodoDateDisplayMode.DDL, TodoTimePrecision.NONE, today
        )
        val dateOnly = TodoDateText.labels(
            dueDay, null, TodoDateDisplayMode.BOTH, TodoTimePrecision.MINUTE, today
        )

        assertEquals("11 月 28 日", hidden.deadline)
        assertEquals("11 月 28 日", dateOnly.deadline)
        assertEquals("剩 3 天", dateOnly.remaining)
    }

    @Test
    fun `compact widget text keeps date time and remaining days`() {
        val labels = TodoDateText.labels(
            LocalDate.of(2027, 1, 2).toEpochDay(),
            485,
            TodoDateDisplayMode.BOTH,
            TodoTimePrecision.MINUTE,
            today,
            compact = true
        )

        assertEquals("27/1/2 08:05", labels.deadline)
        assertEquals("剩 38 天", labels.remaining)
        assertEquals("27/1/2 08:05\n剩 38 天", labels.multiline)
    }

    @Test
    fun `todo without deadline hides both labels`() {
        val labels = TodoDateText.labels(
            null, null, TodoDateDisplayMode.BOTH, TodoTimePrecision.MINUTE, today
        )

        assertNull(labels.deadline)
        assertNull(labels.remaining)
    }
}
