package com.glass.todo.ui.background

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.glass.todo.R
import com.glass.todo.data.GlassCrop
import com.glass.todo.data.Repository
import com.glass.todo.data.WidgetBackgroundConfig
import com.glass.todo.data.WidgetBackgroundType
import com.glass.todo.databinding.ActivityWidgetBackgroundBinding
import com.glass.todo.ui.settings.WidgetColorPickerDialog
import com.glass.todo.util.WidgetBackgroundRenderer
import com.glass.todo.util.WidgetLayoutGeometry
import com.glass.todo.widget.WidgetRefresher
import com.glass.todo.widget.WidgetTarget
import com.glass.todo.widget.WidgetTargetCatalog
import com.glass.todo.widget.background.WidgetBackgroundStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 为某个已添加的桌面组件分别设置默认、图片或纯色背景。 */
class WidgetBackgroundActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetBackgroundBinding
    private val prefs by lazy { Repository.get(this).prefs }
    private var targets: List<WidgetTarget> = emptyList()
    private var selected: WidgetTarget? = null
    private var confirmedSize: Pair<Int, Int>? = null
    private var config = WidgetBackgroundConfig()
    private var sourceBitmap: Bitmap? = null
    private var operationJob: Job? = null
    private var busy = false
    private var applyingTypeSelection = false

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) importImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWidgetBackgroundBinding.inflate(layoutInflater)
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

        bindControls()
        targets = WidgetTargetCatalog.load(this)
        val restoredId = savedInstanceState?.getInt(STATE_WIDGET_ID, INVALID_ID)
            ?.takeIf { it != INVALID_ID }
        val restoredCrop = savedInstanceState?.getFloatArray(STATE_CROP)?.let { values ->
            if (values.size == 4) GlassCrop(values[0], values[1], values[2], values[3]) else null
        }
        val restoredType = savedInstanceState?.getString(STATE_TYPE)
            ?.let { name -> WidgetBackgroundType.entries.firstOrNull { it.name == name } }
        val restoredColor = savedInstanceState?.getInt(STATE_COLOR, config.color) ?: config.color
        val restoredWidth = savedInstanceState?.getInt(STATE_CONFIRMED_WIDTH, 0) ?: 0
        val restoredHeight = savedInstanceState?.getInt(STATE_CONFIRMED_HEIGHT, 0) ?: 0

        val target = targets.firstOrNull { it.id == restoredId } ?: targets.firstOrNull()
        if (target == null) {
            showNoWidgets()
        } else {
            selectTarget(target, restoredCrop)
            if (restoredWidth > 0 && restoredHeight > 0 &&
                restoredWidth == target.widthDp && restoredHeight == target.heightDp
            ) {
                confirmedSize = restoredWidth to restoredHeight
            }
            if (restoredType != null) {
                config = config.copy(type = restoredType, color = restoredColor)
                renderTypeSelection()
                renderColorValue()
            }
        }
        updateButtons()
    }

    private fun bindControls() {
        binding.btnSelectWidget.setOnClickListener { showTargetPicker() }
        binding.btnImport.setOnClickListener { confirmSizeAndImport() }
        binding.btnRefreshSize.setOnClickListener { refreshTargetsFromDesktop(showFeedback = true) }
        binding.backgroundTypeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (applyingTypeSelection || !isChecked || selected == null) return@addOnButtonCheckedListener
            val type = when (checkedId) {
                R.id.btn_type_image -> WidgetBackgroundType.IMAGE
                R.id.btn_type_color -> WidgetBackgroundType.COLOR
                else -> WidgetBackgroundType.NONE
            }
            config = config.copy(type = type)
            renderTypeSelection()
        }
        binding.btnPickColor.setOnClickListener {
            WidgetColorPickerDialog(
                this,
                config.color,
                titleRes = R.string.background_color_picker_title,
                sampleTextRes = R.string.background_color_picker_sample
            ) { color ->
                config = config.copy(color = color or 0xFF000000.toInt())
                renderColorValue()
            }.show()
        }
        binding.btnPreview.setOnClickListener { renderPreview() }
        binding.btnSave.setOnClickListener { saveBackground() }
        binding.btnClear.setOnClickListener { confirmClear() }
        binding.cropView.onCropChanged = {
            binding.previewImage.visibility = View.GONE
        }
        renderTypeSelection()
        renderColorValue()
    }

    override fun onResume() {
        super.onResume()
        refreshTargetsFromDesktop()
    }

    private fun showTargetPicker() {
        if (targets.isEmpty()) {
            Toast.makeText(this, R.string.background_no_widgets, Toast.LENGTH_LONG).show()
            return
        }
        val labels = targets.map { target ->
            val hasSameSize = targets.count {
                it.widthDp == target.widthDp && it.heightDp == target.heightDp
            } > 1
            if (hasSameSize) {
                "${target.label} · ${target.widthDp} × ${target.heightDp} dp"
            } else {
                target.label
            }
        }.toTypedArray()
        val checked = targets.indexOfFirst { it.id == selected?.id }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.background_target_choose_title)
            .setSingleChoiceItems(labels, checked) { dialog, index ->
                selectTarget(targets[index])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun selectTarget(target: WidgetTarget, restoredCrop: GlassCrop? = null) {
        operationJob?.cancel()
        selected = target
        confirmedSize = null
        config = prefs.widgetBackgroundConfig(target.id)
        binding.textTarget.text = target.label
        renderWidgetSize()
        binding.cropView.setAspectRatio(target.aspectRatio)
        renderTypeSelection()
        renderColorValue()
        loadSource(restoredCrop)
    }

    private fun refreshTargetsFromDesktop(showFeedback: Boolean = false) {
        val refreshed = WidgetTargetCatalog.load(this)
        targets = refreshed
        val previous = selected
        if (previous == null) {
            if (refreshed.isEmpty()) {
                showNoWidgets()
            } else {
                selectTarget(refreshed.first())
            }
            return
        }

        val updated = refreshed.firstOrNull { it.id == previous.id }
        if (updated == null) {
            confirmedSize = null
            showNoWidgets()
            return
        }

        selected = updated
        binding.textTarget.text = updated.label
        renderWidgetSize()
        if (previous.widthDp != updated.widthDp || previous.heightDp != updated.heightDp) {
            confirmedSize = null
            binding.cropView.setAspectRatio(updated.aspectRatio, adjustCurrent = sourceBitmap != null)
            hidePreview()
        }
        if (showFeedback) {
            Toast.makeText(this, R.string.background_size_refreshed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderWidgetSize() {
        val target = selected ?: run {
            binding.textWidgetSize.setText(R.string.background_no_widgets)
            return
        }
        binding.textWidgetSize.text = getString(
            R.string.background_current_size,
            target.widthDp,
            target.heightDp,
            target.aspectRatio
        )
    }

    private fun confirmSizeAndImport() {
        if (selected == null) {
            Toast.makeText(this, R.string.background_need_widget, Toast.LENGTH_SHORT).show()
            return
        }
        refreshTargetsFromDesktop()
        val target = selected ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.background_confirm_size_title)
            .setMessage(
                getString(
                    R.string.background_confirm_size_message,
                    target.widthDp,
                    target.heightDp
                )
            )
            .setNegativeButton(R.string.background_adjust_on_desktop, null)
            .setPositiveButton(R.string.background_confirm_and_choose) { _, _ ->
                confirmedSize = target.widthDp to target.heightDp
                binding.cropView.setAspectRatio(target.aspectRatio, adjustCurrent = true)
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            .show()
    }

    private fun loadSource(restoredCrop: GlassCrop? = null) {
        val target = selected ?: return
        operationJob?.cancel()
        sourceBitmap = null
        binding.cropView.setBitmap(null)
        binding.previewImage.setImageDrawable(null)
        binding.previewImage.visibility = View.GONE
        setBusy(true)
        operationJob = lifecycleScope.launch {
            try {
                val app = applicationContext
                val pending = withContext(Dispatchers.IO) { WidgetBackgroundStore.pendingSourceFile(app, target.id) }
                val file = withContext(Dispatchers.IO) {
                    when {
                        pending.isFile -> pending
                        else -> WidgetBackgroundStore.sourceFile(app, target.id).takeIf { it.isFile }
                    }
                }
                val bitmap = file?.let { withContext(Dispatchers.IO) { WidgetBackgroundStore.loadSource(it) } }
                sourceBitmap = bitmap
                binding.cropView.setBitmap(bitmap)
                if (bitmap != null) {
                    binding.cropView.setDefaultCrop(target.aspectRatio)
                    val crop = restoredCrop ?: config.crop.takeIf { !pending.isFile }
                    if (crop != null) binding.cropView.setCrop(crop, constrainAspect = true)
                    binding.cropView.setLockAspect(true)
                }
                binding.textEmpty.visibility = if (bitmap == null) View.VISIBLE else View.GONE
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "读取组件背景图片失败", t)
                sourceBitmap = null
                binding.textEmpty.visibility = View.VISIBLE
            } finally {
                if (operationJob === currentCoroutineContext().job) setBusy(false)
            }
        }
    }

    private fun importImage(uri: Uri) {
        refreshTargetsFromDesktop()
        val target = selected ?: return
        val confirmed = confirmedSize
        if (confirmed == null || confirmed.first != target.widthDp || confirmed.second != target.heightDp) {
            confirmedSize = null
            Toast.makeText(this, R.string.background_size_changed, Toast.LENGTH_LONG).show()
            return
        }
        setBusy(true)
        operationJob = lifecycleScope.launch {
            try {
                val app = applicationContext
                val file = withContext(Dispatchers.IO) { WidgetBackgroundStore.importSource(app, uri, target.id) }
                val bitmap = withContext(Dispatchers.IO) { WidgetBackgroundStore.loadSource(file) }
                    ?: error("decode returned null")
                binding.cropView.setBitmap(null)
                sourceBitmap = bitmap
                binding.cropView.setBitmap(bitmap)
                binding.cropView.setDefaultCrop(target.aspectRatio)
                binding.cropView.setLockAspect(true)
                binding.textEmpty.visibility = View.GONE
                binding.previewImage.setImageDrawable(null)
                binding.previewImage.visibility = View.GONE
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "导入背景图片失败", t)
                Toast.makeText(this@WidgetBackgroundActivity, R.string.background_import_failed, Toast.LENGTH_LONG).show()
            } finally {
                if (operationJob === currentCoroutineContext().job) setBusy(false)
            }
        }
    }

    private fun renderPreview() {
        val target = selected ?: return
        if (config.type == WidgetBackgroundType.IMAGE && sourceBitmap == null) return
        val bitmap = sourceBitmap
        val crop = binding.cropView.getCrop()
        val previewConfig = config
        setBusy(true)
        operationJob = lifecycleScope.launch {
            try {
                val preview = WidgetPreviewRenderer.render(
                    context = this@WidgetBackgroundActivity,
                    target = target,
                    config = previewConfig,
                    source = bitmap,
                    crop = crop
                )
                binding.previewImage.setImageBitmap(preview)
                binding.previewImage.visibility = View.VISIBLE
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "预览背景失败", t)
                Toast.makeText(this@WidgetBackgroundActivity, R.string.background_render_failed, Toast.LENGTH_SHORT).show()
            } finally {
                if (operationJob === currentCoroutineContext().job) setBusy(false)
            }
        }
    }

    private fun saveBackground() {
        refreshTargetsFromDesktop()
        val target = selected ?: return
        val bitmap = sourceBitmap
        if (config.type == WidgetBackgroundType.IMAGE && bitmap == null) return
        val crop = binding.cropView.getCrop()
        setBusy(true)
        operationJob = lifecycleScope.launch {
            try {
                val app = applicationContext
                val newConfig = when (config.type) {
                    WidgetBackgroundType.IMAGE -> {
                        val rendered = withContext(Dispatchers.Default) {
                            WidgetBackgroundRenderer.render(
                                bitmap!!,
                                crop,
                                minimumPixels = WidgetLayoutGeometry.displayPixels(
                                    target.widthDp,
                                    target.heightDp,
                                    resources.displayMetrics.density
                                )
                            )
                        }
                        try {
                            withContext(Dispatchers.IO) {
                                WidgetBackgroundStore.commitPendingSource(app, target.id, rendered)
                            }
                            config.copy(
                                type = WidgetBackgroundType.IMAGE,
                                sourceFileName = WidgetBackgroundStore.sourceFile(app, target.id).name,
                                crop = crop
                            )
                        } finally {
                            rendered.recycle()
                        }
                    }

                    WidgetBackgroundType.COLOR -> config.copy(
                        type = WidgetBackgroundType.COLOR,
                        color = config.color or 0xFF000000.toInt()
                    )

                    WidgetBackgroundType.NONE -> config.copy(type = WidgetBackgroundType.NONE)
                }
                prefs.saveWidgetBackgroundConfig(target.id, newConfig)
                config = newConfig
                WidgetRefresher.refreshAll(app)
                Toast.makeText(this@WidgetBackgroundActivity, R.string.background_saved, Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "保存组件背景失败", t)
                Toast.makeText(this@WidgetBackgroundActivity, R.string.background_render_failed, Toast.LENGTH_LONG).show()
            } finally {
                if (operationJob === currentCoroutineContext().job) setBusy(false)
            }
        }
    }

    private fun confirmClear() {
        val target = selected ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.background_restore_default)
            .setMessage(R.string.background_clear_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.background_restore_default) { _, _ -> clearBackground(target) }
            .show()
    }

    private fun clearBackground(target: WidgetTarget) {
        operationJob?.cancel()
        setBusy(true)
        operationJob = lifecycleScope.launch {
            try {
                val app = applicationContext
                withContext(Dispatchers.IO) { WidgetBackgroundStore.deleteFiles(app, target.id) }
                config = WidgetBackgroundConfig()
                prefs.saveWidgetBackgroundConfig(target.id, config)
                sourceBitmap = null
                binding.cropView.setBitmap(null)
                binding.previewImage.setImageDrawable(null)
                binding.previewImage.visibility = View.GONE
                binding.textEmpty.visibility = View.VISIBLE
                renderTypeSelection()
                renderColorValue()
                WidgetRefresher.refreshAll(app)
                Toast.makeText(this@WidgetBackgroundActivity, R.string.background_cleared, Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "清除组件背景失败", t)
            } finally {
                if (operationJob === currentCoroutineContext().job) setBusy(false)
            }
        }
    }

    private fun showNoWidgets() {
        selected = null
        confirmedSize = null
        config = WidgetBackgroundConfig()
        binding.textTarget.setText(R.string.background_no_widgets)
        renderWidgetSize()
        binding.textEmpty.setText(R.string.background_no_widgets)
        binding.textEmpty.visibility = View.VISIBLE
        binding.cropView.setBitmap(null)
        renderTypeSelection()
        renderColorValue()
    }

    private fun renderTypeSelection() {
        applyingTypeSelection = true
        val checkedId = when (config.type) {
            WidgetBackgroundType.NONE -> R.id.btn_type_none
            WidgetBackgroundType.IMAGE -> R.id.btn_type_image
            WidgetBackgroundType.COLOR -> R.id.btn_type_color
        }
        if (binding.backgroundTypeGroup.checkedButtonId != checkedId) {
            binding.backgroundTypeGroup.check(checkedId)
        }
        applyingTypeSelection = false

        val hasTarget = selected != null
        binding.imageOptions.visibility = if (hasTarget && config.type == WidgetBackgroundType.IMAGE) View.VISIBLE else View.GONE
        binding.colorOptions.visibility = if (hasTarget && config.type == WidgetBackgroundType.COLOR) View.VISIBLE else View.GONE
        if (!hasTarget) {
            binding.textEmpty.visibility = View.VISIBLE
        } else if (config.type == WidgetBackgroundType.IMAGE) {
            binding.textEmpty.visibility = if (sourceBitmap == null) View.VISIBLE else View.GONE
        } else {
            binding.textEmpty.visibility = View.GONE
        }
        updateButtons()
    }

    private fun renderColorValue() {
        val color = config.color or 0xFF000000.toInt()
        binding.backgroundColorPreview.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        binding.textColorValue.text = getString(
            R.string.background_color_value,
            color and 0xFFFFFF
        )
    }

    private fun hidePreview() {
        binding.previewImage.setImageDrawable(null)
        binding.previewImage.visibility = View.GONE
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.progress.visibility = if (value) View.VISIBLE else View.GONE
        updateButtons()
    }

    private fun updateButtons() {
        val hasTarget = selected != null
        val imageReady = config.type != WidgetBackgroundType.IMAGE || sourceBitmap != null
        binding.btnSave.isEnabled = hasTarget && imageReady && !busy
        binding.btnPreview.isEnabled = hasTarget && imageReady && !busy
        binding.btnImport.isEnabled = hasTarget && !busy
        binding.btnRefreshSize.isEnabled = hasTarget && targets.isNotEmpty() && !busy
        binding.btnSelectWidget.isEnabled = hasTarget && targets.isNotEmpty() && !busy
        binding.btnClear.isEnabled = hasTarget && !busy
        binding.btnPickColor.isEnabled = hasTarget && !busy
        binding.backgroundTypeGroup.isEnabled = hasTarget && !busy
        binding.cropView.isEnabled = hasTarget && sourceBitmap != null && !busy
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_WIDGET_ID, selected?.id ?: INVALID_ID)
        outState.putString(STATE_TYPE, config.type.name)
        outState.putInt(STATE_COLOR, config.color)
        outState.putInt(STATE_CONFIRMED_WIDTH, confirmedSize?.first ?: 0)
        outState.putInt(STATE_CONFIRMED_HEIGHT, confirmedSize?.second ?: 0)
        selected?.let { outState.putFloatArray(STATE_CROP, binding.cropView.getCrop().toArray()) }
    }

    override fun onDestroy() {
        operationJob?.cancel()
        super.onDestroy()
    }

    private fun GlassCrop.toArray(): FloatArray = floatArrayOf(left, top, right, bottom)

    private companion object {
        const val TAG = "WidgetBackgroundActivity"
        const val INVALID_ID = -1
        const val STATE_WIDGET_ID = "state_widget_id"
        const val STATE_CROP = "state_crop"
        const val STATE_TYPE = "state_type"
        const val STATE_COLOR = "state_color"
        const val STATE_CONFIRMED_WIDTH = "state_confirmed_width"
        const val STATE_CONFIRMED_HEIGHT = "state_confirmed_height"
    }
}
