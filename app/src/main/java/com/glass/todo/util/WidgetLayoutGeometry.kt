package com.glass.todo.util

import com.glass.todo.data.WidgetLayoutMode
import kotlin.math.max

/** 不依赖 Android 的组件布局计算，方便 JVM 单测覆盖比例和列数边界。 */
object WidgetLayoutGeometry {

    private const val WIDE_RATIO = 1.35f
    private const val TALL_RATIO = 0.75f
    private const val TWO_COLUMN_MIN_WIDTH = 320

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

    /** 待办在足够宽的横向或方形组件中使用两列，竖屏和窄组件保持单列。 */
    fun todoColumns(mode: WidgetLayoutMode, widthDp: Int): Int =
        if ((mode == WidgetLayoutMode.WIDE || mode == WidgetLayoutMode.SQUARE) &&
            widthDp >= TWO_COLUMN_MIN_WIDTH
        ) 2 else 1

    fun aspectRatio(widthDp: Int, heightDp: Int): Float =
        max(1, widthDp).toFloat() / max(1, heightDp).toFloat()
}
