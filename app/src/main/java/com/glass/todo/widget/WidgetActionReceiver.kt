package com.glass.todo.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.glass.todo.data.Repository
import com.glass.todo.widget.countdown.CountdownWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 处理组件上的点击动作：勾选待办、切换倒数日事件。
 *
 * 这些动作都要写数据库，而 BroadcastReceiver 不能阻塞主线程，
 * 所以统一用 goAsync() + IO 协程：先在协程里改数据，再刷新组件，最后 finish()。
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetIntent.ACTION_TOGGLE_TODO -> handleToggle(context, intent)
            WidgetIntent.ACTION_CYCLE_EVENT -> handleCycleEvent(context, intent)
        }
    }

    private fun handleToggle(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra(WidgetIntent.EXTRA_TODO_ID, -1L)
        if (todoId < 0L) {
            Log.w(TAG, "切换待办失败：缺少 todoId")
            return
        }
        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                val repo = Repository.get(app)
                withContext(Dispatchers.IO) {
                    // 单条 SQL 原子切换，避免组件与 App 同时点击时发生丢失更新。
                    val updated = repo.todos.toggleDone(todoId)
                    if (updated == 0) {
                        Log.w(TAG, "待办 $todoId 已不存在，跳过")
                    } else {
                        WidgetRefresher.refreshAllBlocking(app)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "切换待办状态失败", t)
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleCycleEvent(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(
            WidgetIntent.EXTRA_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    CountdownWidgetProvider.cycleToNextEvent(app, widgetId)
                    WidgetRefresher.refreshAllBlocking(app)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "切换倒数日事件失败", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "WidgetActionReceiver"

        /** Receiver 生命周期极短，用独立作用域而非 GlobalScope，便于统一异常处理 */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
