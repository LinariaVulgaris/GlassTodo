package com.glass.todo.ui.layout

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.glass.todo.data.WidgetLayoutMode
import com.glass.todo.util.WidgetLayoutGeometry
import com.glass.todo.widget.WidgetTarget
import kotlin.math.max
import kotlin.math.min

/** 尺寸与形状设置页的示意图，按桌面当前宽高比例绘制组件内容。 */
class WidgetLayoutPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var target: WidgetTarget? = null
    private var mode = WidgetLayoutMode.AUTO
    private val cardRect = RectF()

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 30, 34, 48)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
    }
    fun bind(target: WidgetTarget?, mode: WidgetLayoutMode) {
        this.target = target
        this.mode = mode
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val item = target ?: return
        val padding = dp(14f)
        val availableWidth = max(1f, width - padding * 2f)
        val availableHeight = max(1f, height - padding * 2f)
        val ratio = WidgetLayoutGeometry.aspectRatio(item.widthDp, item.heightDp)
        val cardWidth = min(availableWidth, availableHeight * ratio)
        val cardHeight = cardWidth / ratio
        val left = (width - cardWidth) / 2f
        val top = (height - cardHeight) / 2f
        cardRect.set(left, top, left + cardWidth, top + cardHeight)

        canvas.drawRoundRect(cardRect, dp(16f), dp(16f), cardPaint)
        canvas.drawRoundRect(cardRect, dp(16f), dp(16f), borderPaint)
        drawTodo(canvas)
    }

    private fun drawTodo(canvas: Canvas) {
        val todoLayout = WidgetLayoutGeometry.todoLayout(mode, target?.widthDp ?: 0)
        val columns = todoLayout.columns
        val rows = if (todoLayout == WidgetLayoutGeometry.TodoLayout.COMFORT_LIST) 3 else 4
        val itemTextSize = when (todoLayout) {
            WidgetLayoutGeometry.TodoLayout.GRID -> 10f
            WidgetLayoutGeometry.TodoLayout.COMPACT_LIST -> 11f
            WidgetLayoutGeometry.TodoLayout.COMFORT_LIST -> 13f
        }
        val innerLeft = cardRect.left + dp(12f)
        val innerTop = cardRect.top + dp(14f)
        val innerWidth = cardRect.width() - dp(24f)
        val columnWidth = innerWidth / columns
        val rowHeight = (cardRect.height() - dp(30f)) / rows
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = dp(12f)
        canvas.drawText("待办", innerLeft, innerTop, textPaint)
        mutedPaint.textAlign = Paint.Align.RIGHT
        mutedPaint.textSize = dp(10f)
        canvas.drawText("3 项", cardRect.right - dp(12f), innerTop, mutedPaint)

        for (index in 0 until rows * columns) {
            val column = index % columns
            val row = index / columns
            val x = innerLeft + column * columnWidth
            val y = innerTop + dp(13f) + row * rowHeight
            canvas.drawCircle(x + dp(6f), y + dp(5f), dp(5f), borderPaint)
            mutedPaint.textAlign = Paint.Align.LEFT
            mutedPaint.textSize = dp(itemTextSize)
            val showDeadline = columns == 1 && index == 0
            val deadline = "11 月 9 日 08:05"
            val deadlineWidth = if (showDeadline) mutedPaint.measureText(deadline) + dp(8f) else 0f
            val title = if (index == 0) "完成示例" else "待办事项"
            canvas.save()
            canvas.clipRect(
                x + dp(17f),
                y - dp(itemTextSize),
                max(x + dp(17f), x + columnWidth - deadlineWidth),
                y + dp(4f)
            )
            canvas.drawText(title, x + dp(17f), y + dp(5f), mutedPaint)
            canvas.restore()
            if (showDeadline) {
                mutedPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(deadline, x + columnWidth, y + dp(5f), mutedPaint)
            }
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
