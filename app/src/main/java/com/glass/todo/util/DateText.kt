package com.glass.todo.util

import android.content.Context
import com.glass.todo.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 日期在「epochDay」与界面文案之间的换算。
 *
 * MaterialDatePicker 返回的是「所选日期的 UTC 零点」毫秒数，
 * 若直接按系统时区解析，UTC-5 等负时区会把它变成前一天；
 * 因此这里统一以 UTC 为基准做双向转换。
 */
object DateText {

    private val longFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")

    private val shortFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M 月 d 日")

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm")

    fun epochDayToUtcMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun utcMillisToEpochDay(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

    fun format(context: Context, epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        val today = LocalDate.now()
        return when {
            date.year == today.year -> shortFormatter.format(date)
            else -> longFormatter.format(date)
        }.let { text ->
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
            when (days) {
                0L -> context.getString(R.string.date_today)
                1L -> context.getString(R.string.date_tomorrow)
                -1L -> context.getString(R.string.date_yesterday)
                else -> text
            }
        }
    }

    /** 数据库里的分钟数统一限制在 00:00–23:59；null 保持关闭状态。 */
    fun normalizeMinuteOfDay(value: Int?): Int? = value?.coerceIn(0, 23 * 60 + 59)

    fun formatTime(minuteOfDay: Int): String {
        val minute = normalizeMinuteOfDay(minuteOfDay) ?: 0
        return timeFormatter.format(LocalTime.of(minute / 60, minute % 60))
    }

    /**
     * 详细 DDL 使用绝对日期，不再替换成“今天/明天”，避免看不到准确日期。
     * 组件空间较小时可传 [multiline] = true，把日期和时分分成两行。
     */
    fun formatExactDateTime(
        epochDay: Long,
        minuteOfDay: Int,
        from: LocalDate = LocalDate.now(),
        multiline: Boolean = false
    ): String {
        val date = LocalDate.ofEpochDay(epochDay)
        val dateText = if (date.year == from.year) {
            shortFormatter.format(date)
        } else {
            longFormatter.format(date)
        }
        val timeText = formatTime(minuteOfDay)
        return if (multiline) "$dateText\n$timeText" else "$dateText $timeText"
    }
}
