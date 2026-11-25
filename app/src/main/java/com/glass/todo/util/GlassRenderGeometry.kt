package com.glass.todo.util

import com.glass.todo.data.PixelCrop
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 不依赖 Android 的渲染尺寸计算，方便 JVM 单测覆盖 Binder 位图大小边界。 */
object GlassRenderGeometry {

    /** RemoteViews 事务还要容纳待办行，120k 像素约 480KB，给其余数据留出安全余量。 */
    const val MAX_OUTPUT_PIXELS = 120_000
    const val MAX_OUTPUT_SIDE = 480

    data class Size(val width: Int, val height: Int) {
        val pixels: Int get() = width * height
    }

    fun outputSize(
        crop: PixelCrop,
        maxPixels: Int = MAX_OUTPUT_PIXELS,
        maxSide: Int = MAX_OUTPUT_SIDE
    ): Size {
        require(maxPixels > 0 && maxSide > 0)
        require(crop.width > 0 && crop.height > 0)
        val areaScale = sqrt(maxPixels.toDouble() / (crop.width.toDouble() * crop.height))
        val sideScale = maxSide.toDouble() / max(crop.width, crop.height)
        val scale = min(1.0, min(areaScale, sideScale))
        var width = floor(crop.width * scale).toInt().coerceAtLeast(1)
        var height = floor(crop.height * scale).toInt().coerceAtLeast(1)
        while (width.toLong() * height > maxPixels) {
            if (width >= height) width-- else height--
        }
        width = width.coerceAtMost(maxSide)
        height = height.coerceAtMost(maxSide)
        return Size(width, height)
    }

    /** 圆角矩形有符号距离：内部为负，外部为正。 */
    fun roundedRectDistance(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float
    ): Float {
        val r = radius.coerceIn(0f, min(width, height) / 2f)
        val qx = kotlin.math.abs(x - width / 2f) - (width / 2f - r)
        val qy = kotlin.math.abs(y - height / 2f) - (height / 2f - r)
        val outside = kotlin.math.hypot(max(qx, 0f), max(qy, 0f))
        val inside = min(max(qx, qy), 0f)
        return outside + inside - r
    }
}
