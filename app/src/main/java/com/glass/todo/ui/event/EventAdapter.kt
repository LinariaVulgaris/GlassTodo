package com.glass.todo.ui.event

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.glass.todo.R
import com.glass.todo.data.EventEntity
import com.glass.todo.data.RepeatRule
import com.glass.todo.databinding.ItemEventBinding
import com.glass.todo.util.DateText
import com.glass.todo.util.DayMath
import com.glass.todo.util.Urgency
import com.glass.todo.util.WidgetPalette

class EventAdapter(
    private val onClick: (EventEntity) -> Unit
) : ListAdapter<EventEntity, EventAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    /** 自然跨日后，即使数据库没有变化，倒数天数也需要重算 */
    fun refreshDates() = notifyDataSetChanged()

    inner class VH(private val b: ItemEventBinding) : RecyclerView.ViewHolder(b.root) {

        private val defaultColor = ContextCompat.getColor(b.root.context, R.color.badge_default)

        fun bind(item: EventEntity) {
            val ctx = b.root.context
            val display = DayMath.display(item)
            val accent = WidgetPalette.colorFor(ctx, display.urgency, defaultColor)

            b.itemTitle.text = item.title
            val minute = item.targetMinuteOfDay?.let { DateText.normalizeMinuteOfDay(it) }
            b.itemDate.text = if (minute != null) {
                DateText.formatExactDateTime(
                    DayMath.nextOccurrence(item).toEpochDay(),
                    minute
                )
            } else {
                DateText.format(ctx, item.targetEpochDay)
            }
            b.itemDays.text = display.number
            b.itemCaption.text = display.caption
            b.itemDays.setTextColor(accent)
            b.itemCaption.setTextColor(
                if (display.urgency == Urgency.LATER) defaultColor else accent
            )

            val repeatText = when (item.repeat) {
                RepeatRule.WEEKLY -> ctx.getString(R.string.repeat_weekly)
                RepeatRule.MONTHLY -> ctx.getString(R.string.repeat_monthly)
                RepeatRule.YEARLY -> ctx.getString(R.string.repeat_yearly)
                else -> null
            }
            if (repeatText == null) {
                b.itemRepeat.visibility = View.GONE
            } else {
                b.itemRepeat.visibility = View.VISIBLE
                b.itemRepeat.text = repeatText
            }

            b.root.setOnClickListener { onClick(item) }
            b.itemEdit.setOnClickListener { onClick(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<EventEntity>() {
            override fun areItemsTheSame(oldItem: EventEntity, newItem: EventEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: EventEntity, newItem: EventEntity) =
                oldItem == newItem
        }
    }
}
