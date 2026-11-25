package com.glass.todo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 待办事项。
 *
 * 日期一律以 `LocalDate.toEpochDay()` 的形式存成 Long，避免引入 Room TypeConverter，
 * 也避免时区/毫秒带来的「差一天」问题。minSdk 31 自带 java.time，无需 desugaring。
 */
@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val note: String = "",
    /** 截止日（epochDay）；null 表示无截止日，不参与倒数 */
    val dueEpochDay: Long? = null,
    /** 具体截止时间（当天第几分钟）；null 表示关闭详细 DDL，只显示剩余天数 */
    val dueMinuteOfDay: Int? = null,
    val done: Boolean = false,
    /** 手动排序权重，越小越靠前 */
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
