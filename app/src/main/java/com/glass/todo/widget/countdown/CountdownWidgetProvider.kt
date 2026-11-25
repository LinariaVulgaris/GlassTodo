package com.glass.todo.widget.countdown

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.glass.todo.R
import com.glass.todo.data.EventEntity
import com.glass.todo.data.Prefs
import com.glass.todo.data.Repository
import com.glass.todo.data.WidgetLayoutMode
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
import kotlin.math.abs

/**
 * 倒数日组件：透明背景直接叠在壁纸上，白色大字显示「还有 N 天」。
 *
 * 外观刻意做成系统时间/天气组件那种「无底板」样式；
 * 用户可在设置里选择静态玻璃底板，或为单个组件应用截图校准的液态玻璃背景。
 */
class CountdownWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 读数与渲染都要离开主线程，交给统一刷新器；完成后才释放 goAsync 的额度
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
        // 桌面拖动缩放手柄时立即重算横向/竖向布局，不必等下一次定时刷新。
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

        /** 根布局用 @android:id/background，Android 12+ 会自动做圆角裁剪 */
        private val ROOT_ID = android.R.id.background

        /** 只能在后台线程调用 */
        fun updateAllBlocking(context: Context, awm: AppWidgetManager) {
            val ids = awm.getAppWidgetIds(
                ComponentName(context, CountdownWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return

            val repo = Repository.get(context)
            val events = repo.events.getActiveSync()
            val today = DayMath.today()
            val ordered = ordered(events, today)

            ids.forEach { id ->
                val options = awm.getAppWidgetOptions(id)
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 110)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
                val mode = WidgetLayoutGeometry.resolve(
                    width,
                    height,
                    repo.prefs.widgetLayoutMode(id)
                )
                awm.updateAppWidget(
                    id,
                    render(context, repo.prefs, id, ordered, today, mode, width, height)
                )
            }
        }

        /**
         * 点「下一个」按钮时轮换事件。
         * 选择结果按 appWidgetId 单独记录，所以桌面上放多个倒数日组件也不会互相干扰。
         */
        fun cycleToNextEvent(context: Context, widgetId: Int) {
            val repo = Repository.get(context)
            val ordered = ordered(repo.events.getActiveSync(), DayMath.today())
            if (ordered.isEmpty()) {
                repo.prefs.setSelectedEventId(widgetId, Prefs.AUTO_EVENT)
                return
            }
            if (ordered.size == 1) {
                repo.prefs.setSelectedEventId(widgetId, ordered[0].id)
                return
            }
            val currentId = repo.prefs.selectedEventId(widgetId)
            val index = ordered.indexOfFirst { it.id == currentId }
            // index 为 -1（自动模式）时，(−1 + 1) mod size = 0，正好落到第一个
            val next = ordered[(index + 1).mod(ordered.size)]
            repo.prefs.setSelectedEventId(widgetId, next.id)
        }

        /**
         * 候选事件的稳定顺序：未到的按「还有多久」升序排在前，
         * 已过去的排在后并按最近优先。轮换时视觉上是连贯的。
         */
        private fun ordered(events: List<EventEntity>, today: LocalDate): List<EventEntity> {
            if (events.size < 2) return events
            val days = HashMap<Long, Long>(events.size)
            events.forEach { days[it.id] = DayMath.daysFor(it, today) }
            return events.sortedWith(
                compareBy<EventEntity> { if ((days[it.id] ?: 0L) < 0L) 1 else 0 }
                    .thenBy { abs(days[it.id] ?: 0L) }
                    .thenBy { it.title }
            )
        }

        private fun render(
            context: Context,
            prefs: Prefs,
            widgetId: Int,
            ordered: List<EventEntity>,
            today: LocalDate,
            layoutMode: WidgetLayoutMode,
            widthDp: Int,
            heightDp: Int
        ): RemoteViews {
            val layout = if (layoutMode == WidgetLayoutMode.WIDE) {
                R.layout.widget_countdown_wide
            } else {
                R.layout.widget_countdown
            }
            val views = RemoteViews(context.packageName, layout)
            val base = prefs.widgetTextColor

            WidgetGlassBackground.apply(context, views, prefs, widgetId)
            WidgetTypography.apply(
                views,
                prefs.widgetFontScalePercent,
                R.id.cd_title to 14f,
                R.id.cd_prefix to 13f,
                R.id.cd_suffix to 13f,
                R.id.cd_deadline to if (layoutMode == WidgetLayoutMode.WIDE) 20f else 18f,
                R.id.cd_repeat to 11f,
                R.id.cd_empty to 13f
            )

            views.setTextColor(R.id.cd_title, base)
            views.setTextColor(R.id.cd_prefix, base.withAlpha(0.75f))
            views.setTextColor(R.id.cd_suffix, base.withAlpha(0.75f))
            views.setTextColor(R.id.cd_deadline, base)
            views.setTextColor(R.id.cd_repeat, base.withAlpha(0.6f))
            views.setTextColor(R.id.cd_empty, base.withAlpha(0.85f))
            views.setOnClickPendingIntent(ROOT_ID, WidgetIntent.openApp(context, widgetId * 100 + 1))

            if (ordered.isEmpty()) {
                views.setViewVisibility(R.id.cd_content, View.GONE)
                views.setViewVisibility(R.id.cd_empty, View.VISIBLE)
                views.setTextViewText(R.id.cd_empty, context.getString(R.string.widget_countdown_empty))
                return views
            }

            views.setViewVisibility(R.id.cd_content, View.VISIBLE)
            views.setViewVisibility(R.id.cd_empty, View.GONE)

            val selectedId = prefs.selectedEventId(widgetId)
            val event = ordered.firstOrNull { it.id == selectedId } ?: ordered.first()
            val display = DayMath.display(event, today)
            // 逾期模式的负号改由左侧「已过」表达，数字本身只展示天数。
            val negative = display.number.startsWith("-")
            val number = if (negative) display.number.removePrefix("-") else display.number
            val prefixRes = when {
                negative -> R.string.widget_countdown_prefix_overdue
                display.isCountUp -> R.string.widget_countdown_prefix_elapsed
                else -> R.string.widget_countdown_prefix_remaining
            }

            views.setTextViewText(R.id.cd_title, event.title)
            views.setTextViewText(R.id.cd_prefix, context.getString(prefixRes))
            views.setTextViewText(R.id.cd_number, number)
            views.setTextViewText(R.id.cd_suffix, context.getString(R.string.widget_countdown_suffix_days))
            views.setTextViewTextSize(
                R.id.cd_number,
                android.util.TypedValue.COMPLEX_UNIT_SP,
                WidgetTypography.countdownNumberSize(
                    number,
                    layoutMode,
                    widthDp,
                    heightDp,
                    prefs.widgetFontScalePercent
                )
            )
            views.setTextColor(
                R.id.cd_number,
                WidgetPalette.colorFor(context, display.urgency, base)
            )

            val detailedMinute = event.targetMinuteOfDay?.let { DateText.normalizeMinuteOfDay(it) }
            if (detailedMinute != null) {
                views.setViewVisibility(R.id.cd_days_row, View.GONE)
                views.setViewVisibility(R.id.cd_deadline, View.VISIBLE)
                views.setTextViewText(
                    R.id.cd_deadline,
                    DateText.formatExactDateTime(
                        DayMath.nextOccurrence(event, today).toEpochDay(),
                        detailedMinute,
                        today,
                        multiline = true
                    )
                )
                views.setTextColor(
                    R.id.cd_deadline,
                    WidgetPalette.colorFor(context, display.urgency, base)
                )
            } else {
                views.setViewVisibility(R.id.cd_days_row, View.VISIBLE)
                views.setViewVisibility(R.id.cd_deadline, View.GONE)
            }

            val repeatLabel = repeatLabel(context, event)
            if (repeatLabel == null) {
                views.setViewVisibility(R.id.cd_repeat, View.GONE)
            } else {
                views.setViewVisibility(R.id.cd_repeat, View.VISIBLE)
                views.setTextViewText(R.id.cd_repeat, repeatLabel)
            }

            // 只有一个事件时隐藏切换按钮，避免出现按了没反应的死按钮
            views.setViewVisibility(
                R.id.cd_next,
                if (ordered.size > 1) View.VISIBLE else View.GONE
            )
            if (ordered.size > 1) {
                views.setOnClickPendingIntent(R.id.cd_next, WidgetIntent.cycleEvent(context, widgetId))
            }
            return views
        }

        private fun repeatLabel(context: Context, event: EventEntity): String? = when (event.repeat) {
            com.glass.todo.data.RepeatRule.WEEKLY -> context.getString(R.string.repeat_weekly_short)
            com.glass.todo.data.RepeatRule.MONTHLY -> context.getString(R.string.repeat_monthly_short)
            com.glass.todo.data.RepeatRule.YEARLY -> context.getString(R.string.repeat_yearly_short)
            else -> null
        }
    }
}
