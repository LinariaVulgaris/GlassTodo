package com.glass.todo.data

import java.time.LocalDate

object TodoSorting {

    fun sort(
        items: List<TodoEntity>,
        manual: Boolean,
        today: LocalDate
    ): List<TodoEntity> {
        if (manual) return items
        return items.sortedWith(
            compareBy<TodoEntity> { it.done }
                .thenBy { it.dueEpochDay == null }
                .thenBy { dueGroup(it.dueEpochDay, today) }
                .thenBy { dueOrder(it.dueEpochDay, today) }
                .thenBy { it.dueMinuteOfDay ?: END_OF_DAY_MINUTE }
                .thenBy { it.sortOrder }
                .thenByDescending { it.createdAt }
                .thenByDescending { it.id }
        )
    }

    private fun dueGroup(dueEpochDay: Long?, today: LocalDate): Int = when {
        dueEpochDay == null -> 2
        dueEpochDay < today.toEpochDay() -> 0
        else -> 1
    }

    private fun dueOrder(dueEpochDay: Long?, today: LocalDate): Long = when {
        dueEpochDay == null -> 0L
        dueEpochDay < today.toEpochDay() -> -dueEpochDay
        else -> dueEpochDay
    }

    private const val END_OF_DAY_MINUTE = 23 * 60 + 59
}
