package com.glass.todo.ui.glass

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.glass.todo.R
import com.glass.todo.data.GlassCrop
import com.glass.todo.data.Repository
import com.glass.todo.data.WidgetGlassConfig
import com.glass.todo.databinding.ActivityGlassSetupBinding
import com.glass.todo.util.LiquidGlassRenderer
import com.glass.todo.widget.WidgetRefresher
import com.glass.todo.widget.WidgetTarget
import com.glass.todo.widget.WidgetTargetCatalog
import com.glass.todo.widget.glass.WidgetGlassStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 为某个已添加的桌面组件校准截图区域并生成静态液态玻璃背景。 */
class GlassSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGlassSetupBinding
    private val prefs by lazy { Repository.get(this).prefs }
    private var targets: List<WidgetTarget> = emptyList()
    private var selected: WidgetTarget? = null
    private var config = WidgetGlassConfig()
    private var sourceBitmap: android.graphics.Bitmap? = null
    private var operationJob: Job? = null
    private var busy = false

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) importScreenshot(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGlassSetupBinding.inflate(layoutInflater)
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
        // 先恢复比例锁状态，再异步加载图片，避免图片晚于开关恢复时丢失 unlocked 状态。
        binding.switchLockRatio.isChecked = savedInstanceState?.getBoolean(STATE_LOCK_RATIO, true) ?: true
        val restoredId = savedInstanceState?.getInt(STATE_WIDGET_ID, INVALID_ID)
            ?.takeIf { it != INVALID_ID }
        val target = targets.firstOrNull { it.id == restoredId } ?: targets.firstOrNull()
        if (target == null) {
            showNoWidgets()
        } else {
            val restoredCrop = savedInstanceState?.getFloatArray(STATE_CROP)?.let { values ->
                if (values.size == 4) GlassCrop(values[0], values[1], values[2], values[3]) else null
            }
            selectTarget(target, restoredCrop)
            binding.switchEnabled.isChecked = savedInstanceState?.getBoolean(STATE_ENABLED, true) ?: true
            val strength = savedInstanceState?.getInt(STATE_STRENGTH, 65) ?: 65
            binding.sliderStrength.value = strength.toFloat()
            renderStrengthLabel()
        }
        updateButtons()
    }

    private fun bindControls() {
        binding.btnSelectWidget.setOnClickListener { showTargetPicker() }
        binding.btnImport.setOnClickListener {
            if (selected == null) {
                Snackbar.make(binding.root, R.string.glass_need_widget, Snackbar.LENGTH_SHORT).show()
            } else {
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }
        binding.switchLockRatio.setOnCheckedChangeListener { _, checked ->
            binding.cropView.setLockAspect(checked)
        }
        binding.sliderStrength.addOnChangeListener { _, _, _ ->
            renderStrengthLabel()
            binding.previewImage.visibility = View.GONE
        }
        binding.btnPreview.setOnClickListener { renderPreview() }
        binding.btnSave.setOnClickListener { saveBackground() }
        binding.btnClear.setOnClickListener { confirmClear() }
        binding.cropView.onCropChanged = {
            binding.previewImage.visibility = View.GONE
        }
        renderStrengthLabel()
    }

    private fun showTargetPicker() {
        if (targets.isEmpty()) {
            Snackbar.make(binding.root, R.string.glass_no_widgets, Snackbar.LENGTH_LONG).show()
            return
        }
        val labels = targets.map { it.label }.toTypedArray()
        val checked = targets.indexOfFirst { it.id == selected?.id }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.glass_target_choose_title)
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
        config = prefs.widgetGlassConfig(target.id)
        binding.textTarget.text = target.label
        binding.switchEnabled.isChecked = config.enabled || !config.isUsable
        binding.sliderStrength.value = (config.strength * 100f).coerceIn(20f, 100f)
        binding.cropView.setAspectRatio(target.aspectRatio)
        renderStrengthLabel()
        loadSource(restoredCrop)
    }

    private fun loadSource(restoredCrop: GlassCrop? = null) {
        val target = selected ?: return
        operationJob?.cancel()
        binding.cropView.setBitmap(null)
        sourceBitmap = null
        binding.previewImage.setImageDrawable(null)
        binding.previewImage.visibility = View.GONE
        setBusy(true)

        operationJob = lifecycleScope.launch {
            try {
                val app = applicationContext
                val pending = withContext(Dispatchers.IO) { WidgetGlassStore.pendingSourceFile(app, target.id) }
                val file = withContext(Dispatchers.IO) {
                    when {
                        pending.isFile -> pending
                        !config.sourceFileName.isNullOrBlank() ->
                            WidgetGlassStore.sourceFile(app, target.id).takeIf { it.isFile }
                        else -> null
                    }
                }
                val bitmap = file?.let { withContext(Dispatchers.IO) { WidgetGlassStore.loadSource(it) } }
                sourceBitmap = bitmap
                binding.cropView.setBitmap(bitmap)
                if (bitmap != null) {
                    binding.cropView.setDefaultCrop(target.aspectRatio)
                    val crop = restoredCrop ?: config.crop.takeIf { !pending.isFile }
                    if (crop != null) binding.cropView.setCrop(crop)
                    binding.cropView.setLockAspect(binding.switchLockRatio.isChecked)
                }
                binding.textEmpty.visibility = if (bitmap == null) View.VISIBLE else View.GONE
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "读取液态玻璃源图失败", t)
                binding.textEmpty.visibility = View.VISIBLE
            } finally {
                // 被新任务替换的旧协程不能提前解除新任务的忙碌状态。
                if (operationJob === currentCoroutineContext().job) setBusy(false)
            }
        }
    }

    private fun importScreenshot(uri: Uri) {
        val target = selected ?: return
        setBusy(true)
        operationJob = lifecycleScope.launch {
            try {
                val app = applicationContext
                val file = withContext(Dispatchers.IO) { WidgetGlassStore.importSource(app, uri, target.id) }
                val bitmap = withContext(Dispatchers.IO) { WidgetGlassStore.loadSource(file) }
                    ?: error("decode returned null")
                binding.cropView.setBitmap(null)
                sourceBitmap = bitmap
                binding.cropView.setBitmap(bitmap)
                binding.cropView.setDefaultCrop(target.aspectRatio)
                binding.textEmpty.visibility = View.GONE
                binding.previewImage.setImageDrawable(null)
                binding.previewImage.visibility = View.GONE
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "导入截图失败", t)
                Snackbar.make(binding.root, R.string.glass_import_failed, Snackbar.LENGTH_LONG).show()
            } finally {
                // 被新任务替换的旧协程不能提前解除新任务的忙碌状态。
                if (operationJob === currentCoroutineContext().job) setBusy(false)
            }
        }
    }

    private fun renderPreview() {
        val target = selected ?: return
        val bitmap = sourceBitmap ?: return
        val crop = binding.cropView.getCrop()
        val strength = binding.sliderStrength.value / 100f
        setBusy(true)
        operationJob = lifecycleScope.launch {
            try {
                val preview = withContext(Dispatchers.Default) {
                    LiquidGlassRenderer.render(bitmap, crop, strength, maxOutputPixels = 40_000)
                }
                binding.previewImage.setImageBitmap(preview)
                binding.previewImage.visibility = View.VISIBLE
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "预览渲染失败", t)
                Snackbar.make(binding.root, R.string.glass_render_failed, Snackbar.LENGTH_SHORT).show()
            } finally {
                // 被新任务替换的旧协程不能提前解除新任务的忙碌状态。
                if (operationJob === currentCoroutineContext().job) setBusy(false)
            }
        }
    }

    private fun saveBackground() {
        val target = selected ?: return
        val bitmap = sourceBitmap ?: return
        val crop = binding.cropView.getCrop()
        val strength = binding.sliderStrength.value / 100f
        val enabled = binding.switchEnabled.isChecked
        val oldConfig = config
        setBusy(true)

        operationJob = lifecycleScope.launch {
            try {
                val app = applicationContext
                val newConfig = if (enabled) {
                    val rendered = withContext(Dispatchers.Default) {
                        LiquidGlassRenderer.render(bitmap, crop, strength)
                    }
                    try {
                        withContext(Dispatchers.IO) {
                            WidgetGlassStore.commitPendingSource(app, target.id)
                            WidgetGlassStore.saveRendered(app, target.id, rendered)
                        }
                        oldConfig.copy(
                            enabled = true,
                            sourceFileName = WidgetGlassStore.sourceFile(app, target.id).name,
                            crop = crop,
                            strength = strength
                        )
                    } finally {
                        rendered.recycle()
                    }
                } else {
                    withContext(Dispatchers.IO) { WidgetGlassStore.discardPendingSource(app, target.id) }
                    oldConfig.copy(enabled = false, crop = crop, strength = strength)
                }
                prefs.saveWidgetGlassConfig(target.id, newConfig)
                config = newConfig
                WidgetRefresher.refreshAll(app)
                Snackbar.make(
                    binding.root,
                    if (enabled) R.string.glass_saved else R.string.glass_cleared,
                    Snackbar.LENGTH_SHORT
                ).show()
                finish()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "保存液态玻璃背景失败", t)
                Snackbar.make(binding.root, R.string.glass_render_failed, Snackbar.LENGTH_LONG).show()
                setBusy(false)
            }
        }
    }

    private fun confirmClear() {
        val target = selected ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.glass_clear)
            .setMessage(R.string.glass_clear_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.glass_clear) { _, _ -> clearBackground(target) }
            .show()
    }

    private fun clearBackground(target: WidgetTarget) {
        operationJob?.cancel()
        setBusy(true)
        operationJob = lifecycleScope.launch {
            try {
                val app = applicationContext
                withContext(Dispatchers.IO) { WidgetGlassStore.deleteFiles(app, target.id) }
                config = WidgetGlassConfig()
                prefs.saveWidgetGlassConfig(target.id, config)
                binding.cropView.setBitmap(null)
                sourceBitmap = null
                binding.previewImage.setImageDrawable(null)
                binding.previewImage.visibility = View.GONE
                binding.textEmpty.visibility = View.VISIBLE
                WidgetRefresher.refreshAll(app)
                Snackbar.make(binding.root, R.string.glass_cleared, Snackbar.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "清除液态玻璃背景失败", t)
            } finally {
                // 被新任务替换的旧协程不能提前解除新任务的忙碌状态。
                if (operationJob === currentCoroutineContext().job) setBusy(false)
            }
        }
    }

    private fun showNoWidgets() {
        selected = null
        binding.textTarget.setText(R.string.glass_no_widgets)
        binding.textEmpty.setText(R.string.glass_no_widgets)
        binding.textEmpty.visibility = View.VISIBLE
        binding.cropView.setBitmap(null)
        updateButtons()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.progress.visibility = if (value) View.VISIBLE else View.GONE
        updateButtons()
    }

    private fun updateButtons() {
        val ready = selected != null && sourceBitmap != null
        binding.btnSave.isEnabled = ready && !busy
        binding.btnPreview.isEnabled = ready && !busy
        binding.btnImport.isEnabled = selected != null && !busy
        binding.btnSelectWidget.isEnabled = !busy
        binding.btnClear.isEnabled = !busy
        binding.cropView.isEnabled = !busy
        binding.switchLockRatio.isEnabled = !busy
        binding.switchEnabled.isEnabled = !busy
        binding.sliderStrength.isEnabled = !busy
    }

    private fun renderStrengthLabel() {
        binding.textStrengthValue.text = getString(
            R.string.glass_strength_value,
            binding.sliderStrength.value.toInt()
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_WIDGET_ID, selected?.id ?: INVALID_ID)
        selected?.let { outState.putFloatArray(STATE_CROP, binding.cropView.getCrop().toArray()) }
        outState.putBoolean(STATE_LOCK_RATIO, binding.switchLockRatio.isChecked)
        outState.putBoolean(STATE_ENABLED, binding.switchEnabled.isChecked)
        outState.putInt(STATE_STRENGTH, binding.sliderStrength.value.toInt())
    }

    override fun onDestroy() {
        operationJob?.cancel()
        // 渲染协程取消不会立即中断 CPU 像素循环，位图交给 GC 回收，避免旋转屏幕时提前 recycle。
        super.onDestroy()
    }

    private fun GlassCrop.toArray(): FloatArray = floatArrayOf(left, top, right, bottom)

    private companion object {
        const val TAG = "GlassSetupActivity"
        const val INVALID_ID = -1
        const val STATE_WIDGET_ID = "state_widget_id"
        const val STATE_CROP = "state_crop"
        const val STATE_LOCK_RATIO = "state_lock_ratio"
        const val STATE_ENABLED = "state_enabled"
        const val STATE_STRENGTH = "state_strength"
    }
}
