package com.glass.todo.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.glass.todo.R
import com.glass.todo.widget.countdown.CountdownWidgetProvider
import com.glass.todo.widget.todo.TodoWidgetProvider
import kotlin.math.max

/** 当前 App 已放到桌面的组件实例，供截图校准和尺寸布局设置共用。 */
data class WidgetTarget(
    val id: Int,
    val label: String,
    val kind: Kind,
    val widthDp: Int,
    val heightDp: Int
) {
    val aspectRatio: Float get() = max(1, widthDp).toFloat() / max(1, heightDp).toFloat()

    enum class Kind { COUNTDOWN, TODO }
}

object WidgetTargetCatalog {

    fun load(context: Context): List<WidgetTarget> {
        val manager = context.getSystemService(AppWidgetManager::class.java) ?: return emptyList()
        val packageManager = context.packageManager
        val result = mutableListOf<WidgetTarget>()
        val providers = listOf(
            CountdownWidgetProvider::class.java to WidgetTarget.Kind.COUNTDOWN,
            TodoWidgetProvider::class.java to WidgetTarget.Kind.TODO
        )
        providers.forEach { (provider, kind) ->
            val component = ComponentName(context, provider)
            manager.getAppWidgetIds(component).forEach { id ->
                val info = manager.getAppWidgetInfo(id) ?: return@forEach
                val options = manager.getAppWidgetOptions(id)
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, info.minWidth)
                    .coerceAtLeast(1)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, info.minHeight)
                    .coerceAtLeast(1)
                val providerLabel = info.loadLabel(packageManager)?.toString().orEmpty()
                val fallback = if (kind == WidgetTarget.Kind.COUNTDOWN) {
                    context.getString(R.string.widget_countdown_label)
                } else {
                    context.getString(R.string.widget_todo_label)
                }
                val label = if (providerLabel.isBlank()) "$fallback #$id" else "$providerLabel #$id"
                result += WidgetTarget(id, label, kind, width, height)
            }
        }
        return result.sortedBy { it.label }
    }
}
