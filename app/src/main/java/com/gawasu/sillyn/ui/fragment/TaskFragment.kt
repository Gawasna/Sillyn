package com.gawasu.sillyn.ui.fragment
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs // Import navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.FragmentTaskListBinding // Đảm bảo đúng tên binding class
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.ui.adapter.TaskAdapter
import com.gawasu.sillyn.ui.dialog.AddTaskDialogFragment
import com.gawasu.sillyn.ui.dialog.EditTaskDialogFragment
import com.gawasu.sillyn.ui.viewmodel.MainViewModel
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
    private lateinit var mainViewModel: MainViewModel
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var auth: FirebaseAuth
    private var currentUserId: String? = null

    // Sử dụng Safe Args để nhận arguments
    private val args: TaskFragmentArgs by navArgs() // Import TaskFragmentArgs

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainViewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid

        val filterType = args.filterType
        val categoryName = args.categoryName
        Log.d(TAG, "TaskFragment received args: filterType=$filterType, categoryName=$categoryName")
        // Cập nhật tiêu đề Toolbar và gọi ViewModel để load dữ liệu
        currentUserId?.let { userId ->
            when {
                filterType == "inbox" -> {
                    mainViewModel.setToolbarTitle(getString(R.string.inbox_task))
                    viewModel.loadInboxTasks(userId) // Gọi hàm load Inbox
                }
                filterType == "today" -> {
                    mainViewModel.setToolbarTitle(getString(R.string.today))
                    viewModel.loadTodayTasks(userId) // Gọi hàm load Today
                }
                filterType == "week" -> {
                    mainViewModel.setToolbarTitle(getString(R.string.week_icm))
                    viewModel.loadWeekTasks(userId) // Gọi hàm load Week
                }
                filterType == "category" && categoryName != null -> {
                    mainViewModel.setToolbarTitle(categoryName) // Tiêu đề là tên danh mục
                    viewModel.loadTasksByCategory(userId, categoryName) // Gọi hàm load theo Category
                }
                else -> {
                    // Trường hợp mặc định (ví dụ khi đến từ Bottom Nav lần đầu)
                    // args.filterType có defaultValue="inbox" nên case này ít xảy ra trừ khi argument bị null/empty
                    Log.w(TAG, "Unknown filter type or missing categoryId. Loading Inbox by default.")
                    mainViewModel.setToolbarTitle(getString(R.string.inbox_task))
                    viewModel.loadInboxTasks(userId)
                }
            }
            mainViewModel.setOptionsMenuVisibility(true) // Đảm bảo options menu luôn visible cho fragment này
        } ?: run {
            //TODO: Handle trường hợp không có userId, có thể navigate về auth screen hoặc báo lỗi
            Toast.makeText(context, "User not logged in", Toast.LENGTH_LONG).show()
            // Có thể thêm logic navigate về auth screen nếu userId bị null ở đây
        }

        setupRecyclerView()
        setupSwipeRefreshLayout()
        setupFabAddTask()
        observeTasks()
        observeAddTaskResult()
        observeUpdateTaskResult()
        observeDeleteTaskResult() //<-F
        setupFragmentResultListeners()
        setupFragmentMenu()
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(this)
        binding.recyclerviewTasks.apply {
            adapter = taskAdapter
            layoutManager = LinearLayoutManager(requireContext())
            //setHasFixedSize(true) // Có thể gây vấn đề với ItemAnimator nếu bật
        }
    }

    private fun setupSwipeRefreshLayout() {
        swipeRefreshLayout = binding.swipeRefreshLayoutTasks
        swipeRefreshLayout.setOnRefreshListener {
            // Khi refresh, tải lại dữ liệu cho filter/category hiện tại
            currentUserId?.let { userId ->
                val filterType = args.filterType
                val categoryName = args.categoryName

                when {
                    filterType == "inbox" -> viewModel.loadInboxTasks(userId)
                    filterType == "today" -> viewModel.loadTodayTasks(userId)
                    filterType == "week" -> viewModel.loadWeekTasks(userId)
                    filterType == "category" && categoryName != null -> viewModel.loadTasksByCategory(userId, categoryName)
                    else -> {
                        Log.w(TAG, "Cannot refresh unknown filter type. Refreshing Inbox.")
                        viewModel.loadInboxTasks(userId)
                    }
                }
            }
        }
    }

    private fun setupFabAddTask() {
        binding.fabAddTask.setOnClickListener {
            val currentCategoryName = if (args.filterType == "category") args.categoryName else null

            // TODO: Get the list of available categories from MainViewModel or TaskViewModel
            // For now, let's use an empty list or get it from MainViewModel if it's exposed
            val availableCategories: List<String> = when (val result = mainViewModel.taskCategories.value) {
                is FirebaseResult.Success -> result.data
                else -> emptyList()
            }
            AddTaskDialogFragment.newInstance(currentCategoryName, availableCategories) // Pass category name AND available categories
                .show(childFragmentManager, AddTaskDialogFragment.TAG)
        }
    }

    private fun observeTasks() {
        viewModel.tasks.observe(viewLifecycleOwner) { result ->
            swipeRefreshLayout.isRefreshing = false // Dừng animation refresh
            when (result) {
                is FirebaseResult.Success -> {
                    val tasks = result.data.orEmpty() // Sử dụng orEmpty() để tránh null
                    taskAdapter.submitList(tasks)
                    binding.textviewNoTasks.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerviewTasks.visibility = if (tasks.isEmpty()) View.GONE else View.VISIBLE
                    Log.d(TAG, "Tasks loaded successfully: ${tasks.size} items")
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

    private fun setupFragmentResultListeners() {
        // Listener cho kết quả thêm task mới
        childFragmentManager.setFragmentResultListener(
            REQUEST_KEY_ADD_TASK, // Request key mà dialog gửi đi
            viewLifecycleOwner // LifecycleOwner để tự hủy listener khi view bị destroy
        ) { requestKey, bundle ->
            // Xử lý kết quả nhận được
            val task: Task? = bundle.getParcelable(BUNDLE_KEY_TASK) // Key mà dialog dùng để put Parcelable
            if (task != null) {
                Log.d(TAG, "Received new task from dialog: ${task.title}")
                // TODO: Gọi ViewModel để thêm task
                currentUserId?.let { userId ->
                    viewModel.addTask(userId, task) // Gọi phương thức addTask trong TaskViewModel
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
            REQUEST_KEY_EDIT_TASK, // Request key mà dialog gửi đi khi edit
            viewLifecycleOwner // LifecycleOwner
        ) { requestKey, bundle ->
            // Xử lý kết quả nhận được
            val task: Task? = bundle.getParcelable(BUNDLE_KEY_TASK) // Key mà dialog dùng để put Parcelable
            if (task != null) {
                Log.d(TAG, "Received updated task from dialog: ${task.title}")
                // TODO: Gọi ViewModel để cập nhật task
                currentUserId?.let { userId ->
                    viewModel.updateTask(userId, task) // Gọi phương thức updateTask trong TaskViewModel
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
        // Lấy MenuHost từ Activity (AppCompatActivity là một MenuHost)
        val menuHost: MenuHost = requireActivity()

        // Thêm MenuProvider, liên kết với vòng đời View của Fragment
        // Menu item sẽ được hiển thị khi Fragment ở trạng thái RESUMED (view hiển thị)
        menuHost.addMenuProvider(object : MenuProvider {
            // Phương thức tương đương onCreateOptionsMenu
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.task_fragment_menu, menu) // Inflate menu riêng của Fragment
                Log.d(TAG, "onCreateMenu for TaskFragment called")
            }

            // Phương thức tương đương onPrepareOptionsMenu (tùy chọn)
            override fun onPrepareMenu(menu: Menu) {
                super.onPrepareMenu(menu)
                Log.d(TAG, "onPrepareMenu for TaskFragment called")
                // Logic ẩn/hiện item dựa trên trạng thái Fragment ở đây
                // Ví dụ:
                // val searchItem = menu.findItem(R.id.action_search_tasks)
                // searchItem?.isVisible = taskAdapter.itemCount > 0 // Cẩn thận với taskAdapter null hoặc chưa load data
            }


            // Phương thức tương đương onOptionsItemSelected
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle item selection specific to TaskFragment's menu
                return when (menuItem.itemId) {
                    R.id.action_search_tasks -> { // Ví dụ: ID item tìm kiếm
                        // TODO: Xử lý action tìm kiếm
                        Toast.makeText(context, "Search Clicked (MenuProvider)", Toast.LENGTH_SHORT).show()
                        true // Báo hiệu đã xử lý sự kiện
                    }
                    // Thêm các item menu khác của Fragment ở đây
                    else -> false // Trả về false để hệ thống xử lý nếu không khớp
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED) // <-- Rất quan trọng: liên kết với viewLifecycleOwner và trạng thái RESUMED
        // Bạn có thể dùng lifecycleOwner và Lifecycle.State.STARTED nếu muốn menu hiển thị sớm hơn
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // Có thể reset tiêu đề toolbar hoặc ẩn options menu khi rời khỏi fragment nếu cần
        // mainViewModel.setOptionsMenuVisibility(false)
        // mainViewModel.setToolbarTitle(getString(R.string.app_name)) // Đặt lại tiêu đề mặc định
    }

    // Các hàm xử lý click item, checkbox, dialogs giữ nguyên
    override fun onItemClick(task: Task) { showEditTaskDialog(task) }
    override fun onCheckboxClick(task: Task, isChecked: Boolean) {
        val updatedStatus = if (isChecked) Task.TaskStatus.COMPLETED.name else Task.TaskStatus.PENDING.name
        val updatedTask = task.copy(status = updatedStatus)
        currentUserId?.let { userId ->
            viewModel.updateTask(userId, updatedTask)
        }
    }

//    private fun showAddTaskDialog() {
//      val currentCategoryName = if (args.filterType == "category") args.categoryName else null
//      AddTaskDialogFragment.newInstance(currentCategoryName)
//                 .show(childFragmentManager, AddTaskDialogFragment.TAG)
//   }

// Cập nhật AddTaskDialogFragment.kt để nhận categoryId
// Thêm companion object newInstance(categoryId: String?) -> Bundle args
// TaskFragment: showAddTaskDialog() -> AddTaskDialogFragment.newInstance(currentCategoryId).show(...)
// AddTaskDialogFragment: Nhận args.categoryId, sử dụng nó khi tạo task mới.

    private fun showEditTaskDialog(task: Task) {
        EditTaskDialogFragment.newInstance(task).show(childFragmentManager, EditTaskDialogFragment.TAG)
    }

    companion object {
        private const val TAG = "TaskFragment"
        public const val REQUEST_KEY_ADD_TASK = "add_task_request"
        public const val REQUEST_KEY_EDIT_TASK = "edit_task_request"
        public const val BUNDLE_KEY_TASK = "task_bundle_key"
    }
}