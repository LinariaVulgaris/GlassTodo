package com.glass.todo.ui.todo

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.glass.todo.R
import com.glass.todo.data.Prefs
import com.glass.todo.data.Repository
import com.glass.todo.data.TodoEntity
import com.glass.todo.data.TodoSorting
import com.glass.todo.databinding.FragmentTodoListBinding
import com.glass.todo.util.DayMath
import com.glass.todo.widget.WidgetRefresher
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TodoListFragment : Fragment() {

    private var _binding: FragmentTodoListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: TodoAdapter
    private lateinit var prefs: Prefs

    private val todoDisplayListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (
            key == Prefs.KEY_TODO_DATE_DISPLAY_MODE ||
            key == Prefs.KEY_TODO_TIME_PRECISION ||
            key == Prefs.KEY_TODO_URGENT_DATE_ENABLED ||
            key == Prefs.KEY_TODO_URGENT_DATE_THRESHOLD
        ) {
            refreshDates()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTodoListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repo = Repository.get(requireContext())
        prefs = repo.prefs

        adapter = TodoAdapter(
            prefs = prefs,
            onToggle = { todo -> toggle(todo, repo) },
            onEdit = { todo ->
                TodoEditSheet.newInstance(todo.id).show(parentFragmentManager, TAG_EDIT)
            }
        )
        binding.todoList.layoutManager = LinearLayoutManager(requireContext())
        binding.todoList.adapter = adapter
        prefs.registerOnSharedPreferenceChangeListener(todoDisplayListener)

        binding.todoFab.setOnClickListener {
            TodoEditSheet.newInstance().show(parentFragmentManager, TAG_EDIT)
        }

        attachSwipeToDelete(repo)
        observe(repo)
    }

    private fun observe(repo: Repository) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.observeTodos().collect { list ->
                    adapter.submitList(list)
                    binding.todoEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    fun refreshDates() {
        if (!::adapter.isInitialized) return
        val sorted = TodoSorting.sort(
            adapter.orderedItems(),
            prefs.todoManualSortEnabled,
            DayMath.today()
        )
        adapter.submitList(sorted)
        adapter.refreshDates()
    }

    private fun toggle(todo: TodoEntity, repo: Repository) {
        val app = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repo.todos.toggleDone(todo.id)
                // 桌面组件不会自己感知数据库变化，必须显式推一次
                WidgetRefresher.refreshAll(app)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "切换待办状态失败", t)
                adapter.refreshDates()
                Toast.makeText(app, R.string.todo_toggle_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun attachSwipeToDelete(repo: Repository) {
        var orderChanged = false
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT
        ) {
            override fun isLongPressDragEnabled(): Boolean = true

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                val dragged = adapter.itemAt(from) ?: return false
                val destination = adapter.itemAt(to) ?: return false
                // 已完成项始终留在列表底部，不允许跨组拖动打乱这个约定。
                if (dragged.done != destination.done) return false
                val moved = adapter.moveItem(from, to)
                if (moved) orderChanged = true
                return moved
            }

            override fun onSelectedChanged(
                viewHolder: RecyclerView.ViewHolder?,
                actionState: Int
            ) {
                super.onSelectedChanged(viewHolder, actionState)
                viewHolder?.itemView?.elevation = if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    8f * resources.displayMetrics.density
                } else {
                    0f
                }
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.elevation = 0f
                if (!orderChanged) return
                orderChanged = false
                val orderedIds = adapter.orderedItems().map { it.id }
                val app = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        repo.applyManualTodoOrder(orderedIds)
                        WidgetRefresher.refreshAll(app)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        Log.e(TAG, "保存待办顺序失败", t)
                        adapter.submitList(repo.observeTodos().first())
                        Toast.makeText(app, R.string.todo_reorder_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val item = adapter.itemAt(viewHolder.bindingAdapterPosition) ?: return
                val app = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        repo.todos.delete(item)
                        WidgetRefresher.refreshAll(app)
                        val root = _binding?.root ?: return@launch
                        Snackbar.make(root, R.string.deleted_undo_hint, Snackbar.LENGTH_LONG)
                            .setAnchorView(binding.todoFab)
                            .setAction(R.string.action_undo) {
                                // 撤销必须使用独立的页面生命周期作用域，不能挂在已结束的删除协程里。
                                viewLifecycleOwner.lifecycleScope.launch {
                                    try {
                                        // 原样恢复 id、创建时间和手动顺序；重复撤销不会覆盖现有记录。
                                        repo.todos.insertIfAbsent(item)
                                        WidgetRefresher.refreshAll(app)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (t: Throwable) {
                                        Log.e(TAG, "撤销删除失败", t)
                                        Toast.makeText(app, R.string.todo_restore_failed, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .show()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        Log.e(TAG, "删除待办失败", t)
                        val restoredPosition = adapter.orderedItems().indexOfFirst { it.id == item.id }
                        if (restoredPosition >= 0) adapter.notifyItemChanged(restoredPosition)
                        Toast.makeText(app, R.string.todo_delete_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.todoList)
    }

    override fun onDestroyView() {
        // 防止 Fragment 视图销毁后 binding 仍被回调持有，导致空指针
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(todoDisplayListener)
        }
        binding.todoList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val TAG = "TodoListFragment"
        const val TAG_EDIT = "todo_edit_sheet"
    }
}
