package com.glass.todo.util

import com.glass.todo.data.TodoDateDisplayMode
import com.glass.todo.data.TodoTimePrecision
import java.time.LocalDate

/**
 * App 列表、桌面组件和背景预览共用的待办日期文案。
 * 保持同一套规则，避免三个入口对 DDL、剩余天数和具体时间的理解不一致。
 */
object TodoDateText {

    data class Labels(
        val deadline: String?,
        val remaining: String?
    ) {
        val multiline: String?
            get() = listOfNotNull(deadline, remaining)
                .takeIf { it.isNotEmpty() }
                ?.joinToString("\n")
    }

    fun labels(
        dueEpochDay: Long?,
        dueMinuteOfDay: Int?,
        mode: TodoDateDisplayMode,
        timePrecision: TodoTimePrecision,
        from: LocalDate = DayMath.today(),
        compact: Boolean = false
    ): Labels {
        if (dueEpochDay == null) return Labels(null, null)
        val minute = dueMinuteOfDay?.let(DateText::normalizeMinuteOfDay)
        val deadline = if (mode != TodoDateDisplayMode.DAYS) {
            formatDeadline(dueEpochDay, minute, timePrecision, from, compact)
        } else {
            null
        }
        val remaining = if (mode != TodoDateDisplayMode.DDL) {
            DayMath.badgeFor(dueEpochDay, from)
        } else {
            null
        }
        return Labels(deadline, remaining)
    }

    private fun formatDeadline(
        dueEpochDay: Long,
        minuteOfDay: Int?,
        timePrecision: TodoTimePrecision,
        from: LocalDate,
        compact: Boolean
    ): String {
        val date = if (compact) {
            DateText.formatCompactDate(dueEpochDay, from)
        } else {
            DateText.formatExactDate(dueEpochDay, from)
        }
        val time = minuteOfDay?.let {
            DateText.formatTimeByPrecision(it, timePrecision, compact)
        }
        return if (time == null) date else "$date $time"
    }
}
