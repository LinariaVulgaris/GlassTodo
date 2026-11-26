package com.glass.todo.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.glass.todo.R
import com.glass.todo.widget.todo.TodoWidgetProvider
import kotlin.math.max

/** 当前 App 已放到桌面的待办组件实例，供背景设置和旧版布局预览共用。 */
data class WidgetTarget(
    val id: Int,
    val label: String,
    val widthDp: Int,
    val heightDp: Int
) {
    val aspectRatio: Float get() = max(1, widthDp).toFloat() / max(1, heightDp)
}

object WidgetTargetCatalog {

    fun load(context: Context): List<WidgetTarget> {
        val manager = context.getSystemService(AppWidgetManager::class.java) ?: return emptyList()
        val packageManager = context.packageManager
        val component = ComponentName(context, TodoWidgetProvider::class.java)
        return manager.getAppWidgetIds(component).toList().mapNotNull { id ->
            val info = manager.getAppWidgetInfo(id) ?: return@mapNotNull null
            val options = manager.getAppWidgetOptions(id)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, info.minWidth)
                .coerceAtLeast(1)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, info.minHeight)
                .coerceAtLeast(1)
            val providerLabel = info.loadLabel(packageManager)?.toString().orEmpty()
            val label = providerLabel.ifBlank { context.getString(R.string.widget_todo_label) }
            WidgetTarget(id, label, width, height)
        }.sortedWith(
            compareBy<WidgetTarget> { it.label }
                .thenBy { it.widthDp }
                .thenBy { it.heightDp }
        )
    }
}
