package com.gawasu.sillyn.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider // Import ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.FragmentTaskListBinding
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.ui.adapter.TaskAdapter
import com.gawasu.sillyn.ui.dialog.AddTaskDialogFragment
import com.gawasu.sillyn.ui.dialog.EditTaskDialogFragment
import com.gawasu.sillyn.ui.viewmodel.MainViewModel // Import MainViewModel
import com.gawasu.sillyn.ui.viewmodel.TaskViewModel
import com.gawasu.sillyn.utils.FirebaseResult
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TaskFragment : Fragment(), TaskAdapter.OnItemClickListener {

    private var _binding: FragmentTaskListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TaskViewModel by viewModels()
    private lateinit var mainViewModel: MainViewModel // Declare MainViewModel
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var auth: FirebaseAuth
    private var currentUserId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get MainViewModel instance from the Activity
        mainViewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        // Set toolbar title and show options menu using MainViewModel
        mainViewModel.setToolbarTitle(getString(R.string.nav_tasks_title)) // Use string resource for title
        mainViewModel.setOptionsMenuVisibility(true)

        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid

        setupRecyclerView()
        setupSwipeRefreshLayout()
        setupFabAddTask()
        observeTasks()
        observeAddTaskResult()
        observeUpdateTaskResult()
        observeDeleteTaskResult()

        currentUserId?.let {
            viewModel.getTasks(it) // Lấy tasks khi Fragment được tạo
        } ?: run {
            //TODO: Handle trường hợp không có userId, có thể navigate về auth screen hoặc báo lỗi
            Toast.makeText(context, "User not logged in", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(this)
        binding.recyclerviewTasks.apply {
            adapter = taskAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true) // Tối ưu hóa nếu kích thước item không đổi
        }
    }

    private fun setupSwipeRefreshLayout() {
        swipeRefreshLayout = binding.swipeRefreshLayoutTasks
        swipeRefreshLayout.setOnRefreshListener {
            currentUserId?.let {
                viewModel.getTasks(it) // Gọi lại để refresh tasks
            }
        }
    }

    private fun setupFabAddTask() {
        Log.d(TAG, "fabAddTask reference: ${binding.fabAddTask}")
        binding.fabAddTask.setOnClickListener {
            // TODO: Mở dialog hoặc fragment để thêm task mới
            showAddTaskDialog()
        }
    }

    private fun observeTasks() {
        viewModel.tasks.observe(viewLifecycleOwner) { result ->
            swipeRefreshLayout.isRefreshing = false // Dừng animation refresh
            when (result) {
                is FirebaseResult.Success -> {
                    taskAdapter.submitList(result.data)
                    binding.textviewNoTasks.visibility = if (result.data.isNullOrEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerviewTasks.visibility = if (result.data.isNullOrEmpty()) View.GONE else View.VISIBLE
                }
                is FirebaseResult.Error -> {
                    binding.textviewNoTasks.visibility = View.VISIBLE
                    binding.recyclerviewTasks.visibility = View.GONE
                    Snackbar.make(binding.taskListCoordinatorLayout, "Lỗi khi tải tasks: ${result.exception.message}", Snackbar.LENGTH_LONG).show()
                    Log.e(TAG, "Error fetching tasks: ", result.exception)
                }
                is FirebaseResult.Loading -> {
                    swipeRefreshLayout.isRefreshing = true // Bắt đầu animation refresh
                    binding.textviewNoTasks.visibility = View.GONE
                    binding.recyclerviewTasks.visibility = View.GONE
                }
            }
        }
    }

    private fun observeAddTaskResult() {
        viewModel.addTaskResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is FirebaseResult.Success -> {
                    Snackbar.make(binding.taskListCoordinatorLayout, "Thêm task thành công", Snackbar.LENGTH_SHORT).show()
                    currentUserId?.let { viewModel.getTasks(it) } // Refresh task list
                }
                is FirebaseResult.Error -> {
                    Snackbar.make(binding.taskListCoordinatorLayout, "Lỗi khi thêm task: ${result.exception.message}", Snackbar.LENGTH_LONG).show()
                    Log.e(TAG, "Error adding task: ", result.exception)
                }
                is FirebaseResult.Loading -> {
                    //TODO: Hiển thị loading indicator nếu cần
                }
            }
        }
    }

    private fun observeUpdateTaskResult() {
        viewModel.updateTaskResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is FirebaseResult.Success -> {
                    Snackbar.make(binding.taskListCoordinatorLayout, "Cập nhật task thành công", Snackbar.LENGTH_SHORT).show()
                    currentUserId?.let { viewModel.getTasks(it) } // Refresh task list
                }
                is FirebaseResult.Error -> {
                    Snackbar.make(binding.taskListCoordinatorLayout, "Lỗi khi cập nhật task: ${result.exception.message}", Snackbar.LENGTH_LONG).show()
                    Log.e(TAG, "Error updating task: ", result.exception)
                }
                is FirebaseResult.Loading -> {
                    //TODO: Hiển thị loading indicator nếu cần
                }
            }
        }
    }

    private fun observeDeleteTaskResult() {
        viewModel.deleteTaskResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is FirebaseResult.Success -> {
                    Snackbar.make(binding.taskListCoordinatorLayout, "Xóa task thành công", Snackbar.LENGTH_SHORT).show()
                    currentUserId?.let { viewModel.getTasks(it) } // Refresh task list
                }
                is FirebaseResult.Error -> {
                    Snackbar.make(binding.taskListCoordinatorLayout, "Lỗi khi xóa task: ${result.exception.message}", Snackbar.LENGTH_LONG).show()
                    Log.e(TAG, "Error deleting task: ", result.exception)
                }
                is FirebaseResult.Loading -> {
                    //TODO: Hiển thị loading indicator nếu cần
                }
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // Optionally hide option menu when fragment view is destroyed, if needed for other fragments
        // mainViewModel.setOptionsMenuVisibility(false)
    }

    override fun onItemClick(task: Task) {
        // TODO: Mở dialog hoặc fragment để chỉnh sửa task
        showEditTaskDialog(task)
    }

    override fun onCheckboxClick(task: Task, isChecked: Boolean) {
        val updatedStatus = if (isChecked) Task.TaskStatus.COMPLETED.name else Task.TaskStatus.PENDING.name
        val updatedTask = task.copy(status = updatedStatus) // Tạo bản sao task với status mới
        currentUserId?.let {
            viewModel.updateTask(it, updatedTask)
        }
    }

    private fun showAddTaskDialog() {
        // TODO: Implement dialog or fragment for adding task
        AddTaskDialogFragment().show(childFragmentManager, AddTaskDialogFragment.TAG)
    }

    private fun showEditTaskDialog(task: Task) {
        // TODO: Implement dialog or fragment for editing task
        EditTaskDialogFragment.newInstance(task).show(childFragmentManager, EditTaskDialogFragment.TAG)
    }

    companion object {
        private const val TAG = "TaskFragment"
    }
}