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
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 180, 220, 255)
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
        if (item.kind == WidgetTarget.Kind.COUNTDOWN) drawCountdown(canvas) else drawTodo(canvas)
    }

    private fun drawCountdown(canvas: Canvas) {
        val titleSize = max(11f, min(16f, cardRect.width() / 18f))
        val numberSize = max(24f, min(58f, cardRect.height() * 0.36f))
        textPaint.textSize = dp(titleSize)
        mutedPaint.textSize = dp(max(9f, titleSize - 3f))
        val numberBaseline = cardRect.centerY() + numberSize * 0.35f

        if (mode == WidgetLayoutMode.WIDE && cardRect.width() > cardRect.height() * 1.15f) {
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("倒数日", cardRect.left + dp(14f), cardRect.top + dp(26f), textPaint)
            drawCountdownNumber(
                canvas,
                cardRect.centerX(),
                cardRect.right - dp(14f),
                numberBaseline,
                numberSize
            )
        } else {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("倒数日", cardRect.centerX(), cardRect.top + dp(25f), textPaint)
            drawCountdownNumber(
                canvas,
                cardRect.left + dp(14f),
                cardRect.right - dp(14f),
                numberBaseline,
                numberSize
            )
        }
    }

    /** 与桌面组件一致：小单位在数字左右，数字保持视觉中心。 */
    private fun drawCountdownNumber(
        canvas: Canvas,
        left: Float,
        right: Float,
        baseline: Float,
        numberSize: Float
    ) {
        val prefix = "还有"
        val suffix = "天"
        val number = "128"
        val gap = dp(6f)
        val prefixWidth = mutedPaint.measureText(prefix)
        accentPaint.textSize = dp(numberSize)
        val numberWidth = accentPaint.measureText(number)
        val suffixWidth = mutedPaint.measureText(suffix)
        val groupWidth = prefixWidth + gap + numberWidth + gap + suffixWidth
        val start = max(left, (left + right - groupWidth) / 2f)

        mutedPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(prefix, start, baseline, mutedPaint)
        accentPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(number, start + prefixWidth + gap + numberWidth / 2f, baseline, accentPaint)
        mutedPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(suffix, start + groupWidth, baseline, mutedPaint)
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
