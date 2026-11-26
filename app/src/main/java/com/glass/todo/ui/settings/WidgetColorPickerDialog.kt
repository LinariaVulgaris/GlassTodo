package com.glass.todo.ui.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.InputFilter
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.glass.todo.R
import com.glass.todo.databinding.DialogWidgetColorPickerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/** RGB 滑钮 + 十六进制输入的自定义颜色对话框。 */
class WidgetColorPickerDialog(
    context: Context,
    initialColor: Int,
    private val titleRes: Int = R.string.color_picker_title,
    private val sampleTextRes: Int = R.string.color_picker_sample,
    private val previewMode: PreviewMode = PreviewMode.BACKGROUND,
    private val onColorSelected: (Int) -> Unit
) {

    private val binding = DialogWidgetColorPickerBinding.inflate(android.view.LayoutInflater.from(context))
    private var selectedColor = initialColor or 0xFF000000.toInt()
    private var syncing = false

    fun show() {
        binding.textColorPreviewSample.setText(sampleTextRes)
        render(selectedColor, updateHex = true)
        binding.colorHex.filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(7))

        binding.sliderColorRed.addOnChangeListener { _, _, _ ->
            if (!syncing) updateFromSliders(updateHex = true)
        }
        binding.sliderColorGreen.addOnChangeListener { _, _, _ ->
            if (!syncing) updateFromSliders(updateHex = true)
        }
        binding.sliderColorBlue.addOnChangeListener { _, _, _ ->
            if (!syncing) updateFromSliders(updateHex = true)
        }
        binding.colorHex.doAfterTextChanged { text ->
            if (syncing) return@doAfterTextChanged
            val parsed = parseHex(text?.toString().orEmpty())
            if (parsed != null) {
                selectedColor = parsed
                binding.colorHexLayout.error = null
                render(selectedColor, updateHex = false)
            } else if ((text?.length ?: 0) >= 6) {
                binding.colorHexLayout.error = binding.root.context.getString(R.string.color_hex_invalid)
            }
        }

        val builder = MaterialAlertDialogBuilder(binding.root.context)
            .setTitle(titleRes)
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val parsed = parseHex(binding.colorHex.text?.toString().orEmpty())
                if (parsed == null) {
                    binding.colorHexLayout.error = binding.root.context.getString(R.string.color_hex_invalid)
                    binding.colorHex.requestFocus()
                } else {
                    onColorSelected(parsed)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun updateFromSliders(updateHex: Boolean) {
        selectedColor = Color.rgb(
            binding.sliderColorRed.value.toInt(),
            binding.sliderColorGreen.value.toInt(),
            binding.sliderColorBlue.value.toInt()
        )
        binding.colorHexLayout.error = null
        render(selectedColor, updateHex)
    }

    private fun render(color: Int, updateHex: Boolean) {
        syncing = true
        when (previewMode) {
            PreviewMode.TEXT -> {
                // 文字颜色模式直接展示用户选中的字色，不再把颜色填到示例框背景。
                binding.colorPreview.backgroundTintList = null
                binding.textColorPreviewSample.setTextColor(color)
            }

            PreviewMode.BACKGROUND -> {
                binding.colorPreview.backgroundTintList = ColorStateList.valueOf(color)
                binding.textColorPreviewSample.setTextColor(
                    if (Color.luminance(color) > 0.45f) Color.BLACK else Color.WHITE
                )
            }
        }
        val red = (color ushr 16) and 0xFF
        val green = (color ushr 8) and 0xFF
        val blue = color and 0xFF
        binding.sliderColorRed.value = red.toFloat()
        binding.sliderColorGreen.value = green.toFloat()
        binding.sliderColorBlue.value = blue.toFloat()
        binding.textColorRed.text = String.format(Locale.US, "%d", red)
        binding.textColorGreen.text = String.format(Locale.US, "%d", green)
        binding.textColorBlue.text = String.format(Locale.US, "%d", blue)
        if (updateHex) {
            binding.colorHex.setText(String.format(Locale.US, "#%06X", color and 0xFFFFFF))
            binding.colorHex.setSelection(binding.colorHex.length())
        }
        syncing = false
    }

    private fun parseHex(value: String): Int? {
        val normalized = value.trim().removePrefix("#")
        if (!normalized.matches(Regex("[0-9A-Fa-f]{6}"))) return null
        val rgb = normalized.toLong(16)
        return Color.rgb(((rgb ushr 16) and 0xFF).toInt(), ((rgb ushr 8) and 0xFF).toInt(), (rgb and 0xFF).toInt())
    }

    enum class PreviewMode { TEXT, BACKGROUND }
}
