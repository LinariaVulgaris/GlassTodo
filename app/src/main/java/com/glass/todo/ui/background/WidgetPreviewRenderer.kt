package com.glass.todo.ui.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.core.graphics.createBitmap
import com.glass.todo.data.GlassCrop
import com.glass.todo.data.WidgetBackgroundConfig
import com.glass.todo.data.WidgetBackgroundType
import com.glass.todo.util.WidgetBackgroundRenderer
import com.glass.todo.util.WidgetLayoutGeometry
import com.glass.todo.widget.WidgetTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 用真实组件排版和实际数据生成背景草稿的最终效果图。 */
object WidgetPreviewRenderer {

    private const val MAX_PREVIEW_SIDE = 960
    // 普通 View 预览不经过 Binder，可以按预览画布尺寸保留更多背景细节。
    private const val MAX_DRAFT_PIXELS = 600_000

    suspend fun render(
        context: Context,
        target: WidgetTarget,
        config: WidgetBackgroundConfig,
        source: Bitmap?,
        crop: GlassCrop?
    ): Bitmap {
        val draftBackground = if (config.type == WidgetBackgroundType.IMAGE && source != null) {
            withContext(Dispatchers.Default) {
                WidgetBackgroundRenderer.render(
                    source = source,
                    crop = crop ?: GlassCrop(0f, 0f, 1f, 1f),
                    maxPixels = MAX_DRAFT_PIXELS,
                    maxSide = MAX_PREVIEW_SIDE,
                    minimumPixels = WidgetLayoutGeometry.displayPixels(
                        target.widthDp,
                        target.heightDp,
                        context.resources.displayMetrics.density
                    )
                )
            }
        } else {
            null
        }
        val solidColor = if (config.type == WidgetBackgroundType.COLOR) {
            config.color or 0xFF000000.toInt()
        } else {
            null
        }

        return try {
            withContext(Dispatchers.Main) {
                val root = WidgetBackgroundNativePreview.preview(
                    context = context,
                    target = target,
                    background = draftBackground,
                    backgroundColor = solidColor
                )
                drawPreview(context, root, target)
            }
        } finally {
            draftBackground?.recycle()
        }
    }

    private fun drawPreview(
        context: Context,
        root: View,
        target: WidgetTarget
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val rawWidth = target.widthDp.coerceAtLeast(1) * density
        val rawHeight = target.heightDp.coerceAtLeast(1) * density
        val scale = minOf(1f, MAX_PREVIEW_SIDE / maxOf(rawWidth, rawHeight))
        val width = (rawWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (rawHeight * scale).roundToInt().coerceAtLeast(1)

        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, width, height)
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        return bitmap
    }
}
