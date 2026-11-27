package com.glass.todo.widget.todo

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.glass.todo.R
import com.glass.todo.data.Prefs
import com.glass.todo.data.Repository
import com.glass.todo.data.TodoEntity
import com.glass.todo.data.WidgetBackgroundConfig
import com.glass.todo.data.WidgetLayoutMode
import com.glass.todo.util.DayMath
import com.glass.todo.util.TodoDateText
import com.glass.todo.util.WidgetLayoutGeometry
import com.glass.todo.util.WidgetPalette
import com.glass.todo.util.WidgetTypography
import com.glass.todo.util.withAlpha
import com.glass.todo.widget.UpdateScheduler
import com.glass.todo.widget.WidgetIntent
import com.glass.todo.widget.WidgetRefresher
import com.glass.todo.widget.background.WidgetBackgroundLayer
import java.time.LocalDate

/**
 * 待办列表组件：固定竖向单列，可叠加自定义背景，点击任意一行直接勾选完成。
 *
 * RemoteViews 不支持 RecyclerView，集合类只能用 ListView / GridView / StackView。
 * 数据源这里用 API 31 的 [RemoteViews.RemoteCollectionItems]（since=31，正好等于 minSdk）：
 * 所有行在更新时直接烘进 RemoteViews，不再需要 RemoteViewsService + RemoteViewsFactory，
 * 因此也根除了「数据库已经改了，但 ListView 沿用缓存旧行」这类同步问题——
 * 那条路必须额外调 notifyAppWidgetViewDataChanged 才生效，而该 API 现已被标记弃用。
 *
 * 代价是每次刷新都要重建整个列表并走一次 Binder 传输；
 * 但 [Prefs.maxTodoItems] 上限 20 行、每行只是几个 TextView，远低于事务大小限制。
 */
class TodoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        WidgetRefresher.refreshAll(context) { pending.finish() }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pending = goAsync()
        WidgetRefresher.refreshAll(context) { pending.finish() }
    }

    override fun onEnabled(context: Context) {
        UpdateScheduler.scheduleDaily(context)
    }

    override fun onDisabled(context: Context) {
        // 另一种组件可能还在，所以不能直接 cancel，要统一检查。
        UpdateScheduler.sync(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetBackgroundLayer.forget(context, it) }
    }

    internal data class TodoPreview(
        val views: RemoteViews,
        val rows: List<PreviewRow>
    )

    internal data class PreviewRow(val id: Long, val views: RemoteViews)

    companion object {

        private val ROOT_ID = android.R.id.background

        /** 只能在后台线程调用：内部有阻塞式数据库查询 */
        fun updateAllBlocking(context: Context, awm: AppWidgetManager) {
            val ids = awm.getAppWidgetIds(ComponentName(context, TodoWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val repo = Repository.get(context)
            val prefs = repo.prefs
            val pendingCount = repo.todos.countPending()
            val todos = repo.sortedTodosForWidgetBlocking(prefs.maxTodoItems, prefs.showDoneInWidget)
            val today = DayMath.today()

            ids.forEach { id ->
                val options = awm.getAppWidgetOptions(id)
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
                val mode = WidgetLayoutGeometry.resolve(width, height, prefs.widgetLayoutMode(id))
                val todoLayout = WidgetLayoutGeometry.todoLayout(mode, width)
                awm.updateAppWidget(id, render(
                    context, prefs, id, pendingCount, todos, today, todoLayout,
                    width, height
                ))
            }
        }

        /** 给背景设置页使用：读取与桌面组件相同的数据和排版，但不写回桌面。 */
        internal fun buildPreview(
            context: Context,
            widgetId: Int,
            widthDp: Int,
            heightDp: Int,
            previewConfig: WidgetBackgroundConfig,
            previewBitmap: Bitmap?
        ): TodoPreview {
            val repo = Repository.get(context)
            val prefs = repo.prefs
            val pendingCount = repo.todos.countPending()
            val todos = repo.sortedTodosForWidgetBlocking(prefs.maxTodoItems, prefs.showDoneInWidget)
            val today = DayMath.today()
            val mode = WidgetLayoutGeometry.resolve(widthDp, heightDp, WidgetLayoutMode.AUTO)
            val todoLayout = WidgetLayoutGeometry.todoLayout(mode, widthDp)
            val views = render(
                context, prefs, widgetId, pendingCount, todos, today, todoLayout,
                widthDp, heightDp,
                previewConfig, previewBitmap, localPreview = true
            )
            val rows = todos.map { todo ->
                PreviewRow(
                    todo.id,
                    buildRow(context, prefs, todo, today, todoLayout, localPreview = true)
                )
            }
            return TodoPreview(views, rows)
        }

        private fun render(
            context: Context,
            prefs: Prefs,
            widgetId: Int,
            pendingCount: Int,
            todos: List<TodoEntity>,
            today: LocalDate,
            todoLayout: WidgetLayoutGeometry.TodoLayout,
            widthDp: Int,
            heightDp: Int,
            previewConfig: WidgetBackgroundConfig? = null,
            previewBitmap: Bitmap? = null,
            localPreview: Boolean = false
        ): RemoteViews {
            val layout = when (todoLayout) {
                WidgetLayoutGeometry.TodoLayout.GRID -> R.layout.widget_todo_grid
                WidgetLayoutGeometry.TodoLayout.COMPACT_LIST,
                WidgetLayoutGeometry.TodoLayout.COMFORT_LIST -> R.layout.widget_todo
            }
            val views = RemoteViews(context.packageName, layout)
            val base = prefs.widgetTextColor

            WidgetBackgroundLayer.apply(
                context, views, prefs, widgetId, previewConfig, previewBitmap,
                WidgetLayoutGeometry.displayPixels(widthDp, heightDp, context.resources.displayMetrics.density)
            )
            WidgetTypography.apply(
                views,
                prefs.widgetFontScalePercent,
                R.id.tw_header to 15f,
                R.id.tw_count to 12f,
                R.id.tw_empty to 13f
            )

            views.setTextViewText(R.id.tw_header, context.getString(R.string.widget_todo_header))
            views.setTextViewText(
                R.id.tw_count,
                context.getString(R.string.widget_todo_count, pendingCount)
            )
            views.setTextColor(R.id.tw_header, base)
            views.setTextColor(R.id.tw_count, base.withAlpha(0.7f))
            views.setTextColor(R.id.tw_empty, base.withAlpha(0.8f))
            views.setColorStateList(
                R.id.tw_add,
                "setImageTintList",
                ColorStateList.valueOf(base)
            )
            views.setTextViewText(R.id.tw_empty, context.getString(R.string.widget_todo_empty))

            views.setEmptyView(R.id.tw_list, R.id.tw_empty)
            if (!localPreview) {
                val items = RemoteViews.RemoteCollectionItems.Builder()
                    .setHasStableIds(true)
                    .setViewTypeCount(2)
                todos.forEach { todo ->
                    items.addItem(todo.id, buildRow(context, prefs, todo, today, todoLayout))
                }

                views.setRemoteAdapter(R.id.tw_list, items.build())

                // 模板 + fill-in：模板负责「切换完成状态」这个动作，
                // 具体是哪一条由每行的 fillInIntent 补上 id。
                views.setPendingIntentTemplate(
                    R.id.tw_list,
                    WidgetIntent.toggleTodoTemplate(context, widgetId)
                )
            }

            if (!localPreview) {
                views.setOnClickPendingIntent(ROOT_ID, WidgetIntent.openApp(context, widgetId * 100 + 1))
                views.setOnClickPendingIntent(R.id.tw_add, WidgetIntent.openAddTodo(context, widgetId))
            }
            return views
        }

        private fun buildRow(
            context: Context,
            prefs: Prefs,
            todo: TodoEntity,
            today: LocalDate,
            todoLayout: WidgetLayoutGeometry.TodoLayout,
            localPreview: Boolean = false
        ): RemoteViews {
            val compact = todoLayout == WidgetLayoutGeometry.TodoLayout.COMPACT_LIST
            val rowLayout = if (compact) R.layout.widget_todo_item_compact else R.layout.widget_todo_item
            val rv = RemoteViews(context.packageName, rowLayout)
            val base = prefs.widgetTextColor
            val titleSp = when (todoLayout) {
                WidgetLayoutGeometry.TodoLayout.GRID -> 12f
                WidgetLayoutGeometry.TodoLayout.COMPACT_LIST -> 13f
                WidgetLayoutGeometry.TodoLayout.COMFORT_LIST -> 15f
            }
            val noteSp = if (todoLayout == WidgetLayoutGeometry.TodoLayout.COMFORT_LIST) 11f else 10f
            val deadlineSp = when (todoLayout) {
                WidgetLayoutGeometry.TodoLayout.GRID -> 10f
                WidgetLayoutGeometry.TodoLayout.COMPACT_LIST -> 11f
                WidgetLayoutGeometry.TodoLayout.COMFORT_LIST -> 12f
            }
            WidgetTypography.apply(
                rv,
                prefs.widgetFontScalePercent,
                R.id.item_title to titleSp,
                R.id.item_note to noteSp,
                R.id.item_deadline to deadlineSp,
                R.id.item_badge to deadlineSp
            )

            rv.setTextViewText(R.id.item_title, todo.title)
            rv.setTextColor(
                R.id.item_title,
                if (todo.done) base.withAlpha(0.45f) else base
            )

            if (todo.note.isBlank()) {
                rv.setViewVisibility(R.id.item_note, View.GONE)
            } else {
                rv.setViewVisibility(R.id.item_note, View.VISIBLE)
                rv.setTextViewText(R.id.item_note, todo.note)
                rv.setTextColor(R.id.item_note, base.withAlpha(0.6f))
            }

            // 勾选状态用两张图标切换，而不是 CheckBox：
            // CheckBox 虽然 API 31 起进了 RemoteViews 白名单，但带状态的控件在
            // 组件被缩放 / 拖动重建时容易和数据库不同步，换图最稳。
            rv.setInt(
                R.id.item_check,
                "setImageResource",
                if (todo.done) R.drawable.ic_check_on else R.drawable.ic_check_off
            )
            rv.setColorStateList(
                R.id.item_check,
                "setImageTintList",
                ColorStateList.valueOf(if (todo.done) base.withAlpha(0.45f) else base)
            )

            val labels = TodoDateText.labels(
                dueEpochDay = todo.dueEpochDay,
                dueMinuteOfDay = todo.dueMinuteOfDay,
                mode = prefs.todoDateDisplayMode,
                timePrecision = prefs.todoTimePrecision,
                from = today,
                compact = compact
            )
            val dateColor = badgeColor(context, prefs, base, todo, today)
            if (compact) {
                val metadata = labels.multiline
                if (metadata == null) {
                    rv.setViewVisibility(R.id.item_deadline, View.GONE)
                } else {
                    rv.setViewVisibility(R.id.item_deadline, View.VISIBLE)
                    rv.setTextViewText(R.id.item_deadline, metadata)
                    rv.setTextColor(
                        R.id.item_deadline,
                        dateColor
                    )
                }
                rv.setViewVisibility(R.id.item_badge, View.GONE)
            } else {
                if (labels.deadline == null) {
                    rv.setViewVisibility(R.id.item_deadline, View.GONE)
                } else {
                    rv.setViewVisibility(R.id.item_deadline, View.VISIBLE)
                    rv.setTextViewText(R.id.item_deadline, labels.deadline)
                    rv.setTextColor(
                        R.id.item_deadline,
                        dateColor
                    )
                }
                if (labels.remaining == null) {
                    rv.setViewVisibility(R.id.item_badge, View.GONE)
                } else {
                    rv.setViewVisibility(R.id.item_badge, View.VISIBLE)
                    rv.setTextViewText(R.id.item_badge, labels.remaining)
                    rv.setTextColor(R.id.item_badge, dateColor)
                }
            }

            // 本地效果图不绑定点击动作，避免把桌面宿主专用的 fill-in 事件带进普通 View。
            if (!localPreview) {
                rv.setOnClickFillInIntent(
                    R.id.item_root,
                    Intent().putExtra(WidgetIntent.EXTRA_TODO_ID, todo.id)
                )
            }
            return rv
        }

        private fun badgeColor(
            context: Context,
            prefs: Prefs,
            base: Int,
            todo: TodoEntity,
            today: LocalDate
        ): Int {
            if (todo.done) return base.withAlpha(0.4f)
            val due = todo.dueEpochDay ?: return base
            if (!prefs.todoUrgentDateEnabled) return base
            val days = DayMath.daysForDue(due, today) ?: return base
            val urgent = DayMath.isUrgent(days, prefs.todoUrgentDateThresholdDays)
            return WidgetPalette.colorFor(context, urgent, base)
        }
    }
}
