package com.glass.todo.ui.event

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.glass.todo.R
import com.glass.todo.data.EventEntity
import com.glass.todo.data.RepeatRule
import com.glass.todo.data.Repository
import com.glass.todo.databinding.SheetEventEditBinding
import com.glass.todo.ui.DeadlineTimePicker
import com.glass.todo.util.DateText
import com.glass.todo.widget.WidgetRefresher
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 新建/编辑倒数日事件 */
class EventEditSheet : BottomSheetDialogFragment() {

    private var _binding: SheetEventEditBinding? = null
    private val binding get() = _binding!!

    private var targetEpochDay: Long? = null
    /** null 表示关闭详细 DDL；非 null 表示已选择的具体时间。 */
    private var targetMinuteOfDay: Int? = null
    private var editingId: Long = NEW_ID
    private var writeJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetEventEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repo = Repository.get(requireContext())
        editingId = requireArguments().getLong(ARG_ID, NEW_ID)
        targetEpochDay = savedInstanceState
            ?.takeIf { it.containsKey(STATE_TARGET) }
            ?.getLong(STATE_TARGET, Long.MIN_VALUE)
            ?.takeUnless { it == Long.MIN_VALUE }
        targetMinuteOfDay = savedInstanceState
            ?.takeIf { it.containsKey(STATE_MINUTE) }
            ?.getInt(STATE_MINUTE)
            ?.let { DateText.normalizeMinuteOfDay(it) }
        binding.switchDetailedDeadline.isChecked =
            savedInstanceState?.getBoolean(STATE_DETAILED, false) ?: false

        binding.dateField.setOnClickListener { showDatePicker() }
        binding.switchDetailedDeadline.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (targetEpochDay == null) showDatePicker()
                if (targetMinuteOfDay == null) {
                    targetMinuteOfDay = DeadlineTimePicker.DEFAULT_MINUTE_OF_DAY
                }
            }
            renderDate()
        }
        binding.deadlineTimeField.setOnClickListener {
            DeadlineTimePicker.show(this, targetMinuteOfDay) { minute ->
                targetMinuteOfDay = minute
                renderDate()
            }
        }
        binding.sheetSave.setOnClickListener { save(repo) }
        binding.sheetDelete.setOnClickListener { delete(repo) }

        if (editingId == NEW_ID) {
            binding.sheetDelete.visibility = View.GONE
            binding.sheetTitle.setText(R.string.sheet_title_new_event)
            binding.repeatGroup.check(R.id.repeat_none)
        } else {
            binding.sheetTitle.setText(R.string.sheet_title_edit_event)
            viewLifecycleOwner.lifecycleScope.launch {
                val event = withContext(Dispatchers.IO) { repo.events.getByIdSync(editingId) }
                if (event == null) {
                    dismissAllowingStateLoss()
                } else {
                    binding.eventTitleInput.setText(event.title)
                    targetEpochDay = event.targetEpochDay
                    targetMinuteOfDay = event.targetMinuteOfDay?.let { DateText.normalizeMinuteOfDay(it) }
                    binding.switchDetailedDeadline.isChecked = targetMinuteOfDay != null
                    binding.switchCountUp.isChecked = event.countUpWhenPast
                    binding.repeatGroup.check(buttonForRule(event.repeat))
                    renderDate()
                }
            }
        }
        renderDate()
    }

    private fun buttonForRule(rule: String): Int = when (rule) {
        RepeatRule.WEEKLY -> R.id.repeat_weekly
        RepeatRule.MONTHLY -> R.id.repeat_monthly
        RepeatRule.YEARLY -> R.id.repeat_yearly
        else -> R.id.repeat_none
    }

    private fun ruleOfCheckedButton(): String = when (binding.repeatGroup.checkedButtonId) {
        R.id.repeat_weekly -> RepeatRule.WEEKLY
        R.id.repeat_monthly -> RepeatRule.MONTHLY
        R.id.repeat_yearly -> RepeatRule.YEARLY
        else -> RepeatRule.NONE
    }

    private fun showDatePicker() {
        val initial = targetEpochDay
            ?.let { DateText.epochDayToUtcMillis(it) }
            ?: MaterialDatePicker.todayInUtcMilliseconds()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.pick_event_date)
            .setSelection(initial)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            targetEpochDay = DateText.utcMillisToEpochDay(millis)
            renderDate()
        }
        picker.show(childFragmentManager, "event_date_picker")
    }

    private fun renderDate() {
        binding.dateField.error = null
        val day = targetEpochDay
        val detailed = binding.switchDetailedDeadline.isChecked
        val minute = targetMinuteOfDay?.let { DateText.normalizeMinuteOfDay(it) }
        binding.dateField.text = when {
            day == null -> getString(R.string.date_not_set)
            detailed && minute != null -> DateText.formatExactDateTime(day, minute)
            else -> DateText.format(requireContext(), day)
        }
        binding.deadlineTimeField.visibility =
            if (detailed && day != null) View.VISIBLE else View.GONE
        binding.deadlineTimeField.text = getString(
            R.string.deadline_time_value,
            getString(R.string.pick_deadline_time),
            DateText.formatTime(minute ?: DeadlineTimePicker.DEFAULT_MINUTE_OF_DAY)
        )
    }

    private fun save(repo: Repository) {
        if (writeJob?.isActive == true) return
        val title = binding.eventTitleInput.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            binding.eventTitleLayout.error = getString(R.string.error_title_empty)
            return
        }
        val target = targetEpochDay
        if (target == null) {
            binding.dateField.error = getString(R.string.error_date_empty)
            return
        }
        binding.eventTitleLayout.error = null
        binding.dateField.error = null

        val repeat = ruleOfCheckedButton()
        val countUp = binding.switchCountUp.isChecked
        val minute = if (binding.switchDetailedDeadline.isChecked) {
            DateText.normalizeMinuteOfDay(targetMinuteOfDay)
                ?: DeadlineTimePicker.DEFAULT_MINUTE_OF_DAY
        } else {
            null
        }
        val id = editingId

        writeJob = viewLifecycleOwner.lifecycleScope.launch {
            if (id == NEW_ID) {
                repo.events.insert(
                    EventEntity(
                        title = title,
                        targetEpochDay = target,
                        targetMinuteOfDay = minute,
                        repeat = repeat,
                        countUpWhenPast = countUp
                    )
                )
            } else {
                val existing = withContext(Dispatchers.IO) { repo.events.getByIdSync(id) }
                if (existing != null) {
                    repo.events.update(
                        existing.copy(
                            title = title,
                            targetEpochDay = target,
                            targetMinuteOfDay = minute,
                            repeat = repeat,
                            countUpWhenPast = countUp
                        )
                    )
                }
            }
            WidgetRefresher.refreshAll(requireContext())
            dismissAllowingStateLoss()
        }
    }

    private fun delete(repo: Repository) {
        if (editingId == NEW_ID || writeJob?.isActive == true) return
        val id = editingId
        writeJob = viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) { repo.events.getByIdSync(id) }?.let {
                repo.events.delete(it)
            }
            WidgetRefresher.refreshAll(requireContext())
            dismissAllowingStateLoss()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        targetEpochDay?.let { outState.putLong(STATE_TARGET, it) }
        targetMinuteOfDay?.let { outState.putInt(STATE_MINUTE, it) }
        outState.putBoolean(STATE_DETAILED, binding.switchDetailedDeadline.isChecked)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_ID = "arg_event_id"
        private const val STATE_TARGET = "state_target_epoch_day"
        private const val STATE_MINUTE = "state_target_minute_of_day"
        private const val STATE_DETAILED = "state_detailed_deadline"
        const val NEW_ID = -1L

        fun newInstance(id: Long = NEW_ID): EventEditSheet = EventEditSheet().apply {
            arguments = Bundle().apply { putLong(ARG_ID, id) }
        }
    }
}
