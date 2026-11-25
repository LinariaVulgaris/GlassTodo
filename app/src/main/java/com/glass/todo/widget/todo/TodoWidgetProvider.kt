package com.glass.todo.widget.todo

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.glass.todo.R
import com.glass.todo.data.Prefs
import com.glass.todo.data.Repository
import com.glass.todo.data.TodoEntity
import com.glass.todo.util.DayMath
import com.glass.todo.util.DateText
import com.glass.todo.util.WidgetLayoutGeometry
import com.glass.todo.util.WidgetPalette
import com.glass.todo.util.WidgetTypography
import com.glass.todo.util.withAlpha
import com.glass.todo.widget.UpdateScheduler
import com.glass.todo.widget.WidgetIntent
import com.glass.todo.widget.WidgetRefresher
import com.glass.todo.widget.glass.WidgetGlassBackground
import java.time.LocalDate

/**
 * 待办列表组件：透明背景，白色文字，点击任意一行直接勾选完成。
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
        appWidgetIds.forEach { WidgetGlassBackground.forget(context, it) }
    }

    companion object {

        private val ROOT_ID = android.R.id.background

        /** 只能在后台线程调用：内部有阻塞式数据库查询 */
        fun updateAllBlocking(context: Context, awm: AppWidgetManager) {
            val ids = awm.getAppWidgetIds(ComponentName(context, TodoWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val repo = Repository.get(context)
            val prefs = repo.prefs
            val pendingCount = repo.todos.countPending()
            val todos = if (prefs.showDoneInWidget) {
                repo.todos.getForWidgetIncludingDone(prefs.maxTodoItems)
            } else {
                repo.todos.getPendingForWidget(prefs.maxTodoItems)
            }
            val today = DayMath.today()

            ids.forEach { id ->
                val options = awm.getAppWidgetOptions(id)
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
                val mode = WidgetLayoutGeometry.resolve(width, height, prefs.widgetLayoutMode(id))
                val columns = WidgetLayoutGeometry.todoColumns(mode, width)
                awm.updateAppWidget(id, render(context, prefs, id, pendingCount, todos, today, columns))
            }
        }

        private fun render(
            context: Context,
            prefs: Prefs,
            widgetId: Int,
            pendingCount: Int,
            todos: List<TodoEntity>,
            today: LocalDate,
            columns: Int
        ): RemoteViews {
            val layout = if (columns > 1) R.layout.widget_todo_grid else R.layout.widget_todo
            val views = RemoteViews(context.packageName, layout)
            val base = prefs.widgetTextColor

            WidgetGlassBackground.apply(context, views, prefs, widgetId)
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
            views.setTextViewText(R.id.tw_empty, context.getString(R.string.widget_todo_empty))

            val items = RemoteViews.RemoteCollectionItems.Builder()
                .setHasStableIds(true)
                .setViewTypeCount(1)
            todos.forEach { todo -> items.addItem(todo.id, buildRow(context, prefs, todo, today)) }

            views.setRemoteAdapter(R.id.tw_list, items.build())
            views.setEmptyView(R.id.tw_list, R.id.tw_empty)

            // 模板 + fill-in：模板负责「切换完成状态」这个动作，
            // 具体是哪一条由每行的 fillInIntent 补上 id。
            views.setPendingIntentTemplate(
                R.id.tw_list,
                WidgetIntent.toggleTodoTemplate(context, widgetId)
            )

            views.setOnClickPendingIntent(ROOT_ID, WidgetIntent.openApp(context, widgetId * 100 + 1))
            views.setOnClickPendingIntent(R.id.tw_add, WidgetIntent.openAddTodo(context, widgetId))
            return views
        }

        private fun buildRow(
            context: Context,
            prefs: Prefs,
            todo: TodoEntity,
            today: LocalDate
        ): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_todo_item)
            val base = prefs.widgetTextColor
            WidgetTypography.apply(
                rv,
                prefs.widgetFontScalePercent,
                R.id.item_title to 14f,
                R.id.item_note to 11f,
                R.id.item_deadline to 11f,
                R.id.item_badge to 11f
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

            val badge = DayMath.badgeFor(todo.dueEpochDay, today)
            val detailedMinute = todo.dueMinuteOfDay?.let { DateText.normalizeMinuteOfDay(it) }
            if (badge != null && todo.dueEpochDay != null && detailedMinute != null) {
                rv.setViewVisibility(R.id.item_badge, View.GONE)
                rv.setViewVisibility(R.id.item_deadline, View.VISIBLE)
                rv.setTextViewText(
                    R.id.item_deadline,
                    DateText.formatExactDateTime(
                        todo.dueEpochDay,
                        detailedMinute,
                        today,
                        multiline = true
                    )
                )
                rv.setTextColor(
                    R.id.item_deadline,
                    badgeColor(context, base, todo, today)
                )
            } else {
                rv.setViewVisibility(R.id.item_deadline, View.GONE)
                if (badge == null) {
                    rv.setViewVisibility(R.id.item_badge, View.GONE)
                } else {
                    rv.setViewVisibility(R.id.item_badge, View.VISIBLE)
                    rv.setTextViewText(R.id.item_badge, badge)
                    rv.setTextColor(R.id.item_badge, badgeColor(context, base, todo, today))
                }
            }

            // fill-in：只携带 id，动作本身由 Provider 设的模板 PendingIntent 决定
            rv.setOnClickFillInIntent(
                R.id.item_root,
                Intent().putExtra(WidgetIntent.EXTRA_TODO_ID, todo.id)
            )
            return rv
        }

        private fun badgeColor(
            context: Context,
            base: Int,
            todo: TodoEntity,
            today: LocalDate
        ): Int {
            if (todo.done) return base.withAlpha(0.4f)
            // 没有截止日就不会有徽标；有截止日但离得还远时保持白色即可，
            // 只有临近 / 逾期才用强调色，避免整屏都是彩色。
            val due = todo.dueEpochDay ?: return base
            val days = DayMath.daysForDue(due, today) ?: return base
            return WidgetPalette.colorFor(context, DayMath.urgencyOf(days), base)
        }
    }
}
