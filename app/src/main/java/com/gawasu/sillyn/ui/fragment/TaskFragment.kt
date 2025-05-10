package com.gawasu.sillyn.ui.fragment
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope // Import lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.FragmentTaskListBinding
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.ui.adapter.TaskAdapter
import com.gawasu.sillyn.ui.dialog.AddTaskDialogFragment
import com.gawasu.sillyn.ui.viewmodel.MainViewModel
import com.gawasu.sillyn.ui.viewmodel.TaskViewModel
import com.gawasu.sillyn.utils.FirebaseResult
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch // Import launch
import kotlinx.coroutines.flow.collectLatest // Import collectLatest

@AndroidEntryPoint
class TaskFragment : Fragment(), TaskAdapter.OnItemClickListener { // Implement onItemLongClick interface
    private var _binding: FragmentTaskListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TaskViewModel by viewModels() // Sử dụng activityViewModels() nếu muốn share VM với activity
    private lateinit var mainViewModel: MainViewModel // Dùng activityViewModels()
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var auth: FirebaseAuth
    private var currentUserId: String? = null

    // Sử dụng Safe Args để nhận arguments
    private val args: TaskFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskListBinding.inflate(inflater, container, false)
        Log.d(TAG, "onCreateView")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")

        mainViewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java] // Get shared ViewModel
        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid

        // Load tasks based on navigation arguments
        currentUserId?.let { userId ->
            val filterType = args.filterType
            val categoryName = args.categoryName
            Log.d(TAG, "TaskFragment received args: filterType=$filterType, categoryName=$categoryName")

            // Cập nhật tiêu đề Toolbar và gọi ViewModel để load dữ liệu
            val title = when {
                filterType == "inbox" -> getString(R.string.inbox_task)
                filterType == "today" -> getString(R.string.today)
                filterType == "week" -> getString(R.string.week_icm)
                filterType == "category" && categoryName != null -> categoryName
                else -> {
                    // Trường hợp mặc định (ví dụ khi đến từ Bottom Nav lần đầu)
                    Log.w(TAG, "Unknown filter type or missing categoryId. Defaulting to Inbox.")
                    getString(R.string.inbox_task)
                }
            }
            mainViewModel.setToolbarTitle(title)
            mainViewModel.setOptionsMenuVisibility(true) // Đảm bảo options menu luôn visible cho fragment này

            // Gọi hàm loadTasks chung trong ViewModel
            // Không cần gọi loadInbox/Today/Week/Category trực tiếp ở đây nữa, loadTasks handle logic
            viewModel.loadTasks(userId, filterType, categoryName)

        } ?: run {
            //TODO: Handle trường hợp không có userId, có thể navigate về auth screen hoặc báo lỗi
            Toast.makeText(context, "User not logged in", Toast.LENGTH_LONG).show()
            // Có thể thêm logic navigate về auth screen nếu userId bị null ở đây
        }

        setupRecyclerView()
        setupSwipeRefreshLayout()
        setupFabAddTask()
        observeTasks() // Observes the *processed* tasks from ViewModel
        observeTaskActionResults() // Observes Add, Update, Delete results
        setupFragmentResultListeners()
        setupFragmentMenu()
        observeViewModelState() // Observe state like hideCompleted to update menu icons/checks
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(this) // Pass 'this' as listener
        binding.recyclerviewTasks.apply {
            adapter = taskAdapter
            layoutManager = LinearLayoutManager(requireContext())
            //setHasFixedSize(true) // Có thể gây vấn đề với ItemAnimator nếu bật, tắt để an toàn
        }
    }

    private fun setupSwipeRefreshLayout() {
        swipeRefreshLayout = binding.swipeRefreshLayoutTasks
        swipeRefreshLayout.setOnRefreshListener {
            // Khi refresh, tải lại dữ liệu cho filter/category hiện tại
            currentUserId?.let { userId ->
                // FIX 4: Call internal refreshTasks
                viewModel.refreshTasks(userId)
            } ?: run {
                Log.e(TAG, "Cannot refresh tasks, currentUserId is null")
                swipeRefreshLayout.isRefreshing = false
                Snackbar.make(binding.taskListCoordinatorLayout, "Lỗi: Không thể refresh, người dùng không xác định", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun setupFabAddTask() {
        binding.fabAddTask.setOnClickListener {
            val currentCategoryName = if (args.filterType == "category") args.categoryName else null

            // Get the list of available categories from MainViewModel
            val availableCategories: List<String> = when (val result = mainViewModel.taskCategories.value) {
                is FirebaseResult.Success -> result.data.orEmpty() // Use orEmpty()
                else -> emptyList()
            }
            // Pass default category name AND available categories
            AddTaskDialogFragment.newInstance(currentCategoryName, availableCategories)
                .show(childFragmentManager, AddTaskDialogFragment.TAG)
        }
    }

    // Observe the *processed* task list from TaskViewModel
    private fun observeTasks() {
        viewModel.tasks.observe(viewLifecycleOwner) { result ->
            swipeRefreshLayout.isRefreshing = false // Dừng animation refresh
            when (result) {
                is FirebaseResult.Success -> {
                    val tasks = result.data.orEmpty()
                    taskAdapter.submitList(tasks)
                    binding.textviewNoTasks.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerviewTasks.visibility = if (tasks.isEmpty()) View.GONE else View.VISIBLE
                    Log.d(TAG, "Processed tasks loaded successfully: ${tasks.size} items")
                }
                is FirebaseResult.Error -> {
                    binding.textviewNoTasks.visibility = View.VISIBLE
                    binding.recyclerviewTasks.visibility = View.GONE
                    Snackbar.make(binding.taskListCoordinatorLayout, "Lỗi khi tải tasks: ${result.exception.message}", Snackbar.LENGTH_LONG).show()
                    Log.e(TAG, "Error fetching tasks: ", result.exception)
                }
                is FirebaseResult.Loading -> {
                    // Check if there are already items before showing loading over empty
                    if (taskAdapter.itemCount == 0) {
                        binding.textviewNoTasks.visibility = View.GONE
                        binding.recyclerviewTasks.visibility = View.GONE
                    }
                    swipeRefreshLayout.isRefreshing = true // Bắt đầu animation refresh
                }
            }
        }
    }

    // Observe results of task actions (Add, Update, Delete)
    private fun observeTaskActionResults() {
        viewModel.addTaskResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is FirebaseResult.Success -> {
                    Snackbar.make(binding.taskListCoordinatorLayout, "Thêm task thành công", Snackbar.LENGTH_SHORT).show()
                    // Task list refresh is now handled inside ViewModel's addTask
                }
                is FirebaseResult.Error -> {
                    Snackbar.make(binding.taskListCoordinatorLayout, "Lỗi khi thêm task: ${result.exception.message}", Snackbar.LENGTH_LONG).show()
                    Log.e(TAG, "Error adding task: ", result.exception)
                }
                is FirebaseResult.Loading -> { /* Handle loading if needed */ }
            }
        }

        viewModel.updateTaskResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is FirebaseResult.Success -> {
                    Snackbar.make(binding.taskListCoordinatorLayout, "Cập nhật task thành công", Snackbar.LENGTH_SHORT).show()
                    // Task list refresh is now handled inside ViewModel's updateTask
                }
                is FirebaseResult.Error -> {
                    Snackbar.make(binding.taskListCoordinatorLayout, "Lỗi khi cập nhật task: ${result.exception.message}", Snackbar.LENGTH_LONG).show()
                    Log.e(TAG, "Error updating task: ", result.exception)
                }
                is FirebaseResult.Loading -> { /* Handle loading if needed */ }
            }
        }

        viewModel.deleteTaskResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is FirebaseResult.Success -> {
                    Snackbar.make(binding.taskListCoordinatorLayout, "Xóa task thành công", Snackbar.LENGTH_SHORT).show()
                    // Task list refresh is now handled inside ViewModel's deleteTask
                }
                is FirebaseResult.Error -> {
                    Snackbar.make(binding.taskListCoordinatorLayout, "Lỗi khi xóa task: ${result.exception.message}", Snackbar.LENGTH_LONG).show()
                    Log.e(TAG, "Error deleting task: ", result.exception)
                }
                is FirebaseResult.Loading -> { /* Handle loading if needed */ }
            }
        }
    }


    private fun setupFragmentResultListeners() {
        // Listener cho kết quả thêm task mới
        childFragmentManager.setFragmentResultListener(
            REQUEST_KEY_ADD_TASK,
            viewLifecycleOwner
        ) { requestKey, bundle ->
            val task: Task? = bundle.getParcelable(BUNDLE_KEY_TASK)
            if (task != null) {
                Log.d(TAG, "Received new task from dialog: ${task.title}")
                currentUserId?.let { userId ->
                    viewModel.addTask(userId, task)
                } ?: run {
                    Log.e(TAG, "Cannot add task, currentUserId is null")
                    Snackbar.make(binding.taskListCoordinatorLayout, "Lỗi: Không xác định được người dùng", Snackbar.LENGTH_LONG).show()
                }
            } else {
                Log.e(TAG, "Received null task from dialog for ADD_TASK")
            }
        }

        // Listener cho kết quả cập nhật task
        childFragmentManager.setFragmentResultListener(
            REQUEST_KEY_EDIT_TASK,
            viewLifecycleOwner
        ) { requestKey, bundle ->
            val task: Task? = bundle.getParcelable(BUNDLE_KEY_TASK)
            if (task != null) {
                Log.d(TAG, "Received updated task from dialog: ${task.title}")
                currentUserId?.let { userId ->
                    viewModel.updateTask(userId, task)
                } ?: run {
                    Log.e(TAG, "Cannot update task, currentUserId is null")
                    Snackbar.make(binding.taskListCoordinatorLayout, "Lỗi: Không xác định được người dùng", Snackbar.LENGTH_LONG).show()
                }
            } else {
                Log.e(TAG, "Received null task from dialog for EDIT_TASK")
            }
        }
    }

    private fun setupFragmentMenu() {
        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.task_fragment_menu, menu)
                Log.d(TAG, "onCreateMenu for TaskFragment called")

                // Setup Search View
                val searchItem = menu.findItem(R.id.action_search_tasks)
                val searchView = searchItem?.actionView as? SearchView
                searchView?.queryHint = getString(R.string.search_hint) // Set hint text

                searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        // Handle search submission if needed (e.g., close keyboard)
                        searchView.clearFocus() // Hide keyboard
                        return true // Event handled
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        // Update search query in ViewModel immediately
                        viewModel.setSearchQuery(newText.orEmpty())
                        return true // Event handled
                    }
                })

                // Restore previous search query if any
                lifecycleScope.launch {
                    viewModel.searchQuery.collectLatest { query ->
                        // Only set query if it's different to avoid infinite loops or unnecessary updates
                        if (searchView != null && !searchView.query.toString().contentEquals(query)) {
                            // Use setQuery(query, false) to avoid triggering onQueryTextChange again
                            searchView.setQuery(query, false)
                            // Expand the search view if query is not empty
                            if (query.isNotBlank()) {
                                searchItem.expandActionView()
                            }
                        }
                    }
                }
            }

            override fun onPrepareMenu(menu: Menu) {
                super.onPrepareMenu(menu)
                Log.d(TAG, "onPrepareMenu for TaskFragment called")

                // Update check state of "Ẩn đã hoàn thành" based on ViewModel state
                val hideCompletedItem = menu.findItem(R.id.action_hide_completed)
                // Read current value from StateFlow
                hideCompletedItem?.isChecked = viewModel.hideCompleted.value

                // Update title of sort options to show current selection
                val sortPriorityItem = menu.findItem(R.id.action_sort_priority)
                val sortDueDateItem = menu.findItem(R.id.action_sort_due_date)
                when (viewModel.sortOrder.value) { // Read current value from StateFlow
                    TaskViewModel.SortOrder.PRIORITY -> {
                        sortPriorityItem?.title = getString(R.string.sort_by_priority_checked) // Add checkmark/indicator
                        sortDueDateItem?.title = getString(R.string.sort_by_due_date)
                    }
                    TaskViewModel.SortOrder.DUEDATE -> {
                        sortPriorityItem?.title = getString(R.string.sort_by_priority)
                        sortDueDateItem?.title = getString(R.string.sort_by_due_date_checked) // Add checkmark/indicator
                    }
                    else -> { // NONE
                        sortPriorityItem?.title = getString(R.string.sort_by_priority)
                        sortDueDateItem?.title = getString(R.string.sort_by_due_date)
                    }
                }
            }


            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_search_tasks -> {
                        // Handled by SearchView.OnQueryTextListener
                        false // Let the SearchView handle it
                    }
                    R.id.action_filter_tasks -> {
                        // This is the parent item for the sub-menu, no action needed here
                        false // Return false so the sub-menu can be shown
                    }
                    R.id.action_sort_priority -> {
                        viewModel.setSortOrder(TaskViewModel.SortOrder.PRIORITY)
                        // Invalidate menu to update check/title state - done by observeViewModelState
                        true
                    }
                    R.id.action_sort_due_date -> {
                        viewModel.setSortOrder(TaskViewModel.SortOrder.DUEDATE)
                        // Invalidate menu to update check/title state - done by observeViewModelState
                        true
                    }
                    R.id.action_hide_completed -> {
                        // Toggle the hide completed state
                        val isChecked = !menuItem.isChecked // Get the state *before* toggle
                        viewModel.setHideCompleted(isChecked)
                        // Invalidate options menu to update the checkmark - done by observeViewModelState
                        true
                    }
                    else -> false // Trả về false để hệ thống xử lý nếu không khớp
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    // Observe ViewModel state changes to update menu if necessary (e.g., hideCompleted checkbox)
    private fun observeViewModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.hideCompleted.collectLatest { isHidden ->
                // Invalidate options menu when hideCompleted state changes
                Log.d(TAG, "hideCompleted state changed to $isHidden. Invalidating options menu.")
                requireActivity().invalidateOptionsMenu()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.sortOrder.collectLatest { order ->
                // Invalidate options menu when sortOrder state changes
                Log.d(TAG, "sortOrder state changed to $order. Invalidating options menu.")
                requireActivity().invalidateOptionsMenu()
            }
        }
        // No need to observe searchQuery here, SearchView handles it directly
    }


    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
        _binding = null
        // Reset search query in ViewModel when leaving fragment to avoid restoring it on a different screen/context
        // viewModel.setSearchQuery("") // Optional: depends on desired behavior when navigating away
    }

    // --- TaskAdapter.OnItemClickListener Implementations ---
    override fun onItemClick(task: Task) {
        Log.d(TAG, "Task item clicked: ${task.title}")
        showEditTaskDialog(task)
    }

    override fun onCheckboxClick(task: Task, isChecked: Boolean) {
        Log.d(TAG, "Task checkbox clicked: ${task.title}, isChecked: $isChecked")
        val updatedStatus = if (isChecked) Task.TaskStatus.COMPLETED.name else Task.TaskStatus.PENDING.name
        // Create a *copy* of the task with updated status
        val updatedTask = task.copy(status = updatedStatus)
        currentUserId?.let { userId ->
            viewModel.updateTask(userId, updatedTask)
        } ?: Log.e(TAG, "Cannot update task status, currentUserId is null")
    }

    // Implement onItemLongClick
    override fun onItemLongClick(task: Task, view: View): Boolean {
        Log.d(TAG, "Task item long-clicked: ${task.title}")
        showItemPopupMenu(task, view)
        return true // Consume the long click
    }

    private fun showItemPopupMenu(task: Task, view: View) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(R.menu.menu_task_item_actions, popupMenu.menu) // Need a menu_task_item_actions.xml

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit_task -> {
                    showEditTaskDialog(task)
                    true
                }
                R.id.action_delete_task -> {
                    showDeleteConfirmationDialog(task)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun showDeleteConfirmationDialog(task: Task) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.confirm_delete_title))
            .setMessage(getString(R.string.confirm_delete_message, task.title))
            .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                currentUserId?.let { userId ->
                    task.id?.let { taskId ->
                        viewModel.deleteTask(userId, taskId)
                    } ?: Log.e(TAG, "Cannot delete task, task ID is null: ${task.title}")
                } ?: Log.e(TAG, "Cannot delete task, currentUserId is null")
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }


    // Use the single AddTaskDialogFragment for editing
    private fun showEditTaskDialog(task: Task) {
        // Get the list of available categories from MainViewModel
        val availableCategories: List<String> = when (val result = mainViewModel.taskCategories.value) {
            is FirebaseResult.Success -> result.data.orEmpty()
            else -> emptyList()
        }
        // Pass the task object and available categories to the dialog
        AddTaskDialogFragment.newInstance(task, availableCategories)
            .show(childFragmentManager, AddTaskDialogFragment.TAG)
    }


    companion object {
        private const val TAG = "TaskFragment"
        public const val REQUEST_KEY_ADD_TASK = "add_task_request"
        public const val REQUEST_KEY_EDIT_TASK = "edit_task_request"
        public const val BUNDLE_KEY_TASK = "task_bundle_key"
    }
}