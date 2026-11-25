package com.glass.todo.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.glass.todo.util.DayMath
import com.glass.todo.widget.countdown.CountdownWidgetProvider
import com.glass.todo.widget.todo.TodoWidgetProvider

/**
 * 倒数天数只有在跨日时才需要变化，因此不用高频轮询。
 *
 * 三重保障，缺一不可：
 * 1. 每天 00:05 触发一次全量刷新。用 RTC_WAKEUP + 非精确重复闹钟，
 *    设备睡在零点时也能醒来刷新，同时不需要精确闹钟权限；
 * 2. SystemTimeReceiver 监听系统日期 / 时间 / 时区变化，兜住用户改时间和跨时区；
 * 3. appwidget-provider 的 updatePeriodMillis 以 30 分钟兜底。
 *
 * 另外，BootReceiver 在开机和应用更新后会重新登记；
 * [sync] 则保证两种组件全部移除后取消闹钟，不做无意义的每日唤醒。
 */
object UpdateScheduler {

    /** 按当前是否还存在组件实例，登记或取消每日闹钟。可在主线程短调用。 */
    fun sync(context: Context) {
        val app = context.applicationContext
        val awm = app.getSystemService(AppWidgetManager::class.java)
        val hasWidgets = awm != null && listOf(
            CountdownWidgetProvider::class.java,
            TodoWidgetProvider::class.java
        ).any { provider ->
            awm.getAppWidgetIds(ComponentName(app, provider)).isNotEmpty()
        }
        if (hasWidgets) scheduleDaily(app) else cancel(app)
    }

    fun scheduleDaily(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        am.setInexactRepeating(
            // RTC 不会唤醒睡眠中的设备；这里必须用 RTC_WAKEUP，
            // 否则「00:05 刷新」可能拖到第二天早上用户首次亮屏才发生。
            AlarmManager.RTC_WAKEUP,
            DayMath.nextDailyTriggerMillis(),
            AlarmManager.INTERVAL_DAY,
            dailyPendingIntent(app)
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(dailyPendingIntent(context))
    }

    fun dailyPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailyRefreshReceiver::class.java).apply {
            action = DailyRefreshReceiver.ACTION_DAILY_REFRESH
        }
        // Android 12+ 要求显式声明可变性
        return PendingIntent.getBroadcast(
            context,
            REQUEST_DAILY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val REQUEST_DAILY = 1001
}
