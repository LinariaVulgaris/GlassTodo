package com.glass.todo.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TodoSortingTest {

    private val today = LocalDate.of(2026, 11, 27)

    @Test
    fun `auto sort puts nearest overdue first then upcoming and undated last`() {
        val items = listOf(
            todo(1, due = null),
            todo(2, due = today.plusDays(3)),
            todo(3, due = today.minusDays(5)),
            todo(4, due = today.plusDays(1)),
            todo(5, due = today.minusDays(1))
        )

        val sorted = TodoSorting.sort(items, manual = false, today = today)

        assertEquals(listOf(5L, 3L, 4L, 2L, 1L), sorted.map { it.id })
    }

    @Test
    fun `auto sort uses deadline time within the same day and keeps done last`() {
        val items = listOf(
            todo(1, due = today, minute = null),
            todo(2, due = today, minute = 9 * 60),
            todo(3, due = today, minute = 18 * 60),
            todo(4, due = today.plusDays(1), done = true)
        )

        val sorted = TodoSorting.sort(items, manual = false, today = today)

        assertEquals(listOf(2L, 3L, 1L, 4L), sorted.map { it.id })
    }

    @Test
    fun `manual sort preserves the stored order`() {
        val items = listOf(
            todo(1, due = today.plusDays(10)),
            todo(2, due = null),
            todo(3, due = today.minusDays(2))
        )

        val sorted = TodoSorting.sort(items, manual = true, today = today)

        assertEquals(items, sorted)
    }

    private fun todo(
        id: Long,
        due: LocalDate?,
        minute: Int? = null,
        done: Boolean = false
    ) = TodoEntity(
        id = id,
        title = "todo-$id",
        dueEpochDay = due?.toEpochDay(),
        dueMinuteOfDay = minute,
        done = done,
        createdAt = id
    )
}
