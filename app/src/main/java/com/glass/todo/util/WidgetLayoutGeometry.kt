package com.glass.todo.util

import com.glass.todo.data.WidgetLayoutMode
import kotlin.math.max

/** 不依赖 Android 的组件布局计算，方便 JVM 单测覆盖比例和列数边界。 */
object WidgetLayoutGeometry {

    private const val WIDE_RATIO = 1.35f
    private const val TALL_RATIO = 0.75f
    private const val GRID_MIN_WIDTH = 240
    private const val SQUARE_GRID_MIN_WIDTH = 420

    /** 用户显式选择时始终尊重预设；自动模式才按当前桌面比例分类。 */
    fun resolve(
        widthDp: Int,
        heightDp: Int,
        preferred: WidgetLayoutMode
    ): WidgetLayoutMode {
        if (preferred != WidgetLayoutMode.AUTO) return preferred
        val width = widthDp.coerceAtLeast(1)
        val height = heightDp.coerceAtLeast(1)
        val ratio = width.toFloat() / height.toFloat()
        return when {
            ratio >= WIDE_RATIO -> WidgetLayoutMode.WIDE
            ratio <= TALL_RATIO -> WidgetLayoutMode.TALL
            else -> WidgetLayoutMode.SQUARE
        }
    }

    /**
     * 待办布局不只由列数决定：窄组件下竖向与方形也使用不同的行字号和密度，
     * 避免四个预设最终都渲染成同一种单列列表。
     */
    fun todoLayout(mode: WidgetLayoutMode, widthDp: Int): TodoLayout {
        val width = widthDp.coerceAtLeast(1)
        return when (mode) {
            WidgetLayoutMode.WIDE -> if (width >= GRID_MIN_WIDTH) TodoLayout.GRID else TodoLayout.COMPACT_LIST
            WidgetLayoutMode.SQUARE -> if (width >= SQUARE_GRID_MIN_WIDTH) TodoLayout.GRID else TodoLayout.COMPACT_LIST
            WidgetLayoutMode.TALL -> TodoLayout.COMFORT_LIST
            // AUTO 必须先由调用方结合桌面宽高解析，无法仅凭宽度判断时采用紧凑单列。
            WidgetLayoutMode.AUTO -> TodoLayout.COMPACT_LIST
        }
    }

    /** 保留列数查询，布局设置页和组件 Provider 共用同一套判定。 */
    fun todoColumns(mode: WidgetLayoutMode, widthDp: Int): Int =
        todoLayout(mode, widthDp).columns

    enum class TodoLayout(val columns: Int) {
        GRID(2),
        COMPACT_LIST(1),
        COMFORT_LIST(1)
    }

    fun aspectRatio(widthDp: Int, heightDp: Int): Float =
        max(1, widthDp).toFloat() / max(1, heightDp).toFloat()
}
