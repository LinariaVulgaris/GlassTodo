package com.glass.todo.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.glass.todo.ui.MainActivity

/**
 * 组件里所有 PendingIntent 的构造出口，集中处理 requestCode 与可变性标志。
 *
 * 两个容易踩的点：
 * 1. **fill-in 必须用 FLAG_MUTABLE**。列表组件用 setPendingIntentTemplate + setOnClickFillInIntent，
 *    系统需要把每一行的 extra 合并进模板 PendingIntent，模板若是 IMMUTABLE 则合并无效，
 *    表现是「点了没反应」且不报错。
 * 2. **可变 PendingIntent 必须使用显式 Intent**。Android 14 起，用隐式 Intent 创建
 *    mutable PendingIntent 会直接抛异常。这里的模板 Intent 都显式指向
 *    [WidgetActionReceiver]，因此安全。
 *
 * requestCode 用 widgetId 派生，避免同一桌面上多个组件实例互相覆盖 PendingIntent。
 */
object WidgetIntent {

    const val ACTION_TOGGLE_TODO = "com.glass.todo.widget.action.TOGGLE_TODO"
    const val ACTION_OPEN_ADD = "com.glass.todo.widget.action.OPEN_ADD"

    const val EXTRA_TODO_ID = "extra_todo_id"
    const val EXTRA_WIDGET_ID = "extra_widget_id"

    /** 点击组件空白处打开 App */
    fun openApp(context: Context, requestCode: Int, openAddSheet: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (openAddSheet) action = ACTION_OPEN_ADD
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 列表项点击的模板：切换待办完成状态。
     * 具体是哪一条由 [com.glass.todo.widget.todo.TodoWidgetProvider] 的行视图
     * 通过 fillInIntent 补上 id。
     */
    fun toggleTodoTemplate(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = ACTION_TOGGLE_TODO
            putExtra(EXTRA_WIDGET_ID, widgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            widgetId * 100 + 11,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /** 组件标题栏的「+」，直接跳到 App 的新建待办 */
    fun openAddTodo(context: Context, widgetId: Int): PendingIntent =
        openApp(context, widgetId * 100 + 13, openAddSheet = true)
}
