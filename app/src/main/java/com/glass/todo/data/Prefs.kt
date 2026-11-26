package com.glass.todo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 组件外观与行为设置。
 *
 * 全部写入同一个 SharedPreferences，用 apply() 而非 commit()。
 * apply() 会先同步更新内存中的值，后续组件刷新立刻就能读到；
 * 磁盘写入则交给系统后台队列，避免在设置页主线程上做同步 IO。
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    init {
        // 清理旧倒数日组件按实例保存的选中事件；其余待办与外观设置全部保留。
        val legacyEventKeys = sp.all.keys.filter { it.startsWith("widget_event_") }
        if (legacyEventKeys.isNotEmpty()) {
            sp.edit { legacyEventKeys.forEach(::remove) }
        }
    }

    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.unregisterOnSharedPreferenceChangeListener(listener)
    }

    /** 组件文字颜色，默认纯白 */
    var widgetTextColor: Int
        get() = sp.getInt(KEY_TEXT_COLOR, DEFAULT_TEXT_COLOR)
        set(value) {
            sp.edit { putInt(KEY_TEXT_COLOR, value) }
        }

    /** 自定义颜色槽位；选择预设后仍保留，下次打开颜色选择器可继续编辑。 */
    var widgetCustomTextColor: Int
        get() = sp.getInt(KEY_CUSTOM_TEXT_COLOR, DEFAULT_TEXT_COLOR) or 0xFF000000.toInt()
        set(value) {
            sp.edit { putInt(KEY_CUSTOM_TEXT_COLOR, value or 0xFF000000.toInt()) }
        }

    /** 组件文字大小倍率，100 表示保持原始设计字号。 */
    var widgetFontScalePercent: Int
        get() = sp.getInt(KEY_FONT_SCALE_PERCENT, DEFAULT_FONT_SCALE_PERCENT)
            .coerceIn(WIDGET_FONT_SCALE_MIN, WIDGET_FONT_SCALE_MAX)
        set(value) {
            sp.edit {
                putInt(KEY_FONT_SCALE_PERCENT, value.coerceIn(WIDGET_FONT_SCALE_MIN, WIDGET_FONT_SCALE_MAX))
            }
        }

    /** 是否在文字后面垫一层半透明玻璃底板，提升浅色壁纸下的可读性 */
    var glassPanelEnabled: Boolean
        get() = sp.getBoolean(KEY_GLASS_PANEL, false)
        set(value) {
            sp.edit { putBoolean(KEY_GLASS_PANEL, value) }
        }

    /** 待办组件最多显示几条 */
    var maxTodoItems: Int
        get() = sp.getInt(KEY_MAX_ITEMS, DEFAULT_MAX_ITEMS).coerceIn(1, 20)
        set(value) {
            sp.edit { putInt(KEY_MAX_ITEMS, value.coerceIn(1, 20)) }
        }

    /** 待办组件是否显示已完成项（置灰） */
    var showDoneInWidget: Boolean
        get() = sp.getBoolean(KEY_SHOW_DONE, false)
        set(value) {
            sp.edit { putBoolean(KEY_SHOW_DONE, value) }
        }

    /** 待办日期区域显示 DDL、剩余天数，或两者同时显示。 */
    var todoDateDisplayMode: TodoDateDisplayMode
        get() = sp.getString(KEY_TODO_DATE_DISPLAY_MODE, null)
            ?.let { stored -> TodoDateDisplayMode.entries.firstOrNull { it.name == stored } }
            ?: TodoDateDisplayMode.BOTH
        set(value) {
            sp.edit { putString(KEY_TODO_DATE_DISPLAY_MODE, value.name) }
        }

    /** 待办日期区域的时间精度；仅对已设置具体时间的待办生效。 */
    var todoTimePrecision: TodoTimePrecision
        get() = sp.getString(KEY_TODO_TIME_PRECISION, null)
            ?.let { stored -> TodoTimePrecision.entries.firstOrNull { it.name == stored } }
            ?: TodoTimePrecision.MINUTE
        set(value) {
            sp.edit { putString(KEY_TODO_TIME_PRECISION, value.name) }
        }

    /**
     * 布局设置入口已移除，运行时始终跟随桌面报告的组件形状。
     * 旧版本写下的固定预设不再读取，升级后也会立即恢复自动切换。
     */
    fun widgetLayoutMode(appWidgetId: Int): WidgetLayoutMode = WidgetLayoutMode.AUTO

    /** 仅保留给已编译但不再暴露的旧布局页面，避免破坏旧调用方。 */
    fun saveWidgetLayoutMode(appWidgetId: Int, mode: WidgetLayoutMode) {
        sp.edit { putString(KEY_LAYOUT_PREFIX + appWidgetId, mode.name) }
    }

    /** 读取组件背景；旧版已启用的截图校准图片迁移为普通图片背景。 */
    fun widgetBackgroundConfig(appWidgetId: Int): WidgetBackgroundConfig {
        val prefix = KEY_BACKGROUND_PREFIX + appWidgetId + "_"
        val storedType = sp.getString(prefix + "type", null)
        if (storedType != null) {
            val type = WidgetBackgroundType.entries.firstOrNull { it.name == storedType }
                ?: WidgetBackgroundType.NONE
            val crop = GlassCrop(
                left = sp.getFloat(prefix + "crop_left", 0f),
                top = sp.getFloat(prefix + "crop_top", 0f),
                right = sp.getFloat(prefix + "crop_right", 1f),
                bottom = sp.getFloat(prefix + "crop_bottom", 1f)
            ).normalized()
            return WidgetBackgroundConfig(
                type = type,
                color = (sp.getInt(prefix + "color", WidgetBackgroundConfig.DEFAULT_COLOR)
                    or 0xFF000000.toInt()),
                sourceFileName = sp.getString(prefix + "source", null),
                crop = crop.takeIf { it.isValid }
            )
        }

        val legacy = widgetGlassConfig(appWidgetId)
        return if (legacy.isUsable) {
            WidgetBackgroundConfig(
                type = WidgetBackgroundType.IMAGE,
                sourceFileName = legacy.sourceFileName,
                crop = legacy.crop
            )
        } else {
            WidgetBackgroundConfig()
        }
    }

    fun saveWidgetBackgroundConfig(appWidgetId: Int, config: WidgetBackgroundConfig) {
        val prefix = KEY_BACKGROUND_PREFIX + appWidgetId + "_"
        val crop = config.crop?.normalized()
        sp.edit {
            putString(prefix + "type", config.type.name)
            putInt(prefix + "color", config.color or 0xFF000000.toInt())
            putString(prefix + "source", config.sourceFileName)
            if (crop == null) {
                remove(prefix + "crop_left")
                remove(prefix + "crop_top")
                remove(prefix + "crop_right")
                remove(prefix + "crop_bottom")
            } else {
                putFloat(prefix + "crop_left", crop.left)
                putFloat(prefix + "crop_top", crop.top)
                putFloat(prefix + "crop_right", crop.right)
                putFloat(prefix + "crop_bottom", crop.bottom)
            }
        }
    }

    /** 读取某个组件的液态玻璃配置；没有配置时默认关闭，不影响原有透明背景。 */
    fun widgetGlassConfig(appWidgetId: Int): WidgetGlassConfig {
        val prefix = KEY_GLASS_PREFIX + appWidgetId + "_"
        val crop = GlassCrop(
            left = sp.getFloat(prefix + "crop_left", 0f),
            top = sp.getFloat(prefix + "crop_top", 0f),
            right = sp.getFloat(prefix + "crop_right", 1f),
            bottom = sp.getFloat(prefix + "crop_bottom", 1f)
        ).normalized()
        return WidgetGlassConfig(
            enabled = sp.getBoolean(prefix + "enabled", false),
            sourceFileName = sp.getString(prefix + "source", null),
            crop = crop.takeIf { it.isValid },
            strength = sp.getFloat(prefix + "strength", WidgetGlassConfig.DEFAULT_STRENGTH)
                .coerceIn(0f, 1f)
        )
    }

    fun saveWidgetGlassConfig(appWidgetId: Int, config: WidgetGlassConfig) {
        val prefix = KEY_GLASS_PREFIX + appWidgetId + "_"
        val crop = config.crop?.normalized()
        sp.edit {
            putBoolean(prefix + "enabled", config.enabled)
            putString(prefix + "source", config.sourceFileName)
            putFloat(prefix + "strength", config.strength.coerceIn(0f, 1f))
            if (crop == null) {
                remove(prefix + "crop_left")
                remove(prefix + "crop_top")
                remove(prefix + "crop_right")
                remove(prefix + "crop_bottom")
            } else {
                putFloat(prefix + "crop_left", crop.left)
                putFloat(prefix + "crop_top", crop.top)
                putFloat(prefix + "crop_right", crop.right)
                putFloat(prefix + "crop_bottom", crop.bottom)
            }
        }
    }

    /** 组件被移除时清理它专属的设置、背景参数和旧版玻璃参数，避免 key 无限堆积。 */
    fun forgetWidget(appWidgetId: Int) {
        val glassPrefix = KEY_GLASS_PREFIX + appWidgetId + "_"
        val backgroundPrefix = KEY_BACKGROUND_PREFIX + appWidgetId + "_"
        sp.edit {
            remove(KEY_LAYOUT_PREFIX + appWidgetId)
            listOf(
                "enabled", "source", "strength", "crop_left", "crop_top", "crop_right", "crop_bottom"
            ).forEach { remove(glassPrefix + it) }
            listOf(
                "type", "color", "source", "crop_left", "crop_top", "crop_right", "crop_bottom"
            ).forEach { remove(backgroundPrefix + it) }
        }
    }

    companion object {
        private const val FILE = "glass_todo_prefs"
        private const val KEY_TEXT_COLOR = "widget_text_color"
        private const val KEY_CUSTOM_TEXT_COLOR = "widget_custom_text_color"
        private const val KEY_FONT_SCALE_PERCENT = "widget_font_scale_percent"
        private const val KEY_GLASS_PANEL = "glass_panel_enabled"
        private const val KEY_MAX_ITEMS = "max_todo_items"
        private const val KEY_SHOW_DONE = "show_done_in_widget"
        internal const val KEY_TODO_DATE_DISPLAY_MODE = "todo_date_display_mode"
        internal const val KEY_TODO_TIME_PRECISION = "todo_time_precision"
        private const val KEY_LAYOUT_PREFIX = "widget_layout_"
        private const val KEY_GLASS_PREFIX = "widget_glass_"
        private const val KEY_BACKGROUND_PREFIX = "widget_background_"

        const val DEFAULT_TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val DEFAULT_FONT_SCALE_PERCENT = 100
        const val WIDGET_FONT_SCALE_MIN = 80
        const val WIDGET_FONT_SCALE_MAX = 140
        const val DEFAULT_MAX_ITEMS = 6
    }
}
