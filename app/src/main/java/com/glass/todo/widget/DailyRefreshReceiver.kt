package com.glass.todo.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 每天 00:05 由 [UpdateScheduler] 的闹钟触发，重算所有组件上的倒数天数 */
class DailyRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DAILY_REFRESH) return
        val app = context.applicationContext
        val pending = goAsync()
        WidgetRefresher.refreshAll(app) {
            // 单次闹钟触发后按当前本地日期重排，避免夏令时切换造成时间漂移。
            try {
                UpdateScheduler.sync(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DAILY_REFRESH = "com.glass.todo.widget.action.DAILY_REFRESH"
    }
}
