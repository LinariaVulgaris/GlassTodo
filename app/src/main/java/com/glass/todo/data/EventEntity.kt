package com.glass.todo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 倒数日事件的重复规则。存成字符串便于日后扩展而不破坏数据库结构。 */
object RepeatRule {
    const val NONE = "none"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
    const val YEARLY = "yearly"

    val ALL = listOf(NONE, WEEKLY, MONTHLY, YEARLY)
}

/**
 * 倒数日事件（生日、纪念日、截止日等）。
 *
 * 参考了 CountAway 的思路：既支持「还有 N 天」（未来），也支持「已经 N 天」（过去），
 * 并可按周/月/年重复，重复时永远显示下一次到来的那一天。
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    /** 目标日期（epochDay） */
    val targetEpochDay: Long,
    /** 具体目标时间（当天第几分钟）；null 表示关闭详细 DDL，只显示剩余天数 */
    val targetMinuteOfDay: Int? = null,
    /** [RepeatRule] 之一 */
    val repeat: String = RepeatRule.NONE,
    /** 日期已过且无重复时，是否显示为正数「已经 N 天」 */
    val countUpWhenPast: Boolean = true,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
