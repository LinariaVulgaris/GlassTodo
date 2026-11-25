package com.glass.todo.util

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 不依赖 Android 的截图选区计算，便于覆盖拖动、缩放和触摸命中边界。 */
object GlassCropGeometry {

    data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val isEmpty: Boolean get() = width <= 0f || height <= 0f
    }

    enum class TouchMode { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    /**
     * 四角命中范围不能大于选框短边，否则窄选框的整个内部都会被判成缩放。
     * 始终给中间区域保留至少 40% 的短边长度用于移动。
     */
    fun touchMode(x: Float, y: Float, crop: Bounds, hitRadius: Float): TouchMode {
        if (crop.isEmpty) return TouchMode.NONE
        val radius = max(0f, min(hitRadius, min(crop.width, crop.height) * 0.3f))
        val radiusSquared = radius * radius
        fun near(px: Float, py: Float): Boolean {
            val distanceX = x - px
            val distanceY = y - py
            return distanceX * distanceX + distanceY * distanceY <= radiusSquared
        }
        return when {
            near(crop.left, crop.top) -> TouchMode.TOP_LEFT
            near(crop.right, crop.top) -> TouchMode.TOP_RIGHT
            near(crop.left, crop.bottom) -> TouchMode.BOTTOM_LEFT
            near(crop.right, crop.bottom) -> TouchMode.BOTTOM_RIGHT
            x >= crop.left && x <= crop.right && y >= crop.top && y <= crop.bottom -> TouchMode.MOVE
            else -> TouchMode.NONE
        }
    }

    fun defaultCrop(image: Bounds, aspectRatio: Float): Bounds {
        if (image.isEmpty) return image
        val aspect = aspectRatio.coerceAtLeast(0.1f)
        val width = min(image.width * 0.72f, image.height * 0.78f * aspect)
        val height = width / aspect
        val centerX = image.left + image.width / 2f
        val centerY = image.top + image.height / 2f
        return Bounds(
            centerX - width / 2f,
            centerY - height / 2f,
            centerX + width / 2f,
            centerY + height / 2f
        )
    }

    /** dx/dy 是相对手势起点的总位移，避免丢事件或指针切换造成累积漂移。 */
    fun move(start: Bounds, image: Bounds, dx: Float, dy: Float): Bounds {
        if (start.isEmpty || image.isEmpty) return start
        val width = min(start.width, image.width)
        val height = min(start.height, image.height)
        val left = (start.left + dx).coerceIn(image.left, image.right - width)
        val top = (start.top + dy).coerceIn(image.top, image.bottom - height)
        return Bounds(left, top, left + width, top + height)
    }

    private fun constrainPosition(crop: Bounds, image: Bounds): Bounds {
        val left = crop.left.coerceIn(image.left, image.right - crop.width)
        val top = crop.top.coerceIn(image.top, image.bottom - crop.height)
        return Bounds(left, top, left + crop.width, top + crop.height)
    }

    fun resize(
        start: Bounds,
        image: Bounds,
        corner: TouchMode,
        dx: Float,
        dy: Float,
        aspectRatio: Float,
        lockAspect: Boolean,
        minSide: Float
    ): Bounds {
        if (start.isEmpty || image.isEmpty || corner == TouchMode.NONE || corner == TouchMode.MOVE) {
            return start
        }
        if (!lockAspect) return resizeFree(start, image, corner, dx, dy, minSide)

        val leftCorner = corner == TouchMode.TOP_LEFT || corner == TouchMode.BOTTOM_LEFT
        val topCorner = corner == TouchMode.TOP_LEFT || corner == TouchMode.TOP_RIGHT
        val anchorX = if (leftCorner) start.right else start.left
        val anchorY = if (topCorner) start.bottom else start.top
        val aspect = aspectRatio.coerceAtLeast(0.1f)
        val widthFromDx = start.width + if (leftCorner) -dx else dx
        val heightFromDy = start.height + if (topCorner) -dy else dy
        val oldWidth = start.width.coerceAtLeast(1f)
        val oldHeight = start.height.coerceAtLeast(1f)
        val useWidth = abs(widthFromDx - start.width) / oldWidth >=
            abs(heightFromDy - start.height) / oldHeight
        val desiredWidth = if (useWidth) widthFromDx else heightFromDy * aspect
        val maxWidthByX = if (leftCorner) anchorX - image.left else image.right - anchorX
        val maxHeightByY = if (topCorner) anchorY - image.top else image.bottom - anchorY
        val maxWidth = max(1f, min(maxWidthByX, maxHeightByY * aspect))
        val minWidth = min(maxWidth, max(minSide, minSide * aspect))
        val width = desiredWidth.coerceIn(minWidth, maxWidth)
        val height = width / aspect
        val left = if (leftCorner) anchorX - width else anchorX
        val top = if (topCorner) anchorY - height else anchorY
        return Bounds(left, top, left + width, top + height)
    }

    fun adjustAspect(start: Bounds, image: Bounds, aspectRatio: Float): Bounds {
        if (start.isEmpty || image.isEmpty) return start
        val aspect = aspectRatio.coerceAtLeast(0.1f)
        val width = min(min(start.width, start.height * aspect), min(image.width, image.height * aspect))
        val height = width / aspect
        val centerX = start.left + start.width / 2f
        val centerY = start.top + start.height / 2f
        val resized = Bounds(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f)
        return constrainPosition(resized, image)
    }

    private fun resizeFree(
        start: Bounds,
        image: Bounds,
        corner: TouchMode,
        dx: Float,
        dy: Float,
        minSide: Float
    ): Bounds {
        val leftCorner = corner == TouchMode.TOP_LEFT || corner == TouchMode.BOTTOM_LEFT
        val topCorner = corner == TouchMode.TOP_LEFT || corner == TouchMode.TOP_RIGHT
        val minimum = minSide.coerceAtMost(min(start.width, start.height))
        val left = if (leftCorner) {
            (start.left + dx).coerceIn(image.left, start.right - minimum)
        } else {
            start.left
        }
        val right = if (leftCorner) {
            start.right
        } else {
            (start.right + dx).coerceIn(start.left + minimum, image.right)
        }
        val top = if (topCorner) {
            (start.top + dy).coerceIn(image.top, start.bottom - minimum)
        } else {
            start.top
        }
        val bottom = if (topCorner) {
            start.bottom
        } else {
            (start.bottom + dy).coerceIn(start.top + minimum, image.bottom)
        }
        return Bounds(left, top, right, bottom)
    }
}
