package com.glass.todo.data

/** 截图上的裁剪区域，坐标归一化到 0~1，旋转屏幕或图片缩放后仍可使用。 */
data class GlassCrop(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val aspectRatio: Float get() = if (height > 0f) width / height else 1f
    val isValid: Boolean
        get() = left >= 0f && top >= 0f && right <= 1f && bottom <= 1f &&
            right - left >= MIN_SIDE && bottom - top >= MIN_SIDE

    /** 限制到合法范围，并保证区域不会缩成一条线。 */
    fun normalized(): GlassCrop {
        // 多留一点浮点余量，避免 1 - MIN_SIDE 的减法误差让恰好达到最小边长的区域再次失效。
        val safeLeft = left.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val safeTop = top.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val safeRight = right.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
        val safeBottom = bottom.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
        val l = safeLeft.coerceIn(0f, 1f - MIN_SIDE - EPSILON)
        val t = safeTop.coerceIn(0f, 1f - MIN_SIDE - EPSILON)
        val r = safeRight.coerceIn(l + MIN_SIDE + EPSILON, 1f)
        val b = safeBottom.coerceIn(t + MIN_SIDE + EPSILON, 1f)
        return GlassCrop(l, t, r, b)
    }

    fun toPixels(imageWidth: Int, imageHeight: Int): PixelCrop {
        require(imageWidth > 0 && imageHeight > 0)
        val safe = normalized()
        val leftPx = (safe.left * imageWidth).toInt().coerceIn(0, imageWidth - 1)
        val topPx = (safe.top * imageHeight).toInt().coerceIn(0, imageHeight - 1)
        val rightPx = (safe.right * imageWidth).toInt().coerceIn(leftPx + 1, imageWidth)
        val bottomPx = (safe.bottom * imageHeight).toInt().coerceIn(topPx + 1, imageHeight)
        return PixelCrop(leftPx, topPx, rightPx, bottomPx)
    }

    companion object {
        const val MIN_SIDE = 0.02f
        private const val EPSILON = 0.000001f
    }
}

data class PixelCrop(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** 每个桌面组件独立保存的液态玻璃配置。 */
data class WidgetGlassConfig(
    val enabled: Boolean = false,
    val sourceFileName: String? = null,
    val crop: GlassCrop? = null,
    val strength: Float = DEFAULT_STRENGTH
) {
    val isUsable: Boolean
        get() = enabled && !sourceFileName.isNullOrBlank() && crop?.isValid == true

    companion object {
        const val DEFAULT_STRENGTH = 0.65f
    }
}
