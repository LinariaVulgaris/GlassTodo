package com.glass.todo.ui.todo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.glass.todo.R
import com.glass.todo.data.Prefs
import com.glass.todo.data.TodoEntity
import com.glass.todo.databinding.ItemTodoBinding
import com.glass.todo.util.DayMath
import com.glass.todo.util.TodoDateText
import com.glass.todo.util.Urgency
import com.glass.todo.util.WidgetPalette

class TodoAdapter(
    private val prefs: Prefs,
    private val onToggle: (TodoEntity) -> Unit,
    private val onEdit: (TodoEntity) -> Unit
) : RecyclerView.Adapter<TodoAdapter.VH>() {

    private var items: List<TodoEntity> = emptyList()

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemTodoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long = items[position].id

    /** 同步提交列表，保证长按拖动过程中 Adapter 的位置和界面始终一致。 */
    fun submitList(newItems: List<TodoEntity>) {
        val oldItems = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size
            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldItems[oldItemPosition].id == newItems[newItemPosition].id

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldItems[oldItemPosition] == newItems[newItemPosition]
        })
        items = newItems.toList()
        diff.dispatchUpdatesTo(this)
    }

    fun itemAt(position: Int): TodoEntity? = items.getOrNull(position)

    fun orderedItems(): List<TodoEntity> = items

    fun moveItem(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition !in items.indices || toPosition !in items.indices) return false
        if (fromPosition == toPosition) return false
        val reordered = items.toMutableList()
        val moved = reordered.removeAt(fromPosition)
        reordered.add(toPosition, moved)
        items = reordered
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    /** 自然跨日后，数据库内容没变也会改变「今天/明天/超 N 天」等文案 */
    fun refreshDates() = notifyItemRangeChanged(0, itemCount)

    inner class VH(private val b: ItemTodoBinding) : RecyclerView.ViewHolder(b.root) {

        private val defaultBadgeColor =
            ContextCompat.getColor(b.root.context, R.color.badge_default)

        fun bind(item: TodoEntity) {
            b.itemTitle.text = item.title
            // 用 alpha 表达「已完成」，避免和紧急程度的强调色打架
            b.itemTitle.alpha = if (item.done) 0.45f else 1f

            if (item.note.isBlank()) {
                b.itemNote.visibility = View.GONE
            } else {
                b.itemNote.visibility = View.VISIBLE
                b.itemNote.text = item.note
            }

            val labels = TodoDateText.labels(
                dueEpochDay = item.dueEpochDay,
                dueMinuteOfDay = item.dueMinuteOfDay,
                mode = prefs.todoDateDisplayMode,
                timePrecision = prefs.todoTimePrecision
            )
            val days = item.dueEpochDay?.let { DayMath.daysForDue(it) }
            val urgency = days?.let { DayMath.urgencyOf(it) }
            val dateColor = if (item.done || urgency == null || urgency == Urgency.LATER) {
                defaultBadgeColor
            } else {
                WidgetPalette.colorFor(b.root.context, urgency, defaultBadgeColor)
            }

            if (labels.deadline == null) {
                b.itemDeadline.visibility = View.GONE
            } else {
                b.itemDeadline.visibility = View.VISIBLE
                b.itemDeadline.text = labels.deadline
                b.itemDeadline.setTextColor(dateColor)
            }
            if (labels.remaining == null) {
                b.itemBadge.visibility = View.GONE
            } else {
                b.itemBadge.visibility = View.VISIBLE
                b.itemBadge.text = labels.remaining
                b.itemBadge.setTextColor(dateColor)
            }

            // 先摘监听再赋值，否则代码设值会触发回调，造成「写库→回流→再写库」的循环
            b.itemDone.setOnCheckedChangeListener(null)
            b.itemDone.isChecked = item.done
            b.itemDone.setOnCheckedChangeListener { _, _ -> onToggle(item) }

            b.root.setOnClickListener { onToggle(item) }
            b.itemEdit.setOnClickListener { onEdit(item) }
        }
    }

}
