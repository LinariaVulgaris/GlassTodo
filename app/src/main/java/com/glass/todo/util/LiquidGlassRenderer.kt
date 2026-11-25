package com.glass.todo.util

import android.graphics.Bitmap
import com.glass.todo.data.GlassCrop
import com.glass.todo.data.PixelCrop
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 静态液态玻璃渲染器。
 *
 * 不依赖 OpenGL/AGSL，因此在 minSdk 31 上也能工作。输入是用户校准过的截图区域，
 * 输出经过轻模糊、边缘折射、色散和双向高光的位图；只在保存或预览时运行一次。
 */
object LiquidGlassRenderer {

    fun render(
        source: Bitmap,
        crop: GlassCrop,
        strength: Float,
        maxOutputPixels: Int = GlassRenderGeometry.MAX_OUTPUT_PIXELS
    ): Bitmap {
        require(!source.isRecycled)
        val pixelCrop = crop.normalized().toPixels(source.width, source.height)
        val outputSize = GlassRenderGeometry.outputSize(pixelCrop, maxOutputPixels)
        val amount = strength.coerceIn(0f, 1f)

        val outputMin = min(outputSize.width, outputSize.height)
        val margin = ceil(outputMin * 0.12f).toInt().coerceAtLeast(8)
        val sourceScale = min(
            outputSize.width.toFloat() / pixelCrop.width,
            outputSize.height.toFloat() / pixelCrop.height
        )
        val marginX = ceil(margin / sourceScale).toInt().coerceAtLeast(1)
        val marginY = ceil(margin / sourceScale).toInt().coerceAtLeast(1)
        val sampleCrop = PixelCrop(
            left = max(0, pixelCrop.left - marginX),
            top = max(0, pixelCrop.top - marginY),
            right = min(source.width, pixelCrop.right + marginX),
            bottom = min(source.height, pixelCrop.bottom + marginY)
        )
        val paddedWidth = (sampleCrop.width * sourceScale).roundToInt().coerceAtLeast(1)
        val paddedHeight = (sampleCrop.height * sourceScale).roundToInt().coerceAtLeast(1)
        val offsetX = ((pixelCrop.left - sampleCrop.left) * sourceScale).roundToInt()
        val offsetY = ((pixelCrop.top - sampleCrop.top) * sourceScale).roundToInt()

        val sampled = Bitmap.createBitmap(
            source,
            sampleCrop.left,
            sampleCrop.top,
            sampleCrop.width,
            sampleCrop.height
        )
        val padded = Bitmap.createScaledBitmap(sampled, paddedWidth, paddedHeight, true)
        if (padded !== sampled) sampled.recycle()

        return try {
            val pixels = IntArray(paddedWidth * paddedHeight)
            padded.getPixels(pixels, 0, paddedWidth, 0, 0, paddedWidth, paddedHeight)
            val blurRadius = max(
                1,
                (outputMin * (0.012f + 0.05f * amount)).roundToInt()
            )
            repeat(3) {
                boxBlurHorizontal(pixels, paddedWidth, paddedHeight, blurRadius)
                boxBlurVertical(pixels, paddedWidth, paddedHeight, blurRadius)
            }

            val result = IntArray(outputSize.width * outputSize.height)
            val radius = outputMin * 0.14f
            val edgeWidth = max(3f, outputMin * 0.09f)
            val refraction = outputMin * (0.018f + 0.045f * amount)
            val chromatic = refraction * 0.32f
            val blackAlpha = 0.10f + 0.16f * amount
            val whiteAlpha = 0.025f + 0.05f * amount

            for (y in 0 until outputSize.height) {
                for (x in 0 until outputSize.width) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val distance = GlassRenderGeometry.roundedRectDistance(
                        px,
                        py,
                        outputSize.width.toFloat(),
                        outputSize.height.toFloat(),
                        radius
                    )
                    if (distance > 0.5f) continue

                    val coverage = (0.5f - distance).coerceIn(0f, 1f)
                    val gradientX = GlassRenderGeometry.roundedRectDistance(
                        px + 1f, py, outputSize.width.toFloat(), outputSize.height.toFloat(), radius
                    ) - GlassRenderGeometry.roundedRectDistance(
                        px - 1f, py, outputSize.width.toFloat(), outputSize.height.toFloat(), radius
                    )
                    val gradientY = GlassRenderGeometry.roundedRectDistance(
                        px, py + 1f, outputSize.width.toFloat(), outputSize.height.toFloat(), radius
                    ) - GlassRenderGeometry.roundedRectDistance(
                        px, py - 1f, outputSize.width.toFloat(), outputSize.height.toFloat(), radius
                    )
                    val gradientLength = hypot(gradientX, gradientY).coerceAtLeast(0.001f)
                    val normalX = gradientX / gradientLength
                    val normalY = gradientY / gradientLength

                    val edgeDistance = max(0f, -distance)
                    val edge = (1f - (edgeDistance / edgeWidth).coerceIn(0f, 1f)).pow(2)
                    val shiftX = normalX * refraction * edge
                    val shiftY = normalY * refraction * edge
                    val sampleX = offsetX + px + shiftX
                    val sampleY = offsetY + py + shiftY

                    val center = sampleBilinear(pixels, paddedWidth, paddedHeight, sampleX, sampleY)
                    val redSample = sampleBilinear(
                        pixels,
                        paddedWidth,
                        paddedHeight,
                        sampleX + normalX * chromatic * edge,
                        sampleY + normalY * chromatic * edge
                    )
                    val blueSample = sampleBilinear(
                        pixels,
                        paddedWidth,
                        paddedHeight,
                        sampleX - normalX * chromatic * edge,
                        sampleY - normalY * chromatic * edge
                    )

                    var red = ((redSample ushr 16) and 0xFF).toFloat()
                    var green = ((center ushr 8) and 0xFF).toFloat()
                    var blue = (blueSample and 0xFF).toFloat()

                    red *= 1f - blackAlpha
                    green *= 1f - blackAlpha
                    blue *= 1f - blackAlpha
                    red += 255f * whiteAlpha
                    green += 255f * whiteAlpha
                    blue += 255f * whiteAlpha

                    // 光源固定在左上角：靠上的边缘发亮，右下边缘轻微压暗。
                    val lightDot = normalX * -0.55f + normalY * -0.83f
                    val highlight = max(0f, lightDot) * edge * 58f * amount
                    val shade = max(0f, -lightDot) * edge * 34f * amount
                    val innerRim = exp(
                        -(((edgeDistance - edgeWidth * 0.16f) / (edgeWidth * 0.07f)).pow(2))
                    ) * 30f
                    red += highlight + innerRim - shade
                    green += highlight + innerRim - shade
                    blue += highlight + innerRim - shade

                    val noise = (((x * 73856093) xor (y * 19349663)) and 0xFF) / 255f - 0.5f
                    red = (red + noise * 2.5f).coerceIn(0f, 255f)
                    green = (green + noise * 2.5f).coerceIn(0f, 255f)
                    blue = (blue + noise * 2.5f).coerceIn(0f, 255f)

                    val alpha = (coverage * 255f).roundToInt().coerceIn(0, 255)
                    result[y * outputSize.width + x] =
                        (alpha shl 24) or
                            (red.toInt() shl 16) or
                            (green.toInt() shl 8) or
                            blue.toInt()
                }
            }

            Bitmap.createBitmap(
                result,
                outputSize.width,
                outputSize.height,
                Bitmap.Config.ARGB_8888
            )
        } finally {
            if (!padded.isRecycled) padded.recycle()
        }
    }

    private fun boxBlurHorizontal(pixels: IntArray, width: Int, height: Int, radius: Int) {
        val temp = IntArray(pixels.size)
        val diameter = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            var red = 0
            var green = 0
            var blue = 0
            for (dx in -radius..radius) {
                val color = pixels[row + dx.coerceIn(0, width - 1)]
                red += (color ushr 16) and 0xFF
                green += (color ushr 8) and 0xFF
                blue += color and 0xFF
            }
            for (x in 0 until width) {
                val color = pixels[row + x]
                temp[row + x] = (0xFF shl 24) or
                    ((red / diameter) shl 16) or
                    ((green / diameter) shl 8) or
                    (blue / diameter)
                val remove = pixels[row + (x - radius).coerceIn(0, width - 1)]
                val add = pixels[row + (x + radius + 1).coerceIn(0, width - 1)]
                red += ((add ushr 16) and 0xFF) - ((remove ushr 16) and 0xFF)
                green += ((add ushr 8) and 0xFF) - ((remove ushr 8) and 0xFF)
                blue += (add and 0xFF) - (remove and 0xFF)
            }
        }
        pixels.indices.forEach { pixels[it] = temp[it] }
    }

    private fun boxBlurVertical(pixels: IntArray, width: Int, height: Int, radius: Int) {
        val temp = IntArray(pixels.size)
        val diameter = radius * 2 + 1
        for (x in 0 until width) {
            var red = 0
            var green = 0
            var blue = 0
            for (dy in -radius..radius) {
                val color = pixels[(dy.coerceIn(0, height - 1) * width) + x]
                red += (color ushr 16) and 0xFF
                green += (color ushr 8) and 0xFF
                blue += color and 0xFF
            }
            for (y in 0 until height) {
                val index = y * width + x
                temp[index] = (0xFF shl 24) or
                    ((red / diameter) shl 16) or
                    ((green / diameter) shl 8) or
                    (blue / diameter)
                val remove = pixels[((y - radius).coerceIn(0, height - 1) * width) + x]
                val add = pixels[((y + radius + 1).coerceIn(0, height - 1) * width) + x]
                red += ((add ushr 16) and 0xFF) - ((remove ushr 16) and 0xFF)
                green += ((add ushr 8) and 0xFF) - ((remove ushr 8) and 0xFF)
                blue += (add and 0xFF) - (remove and 0xFF)
            }
        }
        pixels.indices.forEach { pixels[it] = temp[it] }
    }

    private fun sampleBilinear(
        pixels: IntArray,
        width: Int,
        height: Int,
        rawX: Float,
        rawY: Float
    ): Int {
        val x = rawX.coerceIn(0f, width - 1f)
        val y = rawY.coerceIn(0f, height - 1f)
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val fx = x - x0
        val fy = y - y0
        val c00 = pixels[y0 * width + x0]
        val c10 = pixels[y0 * width + x1]
        val c01 = pixels[y1 * width + x0]
        val c11 = pixels[y1 * width + x1]

        fun channel(shift: Int): Int {
            val top = (((c00 ushr shift) and 0xFF) * (1f - fx) +
                ((c10 ushr shift) and 0xFF) * fx)
            val bottom = (((c01 ushr shift) and 0xFF) * (1f - fx) +
                ((c11 ushr shift) and 0xFF) * fx)
            return (top * (1f - fy) + bottom * fy).toInt().coerceIn(0, 255)
        }

        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
