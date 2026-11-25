package com.glass.todo.ui.todo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.glass.todo.R
import com.glass.todo.data.TodoEntity
import com.glass.todo.databinding.ItemTodoBinding
import com.glass.todo.util.DayMath
import com.glass.todo.util.DateText
import com.glass.todo.util.Urgency
import com.glass.todo.util.WidgetPalette

class TodoAdapter(
    private val onToggle: (TodoEntity) -> Unit,
    private val onEdit: (TodoEntity) -> Unit
) : ListAdapter<TodoEntity, TodoAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemTodoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    /** 自然跨日后，数据库内容没变也会改变「今天/明天/超 N 天」等文案 */
    fun refreshDates() = notifyDataSetChanged()

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

            val detailedDay = item.dueEpochDay
            val detailedMinute = item.dueMinuteOfDay?.let { DateText.normalizeMinuteOfDay(it) }
            if (detailedDay != null && detailedMinute != null) {
                b.itemBadge.visibility = View.GONE
                b.itemDeadline.visibility = View.VISIBLE
                b.itemDeadline.text = DateText.formatExactDateTime(detailedDay, detailedMinute)
                val days = DayMath.daysForDue(detailedDay) ?: 0L
                val urgency = DayMath.urgencyOf(days)
                b.itemDeadline.setTextColor(
                    if (item.done || urgency == Urgency.LATER) {
                        defaultBadgeColor
                    } else {
                        WidgetPalette.colorFor(b.root.context, urgency, defaultBadgeColor)
                    }
                )
            } else {
                b.itemDeadline.visibility = View.GONE
                val badge = DayMath.badgeFor(item.dueEpochDay)
                if (badge == null) {
                    b.itemBadge.visibility = View.GONE
                } else {
                    b.itemBadge.visibility = View.VISIBLE
                    b.itemBadge.text = badge
                    val days = DayMath.daysForDue(item.dueEpochDay) ?: 0L
                    val urgency = DayMath.urgencyOf(days)
                    b.itemBadge.setTextColor(
                        if (item.done || urgency == Urgency.LATER) {
                            defaultBadgeColor
                        } else {
                            WidgetPalette.colorFor(b.root.context, urgency, defaultBadgeColor)
                        }
                    )
                }
            }

            // 先摘监听再赋值，否则代码设值会触发回调，造成「写库→回流→再写库」的循环
            b.itemDone.setOnCheckedChangeListener(null)
            b.itemDone.isChecked = item.done
            b.itemDone.setOnCheckedChangeListener { _, _ -> onToggle(item) }

            b.root.setOnClickListener { onToggle(item) }
            b.itemEdit.setOnClickListener { onEdit(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<TodoEntity>() {
            override fun areItemsTheSame(oldItem: TodoEntity, newItem: TodoEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: TodoEntity, newItem: TodoEntity) =
                oldItem == newItem
        }
    }
}
