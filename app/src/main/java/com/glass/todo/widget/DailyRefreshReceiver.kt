package com.glass.todo.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 每天 00:05 由 [UpdateScheduler] 的闹钟触发，重算所有组件上的倒数天数 */
class DailyRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DAILY_REFRESH) return
        val pending = goAsync()
        WidgetRefresher.refreshAll(context) { pending.finish() }
    }

    companion object {
        const val ACTION_DAILY_REFRESH = "com.glass.todo.widget.action.DAILY_REFRESH"
    }
}
