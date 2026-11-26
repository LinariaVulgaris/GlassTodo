package com.glass.todo.ui.background

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.glass.todo.R
import com.glass.todo.data.Prefs
import com.glass.todo.data.Repository
import com.glass.todo.data.TodoEntity
import com.glass.todo.data.WidgetLayoutMode
import com.glass.todo.util.DayMath
import com.glass.todo.util.TodoDateText
import com.glass.todo.util.WidgetLayoutGeometry
import com.glass.todo.util.WidgetPalette
import com.glass.todo.util.WidgetTypography
import com.glass.todo.util.withAlpha
import com.glass.todo.widget.WidgetTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 在应用页面里直接加载组件布局并绑定真实数据。
 *
 * 这里刻意不使用 RemoteViews：最终效果预览运行在普通 Activity 中，而 RemoteViews 的
 * 集合、点击模板等能力属于桌面宿主。普通 View 可以复用完全相同的 XML 排版，同时避免
 * apply/reapply 时的宿主差异。
 */
object WidgetBackgroundNativePreview {

    suspend fun preview(
        context: Context,
        target: WidgetTarget,
        background: Bitmap?,
        backgroundColor: Int? = null
    ): View = withContext(Dispatchers.Main) {
        val data = withContext(Dispatchers.IO) { loadData(context, target) }
        inflateTodo(context, data, background, backgroundColor)
    }

    private suspend fun loadData(context: Context, target: WidgetTarget): TodoData {
        val repo = Repository.get(context)
        val prefs = repo.prefs
        val pending = repo.todos.countPending()
        val todos = if (prefs.showDoneInWidget) {
            repo.todos.getForWidgetIncludingDone(prefs.maxTodoItems)
        } else {
            repo.todos.getPendingForWidget(prefs.maxTodoItems)
        }
        return TodoData(prefs, pending, todos, DayMath.today(), WidgetLayoutGeometry.todoLayout(
            WidgetLayoutGeometry.resolve(target.widthDp, target.heightDp, WidgetLayoutMode.AUTO),
            target.widthDp
        ))
    }

    private fun inflateTodo(
        context: Context,
        data: TodoData,
        background: Bitmap?,
        backgroundColor: Int?
    ): View {
        val root = inflate(context, R.layout.widget_todo)
        applyBackground(root, data.prefs, background, backgroundColor)
        val base = data.prefs.widgetTextColor
        text(root, R.id.tw_header).apply {
            setText(R.string.widget_todo_header)
            setTextColor(base)
            textSize = WidgetTypography.scaledSize(15f, data.prefs.widgetFontScalePercent)
        }
        text(root, R.id.tw_count).apply {
            text = context.getString(R.string.widget_todo_count, data.pendingCount)
            setTextColor(base.withAlpha(0.7f))
            textSize = WidgetTypography.scaledSize(12f, data.prefs.widgetFontScalePercent)
        }
        text(root, R.id.tw_empty).apply {
            setText(R.string.widget_todo_empty)
            setTextColor(base.withAlpha(0.8f))
            textSize = WidgetTypography.scaledSize(13f, data.prefs.widgetFontScalePercent)
        }
        image(root, R.id.tw_add).imageTintList = ColorStateList.valueOf(base)

        val list = root.findViewById<ListView>(R.id.tw_list)
        val empty = root.findViewById<View>(R.id.tw_empty)
        list.emptyView = empty
        list.adapter = TodoAdapter(context, data, base)
        return root
    }

    private fun applyBackground(
        root: View,
        prefs: Prefs,
        background: Bitmap?,
        backgroundColor: Int?
    ) {
        val image = root.findViewById<ImageView>(R.id.widget_background)
        if (background != null) {
            image.setImageBitmap(background)
            image.visibility = View.VISIBLE
            root.background = null
        } else {
            image.setImageDrawable(null)
            image.visibility = View.GONE
            if (backgroundColor != null) {
                root.setBackgroundColor(backgroundColor)
            } else {
                root.setBackgroundResource(if (prefs.glassPanelEnabled) {
                    R.drawable.glass_panel
                } else {
                    android.R.color.transparent
                })
            }
        }
    }

    private fun inflate(context: Context, layout: Int): View =
        LayoutInflater.from(context).inflate(layout, null, false)

    private fun text(root: View, id: Int): TextView = root.findViewById(id)
    private fun image(root: View, id: Int): ImageView = root.findViewById(id)

    private data class TodoData(
        val prefs: Prefs,
        val pendingCount: Int,
        val todos: List<TodoEntity>,
        val today: LocalDate,
        val layout: WidgetLayoutGeometry.TodoLayout
    )

    private class TodoAdapter(
        private val context: Context,
        private val data: TodoData,
        private val base: Int
    ) : BaseAdapter() {
        override fun getCount(): Int = data.todos.size
        override fun getItem(position: Int): Any = data.todos[position]
        override fun getItemId(position: Int): Long = data.todos[position].id
        override fun hasStableIds(): Boolean = true

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val compact = data.layout == WidgetLayoutGeometry.TodoLayout.COMPACT_LIST
            val layout = if (compact) R.layout.widget_todo_item_compact else R.layout.widget_todo_item
            val row = convertView ?: LayoutInflater.from(context)
                .inflate(layout, parent, false)
            bind(row, data.todos[position])
            return row
        }

        private fun bind(row: View, todo: TodoEntity) {
            val titleSp = when (data.layout) {
                WidgetLayoutGeometry.TodoLayout.GRID -> 12f
                WidgetLayoutGeometry.TodoLayout.COMPACT_LIST -> 13f
                WidgetLayoutGeometry.TodoLayout.COMFORT_LIST -> 15f
            }
            val noteSp = if (data.layout == WidgetLayoutGeometry.TodoLayout.COMFORT_LIST) 11f else 10f
            val deadlineSp = when (data.layout) {
                WidgetLayoutGeometry.TodoLayout.GRID -> 10f
                WidgetLayoutGeometry.TodoLayout.COMPACT_LIST -> 11f
                WidgetLayoutGeometry.TodoLayout.COMFORT_LIST -> 12f
            }
            val percent = data.prefs.widgetFontScalePercent
            text(row, R.id.item_title).apply {
                text = todo.title
                setTextColor(if (todo.done) base.withAlpha(0.45f) else base)
                textSize = WidgetTypography.scaledSize(titleSp, percent)
            }
            text(row, R.id.item_note).apply {
                visibility = if (todo.note.isBlank()) View.GONE else View.VISIBLE
                text = todo.note
                setTextColor(base.withAlpha(0.6f))
                textSize = WidgetTypography.scaledSize(noteSp, percent)
            }
            image(row, R.id.item_check).apply {
                setImageResource(if (todo.done) R.drawable.ic_check_on else R.drawable.ic_check_off)
                imageTintList = ColorStateList.valueOf(
                    if (todo.done) base.withAlpha(0.45f) else base
                )
            }

            val compact = data.layout == WidgetLayoutGeometry.TodoLayout.COMPACT_LIST
            val labels = TodoDateText.labels(
                dueEpochDay = todo.dueEpochDay,
                dueMinuteOfDay = todo.dueMinuteOfDay,
                mode = data.prefs.todoDateDisplayMode,
                timePrecision = data.prefs.todoTimePrecision,
                from = data.today,
                compact = compact
            )
            text(row, R.id.item_deadline).apply {
                val metadata = if (compact) labels.multiline else labels.deadline
                visibility = if (metadata == null) View.GONE else View.VISIBLE
                text = metadata
                setTextColor(badgeColor(todo))
                textSize = WidgetTypography.scaledSize(deadlineSp, percent)
            }
            text(row, R.id.item_badge).apply {
                visibility = if (!compact && labels.remaining != null) View.VISIBLE else View.GONE
                text = labels.remaining
                setTextColor(badgeColor(todo))
                textSize = WidgetTypography.scaledSize(deadlineSp, percent)
            }
        }

        private fun badgeColor(todo: TodoEntity): Int {
            if (todo.done) return base.withAlpha(0.4f)
            val days = todo.dueEpochDay?.let { DayMath.daysForDue(it, data.today) }
                ?: return base
            return WidgetPalette.colorFor(context, DayMath.urgencyOf(days), base)
        }
    }
}
