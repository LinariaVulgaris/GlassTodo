package com.glass.todo.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.glass.todo.data.GlassCrop

/** 将用户图片的选定区域直接裁切、缩放到组件背景，不添加模糊或玻璃效果。 */
object WidgetBackgroundRenderer {

    fun render(
        source: Bitmap,
        crop: GlassCrop,
        maxPixels: Int = GlassRenderGeometry.MAX_OUTPUT_PIXELS,
        maxSide: Int = GlassRenderGeometry.MAX_OUTPUT_SIDE,
        minimumPixels: Int = 0,
        config: Bitmap.Config = Bitmap.Config.RGB_565
    ): Bitmap {
        require(!source.isRecycled)
        val pixelCrop = crop.normalized().toPixels(source.width, source.height)
        val outputSize = GlassRenderGeometry.outputSize(pixelCrop, maxPixels, maxSide, minimumPixels)
        val output = createBitmap(
            outputSize.width,
            outputSize.height,
            config
        )
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val sourceRect = Rect(
            pixelCrop.left,
            pixelCrop.top,
            pixelCrop.right,
            pixelCrop.bottom
        )
        canvas.drawBitmap(
            source,
            sourceRect,
            RectF(0f, 0f, outputSize.width.toFloat(), outputSize.height.toFloat()),
            paint
        )
        return output
    }
}
