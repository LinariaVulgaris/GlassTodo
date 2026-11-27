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

/** 日期只使用用户设定的正常文字色或红色。 */
object WidgetPalette {

    fun colorFor(context: Context, urgent: Boolean, userTextColor: Int): Int =
        if (urgent) context.getColor(R.color.todo_date_urgent) else userTextColor
}
