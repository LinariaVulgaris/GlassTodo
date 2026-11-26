package com.glass.todo.widget.glass

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.glass.todo.R
import com.glass.todo.data.Prefs
import com.glass.todo.data.Repository

/** 两种组件共用的可选液态玻璃背景层，保持原有根背景作为回退。 */
object WidgetGlassBackground {

    fun apply(
        context: Context,
        views: RemoteViews,
        prefs: Prefs,
        widgetId: Int
    ) {
        val config = prefs.widgetGlassConfig(widgetId)
        val bitmap = if (config.isUsable) WidgetGlassStore.loadRendered(context, widgetId) else null
        if (bitmap != null) {
            views.setViewVisibility(R.id.widget_background, View.VISIBLE)
            views.setImageViewBitmap(R.id.widget_background, bitmap)
            // 液态玻璃图本身已经包含底板，避免再叠一层静态玻璃背景。
            views.setInt(android.R.id.background, "setBackgroundResource", android.R.color.transparent)
        } else {
            views.setViewVisibility(R.id.widget_background, View.GONE)
            views.setInt(
                android.R.id.background,
                "setBackgroundResource",
                if (prefs.glassPanelEnabled) R.drawable.glass_panel else android.R.color.transparent
            )
        }
    }

    fun forget(context: Context, widgetId: Int) {
        Repository.get(context).prefs.forgetWidget(widgetId)
        WidgetGlassStore.deleteFiles(context, widgetId)
    }
}
