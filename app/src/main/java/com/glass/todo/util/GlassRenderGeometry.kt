package com.glass.todo.util

import com.glass.todo.data.PixelCrop
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 不依赖 Android 的渲染尺寸计算，方便 JVM 单测覆盖 Binder 位图大小边界。 */
object GlassRenderGeometry {

    /**
     * RGB_565 背景每像素 2 字节；24 万像素约 480KB，与旧版 12 万 ARGB_8888
     * 的位图预算相同，却多保留一倍像素，并为 RemoteViews 中最多 20 行待办留出余量。
     */
    const val MAX_OUTPUT_PIXELS = 240_000
    const val MAX_OUTPUT_SIDE = 640

    data class Size(val width: Int, val height: Int) {
        val pixels: Int get() = width * height
    }

    fun outputSize(
        crop: PixelCrop,
        maxPixels: Int = MAX_OUTPUT_PIXELS,
        maxSide: Int = MAX_OUTPUT_SIDE,
        minimumPixels: Int = 0
    ): Size {
        require(maxPixels > 0 && maxSide > 0 && minimumPixels >= 0)
        require(crop.width > 0 && crop.height > 0)
        val area = crop.width.toDouble() * crop.height.toDouble()
        val areaScale = sqrt(maxPixels.toDouble() / area)
        val sideScale = maxSide.toDouble() / max(crop.width, crop.height)
        val budgetScale = min(areaScale, sideScale)
        val maxScale = if (minimumPixels > 0) budgetScale else min(1.0, budgetScale)
        // 有桌面尺寸信息时按显示像素输出：小裁剪图允许放大，大裁剪图按需缩小。
        // 未提供尺寸时保持原行为，不无故放大原图。
        val desiredScale = if (minimumPixels > 0) sqrt(minimumPixels.toDouble() / area) else 1.0
        val scale = min(maxScale, desiredScale)
        var width = floor(crop.width * scale).toInt().coerceAtLeast(1)
        var height = floor(crop.height * scale).toInt().coerceAtLeast(1)
        while (width.toLong() * height > maxPixels) {
            if (width >= height) width-- else height--
        }
        width = width.coerceAtMost(maxSide)
        height = height.coerceAtMost(maxSide)
        return Size(width, height)
    }

    /** 圆角矩形有符号距离：内部为负、外部为正。 */
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
