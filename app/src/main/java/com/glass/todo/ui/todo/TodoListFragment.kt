package com.glass.todo.ui.todo

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

        adapter = TodoAdapter(
            onToggle = { todo -> toggle(todo, repo) },
            onEdit = { todo ->
                TodoEditSheet.newInstance(todo.id).show(parentFragmentManager, TAG_EDIT)
            }
        )
        binding.todoList.layoutManager = LinearLayoutManager(requireContext())
        binding.todoList.adapter = adapter

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
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val item = adapter.currentList.getOrNull(viewHolder.bindingAdapterPosition) ?: return
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.todos.delete(item)
                    WidgetRefresher.refreshAll(requireContext())
                    Snackbar.make(binding.root, R.string.deleted_undo_hint, Snackbar.LENGTH_LONG)
                        .setAnchorView(binding.todoFab)
                        .setAction(R.string.action_undo) {
                            launch {
                                // 原样写回，保留 id 与创建时间
                                repo.todos.insert(item)
                                WidgetRefresher.refreshAll(requireContext())
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
        binding.todoList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val TAG_EDIT = "todo_edit_sheet"
    }
}
