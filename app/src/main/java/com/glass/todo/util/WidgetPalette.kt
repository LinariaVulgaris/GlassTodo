package com.glass.todo.util

import android.content.Context
import com.glass.todo.R
import kotlin.math.roundToInt

/** 把一个颜色的 alpha 通道按比例调整，用于生成次要文字颜色 */
fun Int.withAlpha(fraction: Float): Int {
    val clamped = fraction.coerceIn(0f, 1f)
    val alpha = (((this ushr 24) and 0xFF) * clamped).roundToInt() and 0xFF
    return (alpha shl 24) or (this and 0x00FFFFFF)
}

/**
 * 组件配色。
 *
 * 因为组件是透明背景直接叠在壁纸上，颜色必须同时满足两个条件：
 * 在深色和浅色壁纸上都能看清，所以一律选明度较高的色；
 * 紧急程度用色相区分，让用户扫一眼就知道哪个更急。
 */
object WidgetPalette {

    fun colorFor(context: Context, urgency: Urgency, userTextColor: Int): Int = when (urgency) {
        Urgency.TODAY -> context.getColor(R.color.widget_accent_today)
        Urgency.SOON -> context.getColor(R.color.widget_accent_soon)
        Urgency.PAST -> context.getColor(R.color.widget_accent_past)
        // 不紧急的就用用户设定的颜色，默认白色
        Urgency.LATER -> userTextColor
    }
}
