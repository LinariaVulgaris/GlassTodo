package com.glass.todo.util

import com.glass.todo.data.WidgetLayoutMode
import kotlin.math.max

/** 不依赖 Android 的组件布局计算，方便 JVM 单测覆盖比例和列数边界。 */
object WidgetLayoutGeometry {

    private const val WIDE_RATIO = 1.35f
    private const val TALL_RATIO = 0.75f
    private const val COMFORT_LIST_MIN_WIDTH = 280

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

    /** 窄组件把日期元数据放到标题下方，宽组件保持原来的左右排列。 */
    fun todoLayout(mode: WidgetLayoutMode, widthDp: Int): TodoLayout =
        if (widthDp >= COMFORT_LIST_MIN_WIDTH) TodoLayout.COMFORT_LIST else TodoLayout.COMPACT_LIST

    /** 待办始终只有一列；保留该查询供旧布局预览代码兼容使用。 */
    fun todoColumns(mode: WidgetLayoutMode, widthDp: Int): Int =
        todoLayout(mode, widthDp).columns

    enum class TodoLayout(val columns: Int) {
        GRID(2),
        COMPACT_LIST(1),
        COMFORT_LIST(1)
    }

    fun aspectRatio(widthDp: Int, heightDp: Int): Float =
        max(1, widthDp).toFloat() / max(1, heightDp)

    /** 当前组件在屏幕上的实际像素数，用于在 Binder 预算内选择合适的背景分辨率。 */
    fun displayPixels(widthDp: Int, heightDp: Int, density: Float): Int {
        require(density.isFinite() && density > 0f)
        val width = max(1, widthDp).toLong()
        val height = max(1, heightDp).toLong()
        return (width * height * density.toDouble())            .coerceAtMost(Int.MAX_VALUE.toDouble())
            .toInt()
            .coerceAtLeast(1)
    }
}
