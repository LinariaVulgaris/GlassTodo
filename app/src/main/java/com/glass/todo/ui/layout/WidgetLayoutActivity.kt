package com.glass.todo.ui.layout

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.glass.todo.R
import com.glass.todo.data.Repository
import com.glass.todo.data.WidgetLayoutMode
import com.glass.todo.databinding.ActivityWidgetLayoutBinding
import com.glass.todo.util.WidgetLayoutGeometry
import com.glass.todo.widget.WidgetRefresher
import com.glass.todo.widget.WidgetTarget
import com.glass.todo.widget.WidgetTargetCatalog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/** 选择组件并设置横向、竖向、方形或自动布局；实际占格由桌面系统缩放。 */
class WidgetLayoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetLayoutBinding
    private val prefs by lazy { Repository.get(this).prefs }
    private var targets: List<WidgetTarget> = emptyList()
    private var selected: WidgetTarget? = null
    private var restoredId = INVALID_ID

    private val modeButtons = listOf(
        R.id.btn_layout_auto to WidgetLayoutMode.AUTO,
        R.id.btn_layout_wide to WidgetLayoutMode.WIDE,
        R.id.btn_layout_tall to WidgetLayoutMode.TALL,
        R.id.btn_layout_square to WidgetLayoutMode.SQUARE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWidgetLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }
        restoredId = savedInstanceState?.getInt(STATE_WIDGET_ID, INVALID_ID) ?: INVALID_ID
        bindControls()
    }

    override fun onResume() {
        super.onResume()
        // 从桌面返回后重新读取 AppWidgetManager 报告的实际尺寸。
        refreshTargets(announce = false)
    }

    private fun bindControls() {
        binding.btnSelectWidget.setOnClickListener { showTargetPicker() }
        binding.btnRefreshSize.setOnClickListener { refreshTargets(announce = true) }
        installModeListener()
    }

    private fun installModeListener() {
        binding.layoutGroup.clearOnButtonCheckedListeners()
        binding.layoutGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val current = selected ?: return@addOnButtonCheckedListener
            val mode = modeButtons.firstOrNull { it.first == checkedId }?.second ?: return@addOnButtonCheckedListener
            prefs.saveWidgetLayoutMode(current.id, mode)
            renderSelected()
            WidgetRefresher.refreshAll(this)
            Snackbar.make(binding.root, R.string.widget_layout_applied, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun refreshTargets(announce: Boolean) {
        targets = WidgetTargetCatalog.load(this)
        selected = targets.firstOrNull { it.id == selected?.id }
            ?: targets.firstOrNull { it.id == restoredId }
            ?: targets.firstOrNull()
        if (selected == null) {
            showNoWidgets()
        } else {
            restoredId = INVALID_ID
            binding.textNoWidgets.visibility = android.view.View.GONE
            binding.layoutGroup.isEnabled = true
            binding.previewLayout.isEnabled = true
            renderSelected()
        }
        if (announce) {
            Snackbar.make(binding.root, R.string.widget_layout_size_refreshed, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showTargetPicker() {
        if (targets.isEmpty()) {
            Snackbar.make(binding.root, R.string.widget_layout_no_widgets, Snackbar.LENGTH_LONG).show()
            return
        }
        val labels = targets.map { it.label }.toTypedArray()
        val checked = targets.indexOfFirst { it.id == selected?.id }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.widget_layout_choose_title)
            .setSingleChoiceItems(labels, checked) { dialog, index ->
                selected = targets[index]
                renderSelected()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renderSelected() {
        val target = selected ?: return
        val preferred = prefs.widgetLayoutMode(target.id)
        val resolved = WidgetLayoutGeometry.resolve(target.widthDp, target.heightDp, preferred)
        binding.textTarget.text = target.label
        binding.textCurrentSize.text = getString(
            R.string.widget_layout_current_size,
            target.widthDp,
            target.heightDp,
            WidgetLayoutGeometry.aspectRatio(target.widthDp, target.heightDp)
        )
        binding.layoutGroup.clearOnButtonCheckedListeners()
        binding.layoutGroup.check(modeButtons.firstOrNull { it.second == preferred }?.first ?: R.id.btn_layout_auto)
        installModeListener()
        binding.previewLayout.bind(target, resolved)
        val detail = if (target.kind == WidgetTarget.Kind.TODO) {
            val todoLayout = WidgetLayoutGeometry.todoLayout(resolved, target.widthDp)
            val style = when (todoLayout) {
                WidgetLayoutGeometry.TodoLayout.GRID -> R.string.widget_layout_todo_grid_style
                WidgetLayoutGeometry.TodoLayout.COMPACT_LIST -> R.string.widget_layout_todo_compact_style
                WidgetLayoutGeometry.TodoLayout.COMFORT_LIST -> R.string.widget_layout_todo_comfort_style
            }
            getString(
                R.string.widget_layout_todo_columns,
                WidgetLayoutGeometry.todoColumns(resolved, target.widthDp),
                getString(style)
            )
        } else {
            getString(resolved.labelRes())
        }
        binding.textLayoutSummary.text = if (preferred == WidgetLayoutMode.AUTO) {
            getString(R.string.widget_layout_summary_auto, detail)
        } else {
            getString(R.string.widget_layout_summary_fixed, detail)
        }
    }

    private fun showNoWidgets() {
        selected = null
        restoredId = INVALID_ID
        binding.textTarget.setText(R.string.widget_layout_no_widgets)
        binding.textCurrentSize.setText(R.string.widget_layout_no_widgets)
        binding.layoutGroup.clearOnButtonCheckedListeners()
        binding.layoutGroup.isEnabled = false
        binding.previewLayout.bind(null, WidgetLayoutMode.AUTO)
        binding.previewLayout.isEnabled = false
        binding.textLayoutSummary.text = ""
        binding.textNoWidgets.visibility = android.view.View.VISIBLE
        // 无组件时仍保留刷新按钮，用户添加组件后不必重进页面。
    }

    private fun WidgetLayoutMode.labelRes(): Int = when (this) {
        WidgetLayoutMode.AUTO -> R.string.widget_layout_auto
        WidgetLayoutMode.WIDE -> R.string.widget_layout_wide
        WidgetLayoutMode.TALL -> R.string.widget_layout_tall
        WidgetLayoutMode.SQUARE -> R.string.widget_layout_square
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_WIDGET_ID, selected?.id ?: INVALID_ID)
    }

    private companion object {
        const val INVALID_ID = -1
        const val STATE_WIDGET_ID = "state_widget_id"
    }
}
