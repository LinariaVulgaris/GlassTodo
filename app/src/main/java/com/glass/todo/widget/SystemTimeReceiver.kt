package com.glass.todo.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 用户手动改系统时间、跨时区、或自然跨日时，系统会发出这些广播。
 * 这是每日闹钟之外的第二重保障。
 */
class SystemTimeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // 时区变化后，下一次「零点」的时刻也变了，需要重排闹钟
                UpdateScheduler.sync(context)
                val pending = goAsync()
                WidgetRefresher.refreshAll(context) { pending.finish() }
            }
        }
    }
}
