package com.glass.todo.util

import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * 所有「天数」计算的唯一出口。
 *
 * 统一以 [LocalDate] 的自然日为单位，不做任何小时/分钟换算，
 * 这样待办上显示的「还有 N 天」和用户直觉一致，也不会因时区偏移差一天。
 */
object DayMath {

    fun today(): LocalDate = LocalDate.now()

    fun isUrgent(days: Long, thresholdDays: Int): Boolean = days <= thresholdDays

    /** 待办的截止日距离今天还有多少天；无截止日返回 null */
    fun daysForDue(dueEpochDay: Long?, from: LocalDate = today()): Long? =
        dueEpochDay?.let { ChronoUnit.DAYS.between(from, LocalDate.ofEpochDay(it)) }

    /** 待办右侧的小徽标文案 */
    fun badgeFor(dueEpochDay: Long?, from: LocalDate = today()): String? {
        val days = daysForDue(dueEpochDay, from) ?: return null
        return when {
            days == 0L -> "今天"
            days == 1L -> "明天"
            days == -1L -> "昨天"
            days > 0L -> "剩 $days 天"
            else -> "超 ${-days} 天"
        }
    }

    /** 下一个本地 00:05；按本地日期逐日计算，跨夏令时也不会固定叠加 24 小时。 */
    fun nextDailyTriggerMillis(now: ZonedDateTime = ZonedDateTime.now()): Long {
        var candidate = now.toLocalDate().atTime(0, 5).atZone(now.zone)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        return candidate.toInstant().toEpochMilli()
    }
}
