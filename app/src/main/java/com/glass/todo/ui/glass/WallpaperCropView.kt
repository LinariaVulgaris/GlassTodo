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
import com.glass.todo.util.GlassCropGeometry
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
    private var cropRect = RectF()
    private var crop = GlassCrop(0.12f, 0.12f, 0.88f, 0.88f)
    private var targetAspect = 1f
    private var lockAspect = true
    private var mode = GlassCropGeometry.TouchMode.NONE
    private var activePointerId = INVALID_POINTER
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private val startCrop = RectF()
    private var useDefaultCropWhenReady = false

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
        useDefaultCropWhenReady = false
        updateImageRect()
        invalidate()
    }

    fun setCrop(value: GlassCrop, notify: Boolean = false) {
        useDefaultCropWhenReady = false
        crop = value.normalized()
        updateCropRect()
        if (notify) onCropChanged?.invoke(crop)
        invalidate()
    }

    fun getCrop(): GlassCrop = crop

    fun setDefaultCrop(aspectRatio: Float) {
        targetAspect = aspectRatio.coerceAtLeast(0.1f)
        useDefaultCropWhenReady = true
        if (imageRect.isEmpty) return
        cropRect = GlassCropGeometry.defaultCrop(imageRect.toGeometry(), targetAspect).toRectF()
        useDefaultCropWhenReady = false
        publishCrop()
    }

    fun setAspectRatio(aspectRatio: Float, adjustCurrent: Boolean = false) {
        targetAspect = aspectRatio.coerceAtLeast(0.1f)
        if (adjustCurrent && lockAspect && !imageRect.isEmpty && !cropRect.isEmpty) {
            cropRect = GlassCropGeometry.adjustAspect(cropRect.toGeometry(), imageRect.toGeometry(), targetAspect)
                .toRectF()
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
        if (useDefaultCropWhenReady && !imageRect.isEmpty) {
            cropRect = GlassCropGeometry.defaultCrop(imageRect.toGeometry(), targetAspect).toRectF()
            useDefaultCropWhenReady = false
            publishCrop()
        } else {
            updateCropRect()
        }
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
        if (!isEnabled || image == null || imageRect.isEmpty) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val touchedMode = modeAt(event.x, event.y)
                if (touchedMode == GlassCropGeometry.TouchMode.NONE) return false
                mode = touchedMode
                activePointerId = event.getPointerId(0)
                gestureStartX = event.x
                gestureStartY = event.y
                startCrop.set(cropRect)
                // 必须从 DOWN 开始阻止外层 ScrollView 拦截，否则拖动会被页面滚动抢走。
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == GlassCropGeometry.TouchMode.NONE) return false
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return true
                val dx = event.getX(pointerIndex) - gestureStartX
                val dy = event.getY(pointerIndex) - gestureStartY
                cropRect = if (mode == GlassCropGeometry.TouchMode.MOVE) {
                    GlassCropGeometry.move(startCrop.toGeometry(), imageRect.toGeometry(), dx, dy)
                } else {
                    GlassCropGeometry.resize(
                        startCrop.toGeometry(),
                        imageRect.toGeometry(),
                        mode,
                        dx,
                        dy,
                        targetAspect,
                        lockAspect,
                        max(dp(40f), min(imageRect.width(), imageRect.height()) * 0.08f)
                    )
                }.toRectF()
                publishCrop()
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // 当前只处理单指校准；保留首个活动指针，避免第二根手指导致选框跳动。
                return mode != GlassCropGeometry.TouchMode.NONE
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    endGesture()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val wasDragging = mode != GlassCropGeometry.TouchMode.NONE
                endGesture()
                if (wasDragging) performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                endGesture()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun endGesture() {
        mode = GlassCropGeometry.TouchMode.NONE
        activePointerId = INVALID_POINTER
        parent?.requestDisallowInterceptTouchEvent(false)
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

    private fun modeAt(x: Float, y: Float): GlassCropGeometry.TouchMode =
        GlassCropGeometry.touchMode(x, y, cropRect.toGeometry(), dp(28f))

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

    private fun RectF.toGeometry() = GlassCropGeometry.Bounds(left, top, right, bottom)

    private fun GlassCropGeometry.Bounds.toRectF() = RectF(left, top, right, bottom)

    private companion object {
        const val INVALID_POINTER = -1
    }
}
