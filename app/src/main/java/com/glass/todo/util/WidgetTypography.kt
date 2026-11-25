package com.glass.todo.util

import android.util.TypedValue
import android.widget.RemoteViews
import com.glass.todo.data.WidgetLayoutMode
import kotlin.math.max
import kotlin.math.min

/** 桌面组件文字的统一字号计算；纯计算部分可直接在 JVM 单测中覆盖。 */
object WidgetTypography {

    const val MIN_SCALE_PERCENT = 80
    const val MAX_SCALE_PERCENT = 140
    const val DEFAULT_SCALE_PERCENT = 100

    fun scale(percent: Int): Float =
        percent.coerceIn(MIN_SCALE_PERCENT, MAX_SCALE_PERCENT) / 100f

    fun scaledSize(baseSp: Float, percent: Int): Float =
        max(1f, baseSp) * scale(percent)

    /** 普通 TextView 直接按原始设计字号缩放，保留各层级的字号差异。 */
    fun apply(views: RemoteViews, percent: Int, vararg items: Pair<Int, Float>) {
        items.forEach { (viewId, baseSp) ->
            views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, scaledSize(baseSp, percent))
        }
    }

    /**
     * 倒数日大数字需要同时兼顾组件宽高、数字位数和用户倍率。
     * 空间不足时仍优先保证完整显示，不会为了倍率把数字裁掉。
     */
    fun countdownNumberSize(
        number: String,
        mode: WidgetLayoutMode,
        widthDp: Int,
        heightDp: Int,
        percent: Int
    ): Float {
        val wide = mode == WidgetLayoutMode.WIDE
        val width = max(1, widthDp).toFloat()
        val height = max(1, heightDp).toFloat()
        val digits = number.length.coerceAtLeast(1)
        val designMax = if (wide) 56f else 62f
        // 宽屏左侧还要放标题；数字区域约占组件宽度的一半。
        val availableWidth = if (wide) width * 0.5f else width
        // 为「还有 / 天」预留空间；字号倍率增大时，单位文字也会一起变大。
        val sideTextWidth = 39f * scale(percent) + 10f
        val widthLimit = max(8f, (availableWidth - 20f - sideTextWidth) / (digits * 0.62f))
        val heightLimit = max(16f, height * if (wide) 0.48f else 0.38f)
        val fitted = min(designMax, min(widthLimit, heightLimit))
        val scaled = fitted * scale(percent)
        val hardMax = min(designMax * 1.4f, min(widthLimit, height * if (wide) 0.62f else 0.55f))
        return scaled.coerceIn(8f, max(8f, hardMax))
    }
}
