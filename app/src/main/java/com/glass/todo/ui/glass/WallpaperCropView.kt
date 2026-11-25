package com.glass.todo.ui.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.glass.todo.data.GlassCrop
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 桌面截图校准视图：图片完整可见，选框可移动，四角可拖动缩放。 */
class WallpaperCropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var image: Bitmap? = null
    private val imageRect = RectF()
    private val cropRect = RectF()
    private var crop = GlassCrop(0.12f, 0.12f, 0.88f, 0.88f)
    private var targetAspect = 1f
    private var lockAspect = true
    private var mode = Mode.NONE
    private var lastX = 0f
    private var lastY = 0f
    private val startCrop = RectF()

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 30, 34, 48)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }

    var onCropChanged: ((GlassCrop) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
    }

    fun setBitmap(bitmap: Bitmap?) {
        image = bitmap
        updateImageRect()
        invalidate()
    }

    fun setCrop(value: GlassCrop, notify: Boolean = false) {
        crop = value.normalized()
        updateCropRect()
        if (notify) onCropChanged?.invoke(crop)
        invalidate()
    }

    fun getCrop(): GlassCrop = crop

    fun setDefaultCrop(aspectRatio: Float) {
        targetAspect = aspectRatio.coerceAtLeast(0.1f)
        if (imageRect.isEmpty || targetAspect <= 0f) return
        var width = imageRect.width() * 0.72f
        var height = width / targetAspect
        if (height > imageRect.height() * 0.78f) {
            height = imageRect.height() * 0.78f
            width = height * targetAspect
        }
        val centerX = imageRect.centerX()
        val centerY = imageRect.centerY()
        cropRect.set(
            centerX - width / 2f,
            centerY - height / 2f,
            centerX + width / 2f,
            centerY + height / 2f
        )
        publishCrop()
    }

    fun setAspectRatio(aspectRatio: Float, adjustCurrent: Boolean = false) {
        targetAspect = aspectRatio.coerceAtLeast(0.1f)
        if (adjustCurrent && lockAspect && !imageRect.isEmpty) {
            val centerX = cropRect.centerX()
            val centerY = cropRect.centerY()
            val width = min(cropRect.width(), cropRect.height() * targetAspect)
            val height = width / targetAspect
            cropRect.set(
                centerX - width / 2f,
                centerY - height / 2f,
                centerX + width / 2f,
                centerY + height / 2f
            )
            constrainCrop()
            publishCrop()
        }
        invalidate()
    }

    fun setLockAspect(locked: Boolean) {
        lockAspect = locked
        if (locked) setAspectRatio(targetAspect, adjustCurrent = true)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateImageRect()
        updateCropRect()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = image ?: return
        if (imageRect.isEmpty || cropRect.isEmpty) return

        canvas.drawBitmap(bitmap, null, imageRect, bitmapPaint)
        canvas.save()
        canvas.clipRect(imageRect)
        canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, cropRect.top, scrimPaint)
        canvas.drawRect(imageRect.left, cropRect.bottom, imageRect.right, imageRect.bottom, scrimPaint)
        canvas.drawRect(imageRect.left, cropRect.top, cropRect.left, cropRect.bottom, scrimPaint)
        canvas.drawRect(cropRect.right, cropRect.top, imageRect.right, cropRect.bottom, scrimPaint)
        canvas.restore()

        canvas.drawRoundRect(cropRect, dp(7f), dp(7f), borderPaint)
        canvas.drawLine(
            cropRect.left + cropRect.width() / 3f,
            cropRect.top,
            cropRect.left + cropRect.width() / 3f,
            cropRect.bottom,
            gridPaint
        )
        canvas.drawLine(
            cropRect.left + cropRect.width() * 2f / 3f,
            cropRect.top,
            cropRect.left + cropRect.width() * 2f / 3f,
            cropRect.bottom,
            gridPaint
        )
        canvas.drawLine(
            cropRect.left,
            cropRect.top + cropRect.height() / 3f,
            cropRect.right,
            cropRect.top + cropRect.height() / 3f,
            gridPaint
        )
        canvas.drawLine(
            cropRect.left,
            cropRect.top + cropRect.height() * 2f / 3f,
            cropRect.right,
            cropRect.top + cropRect.height() * 2f / 3f,
            gridPaint
        )

        drawHandle(canvas, cropRect.left, cropRect.top)
        drawHandle(canvas, cropRect.right, cropRect.top)
        drawHandle(canvas, cropRect.left, cropRect.bottom)
        drawHandle(canvas, cropRect.right, cropRect.bottom)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (image == null || imageRect.isEmpty) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode = modeAt(event.x, event.y)
                if (mode == Mode.NONE) return false
                lastX = event.x
                lastY = event.y
                startCrop.set(cropRect)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.NONE) return false
                val dx = event.x - lastX
                val dy = event.y - lastY
                lastX = event.x
                lastY = event.y
                if (mode == Mode.MOVE) moveCrop(dx, dy) else resizeCrop(mode, dx, dy)
                publishCrop()
                return true
            }

            MotionEvent.ACTION_UP -> {
                mode = Mode.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                mode = Mode.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateImageRect() {
        val bitmap = image ?: run {
            imageRect.setEmpty()
            cropRect.setEmpty()
            return
        }
        if (width <= 0 || height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) return
        val padding = dp(10f)
        val availableWidth = width - padding * 2f
        val availableHeight = height - padding * 2f
        val scale = min(availableWidth / bitmap.width, availableHeight / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        imageRect.set(
            (width - drawWidth) / 2f,
            (height - drawHeight) / 2f,
            (width + drawWidth) / 2f,
            (height + drawHeight) / 2f
        )
        updateCropRect()
    }

    private fun updateCropRect() {
        if (imageRect.isEmpty) return
        val safe = crop.normalized()
        cropRect.set(
            imageRect.left + safe.left * imageRect.width(),
            imageRect.top + safe.top * imageRect.height(),
            imageRect.left + safe.right * imageRect.width(),
            imageRect.top + safe.bottom * imageRect.height()
        )
    }

    private fun moveCrop(dx: Float, dy: Float) {
        val width = cropRect.width()
        val height = cropRect.height()
        val left = (startCrop.left + dx).coerceIn(imageRect.left, imageRect.right - width)
        val top = (startCrop.top + dy).coerceIn(imageRect.top, imageRect.bottom - height)
        cropRect.set(left, top, left + width, top + height)
    }

    private fun resizeCrop(corner: Mode, dx: Float, dy: Float) {
        val minSide = max(dp(40f), min(imageRect.width(), imageRect.height()) * 0.08f)
        if (!lockAspect) {
            if (corner == Mode.TOP_LEFT || corner == Mode.BOTTOM_LEFT) {
                cropRect.left = (startCrop.left + dx)
                    .coerceIn(imageRect.left, cropRect.right - minSide)
            } else {
                cropRect.right = (startCrop.right + dx)
                    .coerceIn(cropRect.left + minSide, imageRect.right)
            }
            if (corner == Mode.TOP_LEFT || corner == Mode.TOP_RIGHT) {
                cropRect.top = (startCrop.top + dy)
                    .coerceIn(imageRect.top, cropRect.bottom - minSide)
            } else {
                cropRect.bottom = (startCrop.bottom + dy)
                    .coerceIn(cropRect.top + minSide, imageRect.bottom)
            }
            return
        }

        val anchorX = if (corner == Mode.TOP_LEFT || corner == Mode.BOTTOM_LEFT) {
            startCrop.right
        } else {
            startCrop.left
        }
        val anchorY = if (corner == Mode.TOP_LEFT || corner == Mode.TOP_RIGHT) {
            startCrop.bottom
        } else {
            startCrop.top
        }
        val widthFromDx = startCrop.width() + if (corner == Mode.TOP_RIGHT || corner == Mode.BOTTOM_RIGHT) dx else -dx
        val heightFromDy = startCrop.height() + if (corner == Mode.BOTTOM_LEFT || corner == Mode.BOTTOM_RIGHT) dy else -dy
        val useWidth = abs(widthFromDx - startCrop.width()) / startCrop.width() >=
            abs(heightFromDy - startCrop.height()) / startCrop.height()
        val desiredWidth = if (useWidth) widthFromDx else heightFromDy * targetAspect
        val maxWidthByX = if (corner == Mode.TOP_LEFT || corner == Mode.BOTTOM_LEFT) {
            anchorX - imageRect.left
        } else {
            imageRect.right - anchorX
        }
        val maxHeightByY = if (corner == Mode.TOP_LEFT || corner == Mode.TOP_RIGHT) {
            anchorY - imageRect.top
        } else {
            imageRect.bottom - anchorY
        }
        val maxWidth = min(maxWidthByX, maxHeightByY * targetAspect).coerceAtLeast(1f)
        val minWidth = max(minSide, minSide * targetAspect).coerceAtMost(maxWidth)
        val width = desiredWidth.coerceIn(minWidth, maxWidth)
        val height = width / targetAspect
        val left = if (corner == Mode.TOP_LEFT || corner == Mode.BOTTOM_LEFT) anchorX - width else anchorX
        val top = if (corner == Mode.TOP_LEFT || corner == Mode.TOP_RIGHT) anchorY - height else anchorY
        cropRect.set(left, top, left + width, top + height)
    }

    private fun constrainCrop() {
        if (imageRect.isEmpty) return
        val width = cropRect.width().coerceAtMost(imageRect.width())
        val height = cropRect.height().coerceAtMost(imageRect.height())
        val left = cropRect.left.coerceIn(imageRect.left, imageRect.right - width)
        val top = cropRect.top.coerceIn(imageRect.top, imageRect.bottom - height)
        cropRect.set(left, top, left + width, top + height)
    }

    private fun modeAt(x: Float, y: Float): Mode {
        val radius = dp(28f)
        return when {
            hypot(x - cropRect.left, y - cropRect.top) <= radius -> Mode.TOP_LEFT
            hypot(x - cropRect.right, y - cropRect.top) <= radius -> Mode.TOP_RIGHT
            hypot(x - cropRect.left, y - cropRect.bottom) <= radius -> Mode.BOTTOM_LEFT
            hypot(x - cropRect.right, y - cropRect.bottom) <= radius -> Mode.BOTTOM_RIGHT
            cropRect.contains(x, y) -> Mode.MOVE
            else -> Mode.NONE
        }
    }

    private fun publishCrop() {
        if (imageRect.isEmpty || cropRect.isEmpty) return
        crop = GlassCrop(
            ((cropRect.left - imageRect.left) / imageRect.width()).coerceIn(0f, 1f),
            ((cropRect.top - imageRect.top) / imageRect.height()).coerceIn(0f, 1f),
            ((cropRect.right - imageRect.left) / imageRect.width()).coerceIn(0f, 1f),
            ((cropRect.bottom - imageRect.top) / imageRect.height()).coerceIn(0f, 1f)
        ).normalized()
        onCropChanged?.invoke(crop)
        invalidate()
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        val radius = dp(6f)
        canvas.drawCircle(x, y, radius, handleFillPaint)
        canvas.drawCircle(x, y, radius, handleStrokePaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private enum class Mode { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private fun hypot(x: Float, y: Float): Float = kotlin.math.hypot(x, y)
}
