package com.glass.todo.ui

import android.text.format.DateFormat
import androidx.fragment.app.Fragment
import com.glass.todo.R
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat

/** 待办的具体时间选择器。 */
object DeadlineTimePicker {

    const val DEFAULT_MINUTE_OF_DAY = 23 * 60 + 59

    fun show(fragment: Fragment, initialMinute: Int?, onSelected: (Int) -> Unit) {
        val minute = initialMinute?.coerceIn(0, 23 * 60 + 59) ?: DEFAULT_MINUTE_OF_DAY
        val context = fragment.requireContext()
        val timeFormat = if (DateFormat.is24HourFormat(context)) {
            TimeFormat.CLOCK_24H
        } else {
            TimeFormat.CLOCK_12H
        }
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(timeFormat)
            .setHour(minute / 60)
            .setMinute(minute % 60)
            .setTitleText(R.string.pick_deadline_time)
            .build()
        picker.addOnPositiveButtonClickListener {
            onSelected((picker.hour * 60 + picker.minute).coerceIn(0, 23 * 60 + 59))
        }
        picker.show(fragment.childFragmentManager, "deadline_time_picker")
    }
}
