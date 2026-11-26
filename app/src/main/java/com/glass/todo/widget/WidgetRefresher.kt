package com.glass.todo.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.glass.todo.widget.todo.TodoWidgetProvider
import java.util.concurrent.Executors

/**
 * 组件刷新的统一入口。
 *
 * BroadcastReceiver 里不能做耗时操作，Room 查询也不能放在主线程，
 * 所以异步入口统一走单线程执行器，Receiver 的同步入口再用同一把锁串行化：
 * - 数据读取用 DAO 的同步方法；
 * - 调用方如果是 Receiver，用 goAsync() 拿到 PendingResult，
 *   再把 finish() 交给 [refreshAll] 的回调，确保进程不会在刷新完成前被杀。
 */
object WidgetRefresher {

    private const val TAG = "WidgetRefresher"

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "glass-widget-refresh").apply { isDaemon = true }
    }

    /**
     * 异步刷新和 Receiver 里的同步刷新最终都经过同一把锁。
     * 单线程执行器只能串行化「异步入口」，receiver 自己起的 IO 协程仍可能并发，
     * 所以这里再兜一层，避免旧快照晚于新快照写入桌面。
     */
    private val refreshLock = Any()

    /** 合并刷新等待期内的重复请求，避免系统更新、缩放和 UI 修改把队列塞满。 */
    private val callbackLock = Any()
    private var refreshPending = false
    private var refreshAgain = false
    private val callbacks = mutableListOf<() -> Unit>()

    /**
     * 异步刷新全部组件。UI 侧改完数据后调用这个即可。
     * 等待中的请求直接合并；刷新执行中的请求最多触发一次跟进刷新，
     * 避免请求恰好在数据库快照读取后到达而导致桌面保留旧数据。
     *
     * @param onFinished 在后台线程回调；Receiver 应在此调用 PendingResult.finish()
     */
    @JvmOverloads
    fun refreshAll(context: Context, onFinished: (() -> Unit)? = null) {
        val app = context.applicationContext
        synchronized(callbackLock) {
            onFinished?.let(callbacks::add)
            if (refreshPending) {
                refreshAgain = true
                return
            }
            refreshPending = true
            refreshAgain = false
        }
        executor.execute { drainRefreshRequests(app) }
    }

    private fun drainRefreshRequests(app: Context) {
        while (true) {
            val currentCallbacks = synchronized(callbackLock) {
                callbacks.toList().also { callbacks.clear() }
            }
            try {
                refreshAllBlocking(app)
            } catch (t: Throwable) {
                Log.e(TAG, "刷新组件失败", t)
            }
            currentCallbacks.forEach { callback ->
                try {
                    callback.invoke()
                } catch (t: Throwable) {
                    Log.e(TAG, "刷新回调失败", t)
                }
            }

            val shouldRunAgain = synchronized(callbackLock) {
                if (refreshAgain) {
                    refreshAgain = false
                    true
                } else {
                    refreshPending = false
                    false
                }
            }
            if (!shouldRunAgain) return
        }
    }

    /**
     * 同步刷新全部组件。**只能在后台线程调用**，因为内部会阻塞读数据库。
     * 协程里请包在 `withContext(Dispatchers.IO)` 中。
     */
    fun refreshAllBlocking(context: Context) = synchronized(refreshLock) {
        val app = context.applicationContext
        val awm = app.getSystemService(AppWidgetManager::class.java) ?: return@synchronized
        try {
            if (hasWidgets(awm, app, TodoWidgetProvider::class.java)) {
                TodoWidgetProvider.updateAllBlocking(app, awm)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "刷新组件失败", t)
        }
    }

    /** 没有任何实例时直接跳过，省掉一次数据库查询 */
    private fun hasWidgets(
        awm: AppWidgetManager,
        context: Context,
        provider: Class<*>
    ): Boolean = awm.getAppWidgetIds(ComponentName(context, provider)).isNotEmpty()
}
