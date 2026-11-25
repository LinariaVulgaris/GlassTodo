package com.glass.todo.util

import com.glass.todo.data.EventEntity
import com.glass.todo.data.RepeatRule
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** 紧急程度，决定组件上数字的配色 */
enum class Urgency { PAST, TODAY, SOON, LATER }

/** 组件大字展示用的文案 */
data class CountdownDisplay(
    /** 大数字，例如 "128" */
    val number: String,
    /** 数字下方的说明，例如 "还有 128 天" */
    val caption: String,
    val urgency: Urgency,
    /** true 表示这是「已经过去 N 天」 */
    val isCountUp: Boolean
)

/**
 * 所有「天数」计算的唯一出口。
 *
 * 统一以 [LocalDate] 的自然日为单位，不做任何小时/分钟换算，
 * 这样组件上显示的「还有 N 天」和用户直觉一致，也不会因时区偏移差一天。
 */
object DayMath {

    fun today(): LocalDate = LocalDate.now()

    fun urgencyOf(days: Long): Urgency = when {
        days < 0L -> Urgency.PAST
        days == 0L -> Urgency.TODAY
        days <= 7L -> Urgency.SOON
        else -> Urgency.LATER
    }

    /**
     * 计算事件下一次出现的日期。
     *
     * 重复推算始终以原始 target 为基准乘以步长，而不是在上一次结果上累加，
     * 否则「1 月 31 日按月重复」会因为 2 月被截断成 28 日而逐渐漂移。
     */
    fun nextOccurrence(event: EventEntity, from: LocalDate = today()): LocalDate {
        val target = LocalDate.ofEpochDay(event.targetEpochDay)
        if (event.repeat == RepeatRule.NONE) return target
        if (!target.isBefore(from)) return target

        val step: (LocalDate, Int) -> LocalDate = when (event.repeat) {
            RepeatRule.WEEKLY -> { base, k -> base.plusWeeks(k.toLong()) }
            RepeatRule.MONTHLY -> { base, k -> base.plusMonths(k.toLong()) }
            RepeatRule.YEARLY -> { base, k -> base.plusYears(k.toLong()) }
            else -> return target
        }

        // 上限保护：即使数据异常也不会死循环
        var k = 1
        var candidate = step(target, k)
        while (candidate.isBefore(from) && k < 20000) {
            k++
            candidate = step(target, k)
        }
        return candidate
    }

    /** 事件距离今天还有多少天（负数表示已过去） */
    fun daysFor(event: EventEntity, from: LocalDate = today()): Long {
        val next = nextOccurrence(event, from)
        return ChronoUnit.DAYS.between(from, next)
    }

    /** 待办的截止日距离今天还有多少天；无截止日返回 null */
    fun daysForDue(dueEpochDay: Long?, from: LocalDate = today()): Long? =
        dueEpochDay?.let { ChronoUnit.DAYS.between(from, LocalDate.ofEpochDay(it)) }

    /** 生成组件上的大字与说明文案 */
    fun display(event: EventEntity, from: LocalDate = today()): CountdownDisplay {
        val raw = daysFor(event, from)
        return when {
            raw == 0L -> CountdownDisplay("0", "就是今天", Urgency.TODAY, false)
            raw > 0L -> CountdownDisplay(
                number = raw.toString(),
                caption = if (raw == 1L) "就是明天" else "还有 $raw 天",
                urgency = urgencyOf(raw),
                isCountUp = false
            )
            else -> {
                val past = -raw
                if (event.countUpWhenPast) {
                    CountdownDisplay(
                        number = past.toString(),
                        caption = if (past == 1L) "已是昨天" else "已经 $past 天",
                        urgency = Urgency.PAST,
                        isCountUp = true
                    )
                } else {
                    CountdownDisplay(
                        number = raw.toString(),
                        caption = "已过 $past 天",
                        urgency = Urgency.PAST,
                        isCountUp = true
                    )
                }
            }
        }
    }

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

    /** 下一个触发刷新时刻：最近一个 00:05 */
    fun nextDailyTriggerMillis(): Long {
        val now = ZonedDateTime.now()
        var candidate = now.toLocalDate().atTime(0, 5).atZone(now.zone)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        return candidate.toInstant().toEpochMilli()
    }
}
