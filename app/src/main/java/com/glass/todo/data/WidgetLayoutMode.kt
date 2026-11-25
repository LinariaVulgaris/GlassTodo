package com.glass.todo.data

/** 组件内部排版预设；实际桌面占格仍由桌面系统控制。 */
enum class WidgetLayoutMode {
    /** 根据桌面报告的当前宽高自动选择横向、竖向或方形布局。 */
    AUTO,

    /** 强制宽屏排版。 */
    WIDE,

    /** 强制竖屏排版。 */
    TALL,

    /** 强制接近正方形的排版。 */
    SQUARE
}
