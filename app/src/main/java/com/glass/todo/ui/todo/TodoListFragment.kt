package com.glass.todo.ui.todo

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.glass.todo.databinding.FragmentTodoListBinding
import com.glass.todo.widget.WidgetRefresher
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class TodoListFragment : Fragment() {

    private var _binding: FragmentTodoListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: TodoAdapter
    private lateinit var prefs: Prefs

    private val todoDisplayListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.KEY_TODO_DATE_DISPLAY_MODE || key == Prefs.KEY_TODO_TIME_PRECISION) {
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
                repo.todos.observeAll().collect { list ->
                    adapter.submitList(list)
                    binding.todoEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    fun refreshDates() {
        if (::adapter.isInitialized) adapter.refreshDates()
    }

    private fun toggle(todo: TodoEntity, repo: Repository) {
        viewLifecycleOwner.lifecycleScope.launch {
            repo.todos.toggleDone(todo.id)
            // 桌面组件不会自己感知数据库变化，必须显式推一次
            WidgetRefresher.refreshAll(requireContext())
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
                    repo.reorderTodos(orderedIds)
                    WidgetRefresher.refreshAll(app)
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val item = adapter.itemAt(viewHolder.bindingAdapterPosition) ?: return
                val app = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.todos.delete(item)
                    WidgetRefresher.refreshAll(app)
                    Snackbar.make(binding.root, R.string.deleted_undo_hint, Snackbar.LENGTH_LONG)
                        .setAnchorView(binding.todoFab)
                        .setAction(R.string.action_undo) {
                            // 撤销必须使用独立的页面生命周期作用域，不能挂在已结束的删除协程里。
                            viewLifecycleOwner.lifecycleScope.launch {
                                // 原样写回，保留 id、创建时间和手动顺序。
                                repo.todos.insert(item)
                                WidgetRefresher.refreshAll(app)
                            }
                        }
                        .show()
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
        const val TAG_EDIT = "todo_edit_sheet"
    }
}
