package com.glass.todo.util

import android.util.TypedValue
import android.widget.RemoteViews
import kotlin.math.max

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

}
