package com.glass.todo.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机和应用更新后补登记每日闹钟，并立即刷新一次桌面组件。
 *
 * AlarmManager 的重复闹钟不会跨重启保留；仅靠 App 冷启动时登记是不够的，
 * 因为用户可能开机后一直不打开 App。updatePeriodMillis 虽然能兜底，
 * 但主动刷新能让锁屏 / 桌面在开机后尽快显示正确的日期。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                UpdateScheduler.sync(context)
                val pending = goAsync()
                WidgetRefresher.refreshAll(context) { pending.finish() }
            }
        }
    }
}
