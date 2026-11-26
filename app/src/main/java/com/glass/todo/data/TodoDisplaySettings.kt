package com.glass.todo.data

/** 待办日期区域的显示内容。 */
enum class TodoDateDisplayMode {
    DDL,
    DAYS,
    BOTH
}

/** 待办已设置具体时间时，日期区域的时间精度。 */
enum class TodoTimePrecision {
    NONE,
    HOUR,
    MINUTE
}
