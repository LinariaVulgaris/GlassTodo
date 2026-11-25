package com.glass.todo.ui.todo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.glass.todo.R
import com.glass.todo.data.Repository
import com.glass.todo.data.TodoEntity
import com.glass.todo.databinding.SheetTodoEditBinding
import com.glass.todo.ui.DeadlineTimePicker
import com.glass.todo.util.DateText
import com.glass.todo.widget.WidgetRefresher
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 新建/编辑待办的底部弹窗。不传 id 即为新建。 */
class TodoEditSheet : BottomSheetDialogFragment() {

    private var _binding: SheetTodoEditBinding? = null
    private val binding get() = _binding!!

    /** 当前选中的截止日，null 表示不设截止日 */
    private var dueEpochDay: Long? = null
    /** null 表示关闭详细 DDL；非 null 表示已选择的具体时间。 */
    private var dueMinuteOfDay: Int? = null
    private var editingId: Long = NEW_ID
    private var writeJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetTodoEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repo = Repository.get(requireContext())
        editingId = requireArguments().getLong(ARG_ID, NEW_ID)
        dueEpochDay = savedInstanceState
            ?.takeIf { it.containsKey(STATE_DUE) }
            ?.getLong(STATE_DUE, Long.MIN_VALUE)
            ?.takeUnless { it == Long.MIN_VALUE }
        dueMinuteOfDay = savedInstanceState
            ?.takeIf { it.containsKey(STATE_MINUTE) }
            ?.getInt(STATE_MINUTE)
            ?.let { DateText.normalizeMinuteOfDay(it) }
        binding.switchDetailedDeadline.isChecked =
            savedInstanceState?.getBoolean(STATE_DETAILED, false) ?: false

        binding.dueField.setOnClickListener { showDatePicker() }
        binding.dueClear.setOnClickListener {
            dueEpochDay = null
            binding.switchDetailedDeadline.isChecked = false
            renderDue()
        }
        binding.switchDetailedDeadline.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (dueEpochDay == null) showDatePicker()
                if (dueMinuteOfDay == null) dueMinuteOfDay = DeadlineTimePicker.DEFAULT_MINUTE_OF_DAY
            }
            renderDue()
        }
        binding.deadlineTimeField.setOnClickListener {
            DeadlineTimePicker.show(this, dueMinuteOfDay) { minute ->
                dueMinuteOfDay = minute
                renderDue()
            }
        }
        binding.sheetSave.setOnClickListener { save(repo) }
        binding.sheetDelete.setOnClickListener { delete(repo) }

        if (editingId == NEW_ID) {
            binding.sheetDelete.visibility = View.GONE
            binding.sheetTitle.setText(R.string.sheet_title_new_todo)
        } else {
            binding.sheetTitle.setText(R.string.sheet_title_edit_todo)
            viewLifecycleOwner.lifecycleScope.launch {
                val todo = withContext(Dispatchers.IO) { repo.todos.getByIdSync(editingId) }
                if (todo == null) {
                    dismissAllowingStateLoss()
                } else {
                    binding.todoTitleInput.setText(todo.title)
                    binding.todoNoteInput.setText(todo.note)
                    dueEpochDay = todo.dueEpochDay
                    dueMinuteOfDay = todo.dueMinuteOfDay?.let { DateText.normalizeMinuteOfDay(it) }
                    binding.switchDetailedDeadline.isChecked = dueMinuteOfDay != null
                    renderDue()
                }
            }
        }
        renderDue()
    }

    private fun showDatePicker() {
        val initial = dueEpochDay
            ?.let { DateText.epochDayToUtcMillis(it) }
            ?: MaterialDatePicker.todayInUtcMilliseconds()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.pick_due_date)
            .setSelection(initial)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            dueEpochDay = DateText.utcMillisToEpochDay(millis)
            renderDue()
        }
        picker.show(childFragmentManager, "due_picker")
    }

    private fun renderDue() {
        binding.dueField.error = null
        val day = dueEpochDay
        val detailed = binding.switchDetailedDeadline.isChecked
        val minute = dueMinuteOfDay?.let { DateText.normalizeMinuteOfDay(it) }
        if (day == null) {
            binding.dueField.setText(R.string.due_none)
            binding.dueClear.visibility = View.GONE
        } else {
            binding.dueField.text = if (detailed && minute != null) {
                DateText.formatExactDateTime(day, minute)
            } else {
                DateText.format(requireContext(), day)
            }
            binding.dueClear.visibility = View.VISIBLE
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
        // Room 的 suspend 写入会切到后台，期间按钮仍可点；不加这道门会重复插入。
        if (writeJob?.isActive == true) return
        val title = binding.todoTitleInput.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            binding.todoTitleLayout.error = getString(R.string.error_title_empty)
            return
        }
        binding.todoTitleLayout.error = null
        val note = binding.todoNoteInput.text?.toString()?.trim().orEmpty()
        val day = dueEpochDay
        val detailed = binding.switchDetailedDeadline.isChecked
        if (day == null && detailed) {
            binding.dueField.error = getString(R.string.error_date_empty)
            return
        }
        val minute = if (detailed) {
            DateText.normalizeMinuteOfDay(dueMinuteOfDay)
                ?: DeadlineTimePicker.DEFAULT_MINUTE_OF_DAY
        } else {
            null
        }
        val id = editingId

        writeJob = viewLifecycleOwner.lifecycleScope.launch {
            if (id == NEW_ID) {
                repo.todos.insert(
                    TodoEntity(
                        title = title,
                        note = note,
                        dueEpochDay = day,
                        dueMinuteOfDay = minute
                    )
                )
            } else {
                val existing = withContext(Dispatchers.IO) { repo.todos.getByIdSync(id) }
                if (existing != null) {
                    repo.todos.update(
                        existing.copy(
                            title = title,
                            note = note,
                            dueEpochDay = day,
                            dueMinuteOfDay = minute
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
            withContext(Dispatchers.IO) { repo.todos.getByIdSync(id) }?.let {
                repo.todos.delete(it)
            }
            WidgetRefresher.refreshAll(requireContext())
            dismissAllowingStateLoss()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        dueEpochDay?.let { outState.putLong(STATE_DUE, it) }
        dueMinuteOfDay?.let { outState.putInt(STATE_MINUTE, it) }
        outState.putBoolean(STATE_DETAILED, binding.switchDetailedDeadline.isChecked)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_ID = "arg_todo_id"
        private const val STATE_DUE = "state_due_epoch_day"
        private const val STATE_MINUTE = "state_due_minute_of_day"
        private const val STATE_DETAILED = "state_detailed_deadline"
        const val NEW_ID = -1L

        fun newInstance(id: Long = NEW_ID): TodoEditSheet = TodoEditSheet().apply {
            arguments = Bundle().apply { putLong(ARG_ID, id) }
        }
    }
}
