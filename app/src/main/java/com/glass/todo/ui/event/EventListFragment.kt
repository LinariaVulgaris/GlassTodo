package com.glass.todo.ui.event

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
import com.glass.todo.databinding.FragmentEventListBinding
import com.glass.todo.widget.WidgetRefresher
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class EventListFragment : Fragment() {

    private var _binding: FragmentEventListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: EventAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repo = Repository.get(requireContext())

        adapter = EventAdapter { event ->
            EventEditSheet.newInstance(event.id).show(parentFragmentManager, TAG_EDIT)
        }
        binding.eventList.layoutManager = LinearLayoutManager(requireContext())
        binding.eventList.adapter = adapter

        binding.eventFab.setOnClickListener {
            EventEditSheet.newInstance().show(parentFragmentManager, TAG_EDIT)
        }

        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val item = adapter.currentList.getOrNull(viewHolder.bindingAdapterPosition) ?: return
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.events.delete(item)
                    WidgetRefresher.refreshAll(requireContext())
                    Snackbar.make(binding.root, R.string.deleted_undo_hint, Snackbar.LENGTH_LONG)
                        .setAnchorView(binding.eventFab)
                        .setAction(R.string.action_undo) {
                            launch {
                                repo.events.insert(item)
                                WidgetRefresher.refreshAll(requireContext())
                            }
                        }
                        .show()
                }
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.eventList)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.events.observeActive().collect { list ->
                    adapter.submitList(list)
                    binding.eventEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    fun refreshDates() {
        if (::adapter.isInitialized) adapter.refreshDates()
    }

    override fun onDestroyView() {
        binding.eventList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val TAG_EDIT = "event_edit_sheet"
    }
}
