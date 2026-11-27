package com.glass.todo.widget.background

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.glass.todo.R
import com.glass.todo.data.Prefs
import com.glass.todo.data.Repository
import com.glass.todo.data.WidgetBackgroundConfig
import com.glass.todo.data.WidgetBackgroundType

/** 两种组件共用的背景层：默认背景、纯色块或用户图片。 */
object WidgetBackgroundLayer {

    private const val ROOT_ID = android.R.id.background
    private const val TAG = "WidgetBackground"

    fun apply(
        context: Context,
        views: RemoteViews,
        prefs: Prefs,
        widgetId: Int,
        previewConfig: WidgetBackgroundConfig? = null,
        previewBitmap: Bitmap? = null,
        minimumBackgroundPixels: Int = 0
    ) {
        val config = previewConfig ?: prefs.widgetBackgroundConfig(widgetId)
        when (config.type) {
            WidgetBackgroundType.IMAGE -> {
                val bitmap = previewBitmap ?: try {
                    WidgetBackgroundStore.loadOrCreateRendered(context, widgetId, config, minimumBackgroundPixels)
                } catch (t: Throwable) {
                    Log.e(TAG, "读取组件图片背景失败", t)
                    null
                }
                if (bitmap != null) {
                    views.setViewVisibility(R.id.widget_background, View.VISIBLE)
                    views.setImageViewBitmap(R.id.widget_background, bitmap)
                    views.setInt(ROOT_ID, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
                } else {
                    applyDefault(views, prefs)
                }
            }

            WidgetBackgroundType.COLOR -> {
                if (config.hasColor) {
                    views.setViewVisibility(R.id.widget_background, View.GONE)
                    views.setInt(
                        ROOT_ID,
                        "setBackgroundColor",
                        config.color or 0xFF000000.toInt()
                    )
                } else {
                    applyDefault(views, prefs)
                }
            }

            WidgetBackgroundType.NONE -> applyDefault(views, prefs)
        }
    }

    private fun applyDefault(views: RemoteViews, prefs: Prefs) {
        views.setViewVisibility(R.id.widget_background, View.GONE)
        views.setInt(
            ROOT_ID,
            "setBackgroundResource",
            if (prefs.glassPanelEnabled) R.drawable.glass_panel else android.R.color.transparent
        )
    }

    fun forget(context: Context, widgetId: Int) {
        Repository.get(context).prefs.forgetWidget(widgetId)
        WidgetBackgroundStore.deleteFiles(context, widgetId)
    }
}
