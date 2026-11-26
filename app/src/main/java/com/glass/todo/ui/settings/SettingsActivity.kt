package com.glass.todo.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.glass.todo.R
import com.glass.todo.data.Prefs
import com.glass.todo.data.Repository
import com.glass.todo.data.TodoDateDisplayMode
import com.glass.todo.data.TodoTimePrecision
import com.glass.todo.databinding.ActivitySettingsBinding
import com.glass.todo.ui.background.WidgetBackgroundActivity
import com.glass.todo.widget.WidgetRefresher
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch

/**
 * 组件外观与行为设置。
 *
 * 刻意没有用 PreferenceFragment：这个页面选项很少，
 * 直接用 Material 控件既省一个依赖，也方便每项变更后立刻刷新桌面组件。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repo: Repository
    private lateinit var prefs: Prefs
    private var applyingColorSelection = false
    private var applyingTodoDateSelection = false
    private var applyingTodoTimeSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // targetSdk 35+ 强制边到边；显式避开系统栏与刘海。
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repo = Repository.get(this)
        prefs = repo.prefs

        bindTextColor()
        bindWidgetFontSize()
        bindGlassPanel()
        bindWidgetBackground()
        bindMaxItems()
        bindShowDone()
        bindTodoDateDisplay()
        bindClearDone()
        bindProjectLink()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ---------- 文字颜色 ----------

    private val colorOptions: List<Triple<Int, Int, Int>> by lazy {
        listOf(
            Triple(R.id.btn_color_white, R.color.widget_text_white, R.string.color_white),
            Triple(R.id.btn_color_warm, R.color.widget_text_warm, R.string.color_warm),
            Triple(R.id.btn_color_cool, R.color.widget_text_cool, R.string.color_cool),
            Triple(R.id.btn_color_mint, R.color.widget_text_mint, R.string.color_mint)
        )
    }

    private fun bindTextColor() {
        renderTextColorSelection()
        binding.colorGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (applyingColorSelection || !isChecked) return@addOnButtonCheckedListener
            val option = colorOptions.firstOrNull { it.first == checkedId } ?: return@addOnButtonCheckedListener
            prefs.widgetTextColor = resolve(option.second)
            renderTextColorSelection()
            refreshWidgets()
        }
        binding.btnColorCustom.setOnClickListener {
            WidgetColorPickerDialog(
                this,
                prefs.widgetCustomTextColor,
                previewMode = WidgetColorPickerDialog.PreviewMode.TEXT
            ) { color ->
                prefs.widgetCustomTextColor = color
                prefs.widgetTextColor = color
                renderTextColorSelection()
                refreshWidgets()
            }.show()
        }
    }

    private fun renderTextColorSelection() {
        val current = prefs.widgetTextColor
        val match = colorOptions.firstOrNull { resolve(it.second) == current }
        applyingColorSelection = true
        binding.colorGroup.clearChecked()
        match?.let { binding.colorGroup.check(it.first) }
        applyingColorSelection = false

        val label = match?.let { getString(it.third) } ?: getString(R.string.color_custom)
        binding.textColorValue.text = getString(
            R.string.color_current_value,
            label,
            current and 0xFFFFFF
        )
    }

    private fun bindWidgetFontSize() {
        renderFontSizeLabel(prefs.widgetFontScalePercent)
        binding.sliderWidgetFontSize.value = prefs.widgetFontScalePercent.toFloat()
        binding.sliderWidgetFontSize.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val percent = value.toInt()
            renderFontSizeLabel(percent)
            prefs.widgetFontScalePercent = percent
        }
        binding.sliderWidgetFontSize.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit

            override fun onStopTrackingTouch(slider: Slider) {
                refreshWidgets()
            }
        })
    }

    private fun renderFontSizeLabel(percent: Int) {
        binding.textFontSizeValue.text = getString(R.string.widget_font_size_value, percent)
    }

    private fun resolve(colorRes: Int): Int = ContextCompat.getColor(this, colorRes)

    // ---------- 其余开关 ----------

    private fun bindGlassPanel() {
        binding.switchGlass.isChecked = prefs.glassPanelEnabled
        binding.switchGlass.setOnCheckedChangeListener { _, checked ->
            prefs.glassPanelEnabled = checked
            refreshWidgets()
        }
    }

    private fun bindWidgetBackground() {
        binding.btnWidgetBackground.setOnClickListener {
            startActivity(Intent(this, WidgetBackgroundActivity::class.java))
        }
    }

    private fun bindMaxItems() {
        renderMaxItemsLabel(prefs.maxTodoItems)
        binding.sliderMaxItems.value = prefs.maxTodoItems.toFloat()
        binding.sliderMaxItems.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val v = value.toInt()
            renderMaxItemsLabel(v)
            prefs.maxTodoItems = v
        }
        // 拖动过程中每移动 1 都会触发 change；只在松手时刷新一次组件，
        // 避免往单线程刷新队列里塞进十几份重复任务。
        binding.sliderMaxItems.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit

            override fun onStopTrackingTouch(slider: Slider) {
                refreshWidgets()
            }
        })
    }

    private fun renderMaxItemsLabel(value: Int) {
        binding.textMaxItemsValue.text = getString(R.string.max_items_value, value)
    }

    private fun bindShowDone() {
        binding.switchShowDone.isChecked = prefs.showDoneInWidget
        binding.switchShowDone.setOnCheckedChangeListener { _, checked ->
            prefs.showDoneInWidget = checked
            refreshWidgets()
        }
    }

    private fun bindTodoDateDisplay() {
        applyingTodoDateSelection = true
        binding.todoDateModeGroup.check(
            when (prefs.todoDateDisplayMode) {
                TodoDateDisplayMode.DDL -> R.id.btn_todo_date_ddl
                TodoDateDisplayMode.DAYS -> R.id.btn_todo_date_days
                TodoDateDisplayMode.BOTH -> R.id.btn_todo_date_both
            }
        )
        applyingTodoDateSelection = false
        binding.todoDateModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (applyingTodoDateSelection || !isChecked) return@addOnButtonCheckedListener
            prefs.todoDateDisplayMode = when (checkedId) {
                R.id.btn_todo_date_ddl -> TodoDateDisplayMode.DDL
                R.id.btn_todo_date_days -> TodoDateDisplayMode.DAYS
                else -> TodoDateDisplayMode.BOTH
            }
            refreshWidgets()
        }

        applyingTodoTimeSelection = true
        binding.todoTimePrecisionGroup.check(
            when (prefs.todoTimePrecision) {
                TodoTimePrecision.NONE -> R.id.btn_todo_time_none
                TodoTimePrecision.HOUR -> R.id.btn_todo_time_hour
                TodoTimePrecision.MINUTE -> R.id.btn_todo_time_minute
            }
        )
        applyingTodoTimeSelection = false
        binding.todoTimePrecisionGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (applyingTodoTimeSelection || !isChecked) return@addOnButtonCheckedListener
            prefs.todoTimePrecision = when (checkedId) {
                R.id.btn_todo_time_none -> TodoTimePrecision.NONE
                R.id.btn_todo_time_hour -> TodoTimePrecision.HOUR
                else -> TodoTimePrecision.MINUTE
            }
            refreshWidgets()
        }
    }

    private fun bindClearDone() {
        binding.btnClearDone.setOnClickListener {
            lifecycleScope.launch {
                repo.todos.clearAllDone()
                refreshWidgets()
                Snackbar.make(binding.root, R.string.clear_done_done, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindProjectLink() {
        binding.settingsGithubLink.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    getString(R.string.project_github_url).toUri()
                )
            )
        }
    }

    /** 任何设置变更后都必须推一次，桌面组件不会自己感知 SharedPreferences 的变化 */
    private fun refreshWidgets() = WidgetRefresher.refreshAll(this)
}
