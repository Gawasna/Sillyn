package com.gawasu.sillyn.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.gawasu.sillyn.data.repository.TaskRepository // Đảm bảo TaskRepository có các hàm cần thiết
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.reminder.TaskReminderScheduler
import com.gawasu.sillyn.util.ReminderTimeCalculator
import com.gawasu.sillyn.utils.FirebaseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val taskReminderScheduler: TaskReminderScheduler
) : ViewModel() {

    // Sử dụng MutableStateFlow để giữ raw tasks từ repository cho filter/sort
    private val _rawTasks = MutableStateFlow<FirebaseResult<List<Task>>>(FirebaseResult.Loading)

    // State Flows cho trạng thái UI (sort, hide completed, search)
    private val _sortOrder = MutableStateFlow(SortOrder.NONE)
    val sortOrder = _sortOrder.asStateFlow() // Expose as StateFlow

    private val _hideCompleted = MutableStateFlow(false)
    val hideCompleted = _hideCompleted.asStateFlow() // Expose as StateFlow

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow() // Expose as StateFlow


    // LiveData cho danh sách tasks đã xử lý (lọc, sắp xếp, tìm kiếm) mà Fragment sẽ observe
    val tasks: LiveData<FirebaseResult<List<Task>>> = combine(
        _rawTasks,
        _sortOrder,
        _hideCompleted,
        _searchQuery
    ) { rawTasksResult, sortOrder, hideCompleted, searchQuery ->
        // Combine function runs whenever any of the source flows emit a new value
        when (rawTasksResult) {
            is FirebaseResult.Loading -> FirebaseResult.Loading // Propagate Loading state
            is FirebaseResult.Error -> FirebaseResult.Error(rawTasksResult.exception) // Propagate Error state
            is FirebaseResult.Success -> {
                val tasks = rawTasksResult.data.orEmpty()

                // 1. Apply Search Query
                val filteredTasks = if (searchQuery.isNotBlank()) {
                    tasks.filter { task ->
                        // Case-insensitive search on title only
                        task.title.contains(searchQuery, ignoreCase = true)
                    }
                } else {
                    tasks
                }

                // 2. Separate Completed and Pending
                val (completedTasks, pendingTasks) = filteredTasks.partition { it.status == Task.TaskStatus.COMPLETED.name }

                // 3. Sort Pending Tasks
                val sortedPendingTasks = sortTasks(pendingTasks, sortOrder)

                // 4. Sort Completed Tasks (as per requirement)
                val sortedCompletedTasks = sortTasks(completedTasks, sortOrder)


                // 5. Combine Lists based on hideCompleted flag
                val finalTaskList = if (hideCompleted) {
                    sortedPendingTasks
                } else {
                    sortedPendingTasks + sortedCompletedTasks // Completed tasks always at the bottom
                }

                FirebaseResult.Success(finalTaskList)
            }
        }
    }.asLiveData(viewModelScope.coroutineContext) // Collect the combined flow and expose as LiveData

    // LiveDatas cho kết quả các action (Add, Update, Delete)
    private val _addTaskResult = MutableLiveData<FirebaseResult<Unit>>()
    val addTaskResult: LiveData<FirebaseResult<Unit>> get() = _addTaskResult

    private val _updateTaskResult = MutableLiveData<FirebaseResult<Void>>()
    val updateTaskResult: LiveData<FirebaseResult<Void>> = _updateTaskResult

    private val _deleteTaskResult = MutableLiveData<FirebaseResult<Void>>()
    val deleteTaskResult: LiveData<FirebaseResult<Void>> = _deleteTaskResult


    // Enum for sorting criteria
    enum class SortOrder {
        NONE, PRIORITY, DUEDATE
    }

    // Helper function to apply sorting logic
    private fun sortTasks(tasks: List<Task>, sortOrder: SortOrder): List<Task> {
        return when (sortOrder) {
            SortOrder.NONE -> tasks // No sorting, keep current order
            SortOrder.PRIORITY -> tasks.sortedWith(compareByDescending<Task> { it.priorityValue() }.thenBy { it.dueDate ?: Date(Long.MAX_VALUE) }) // High to Low, then by DueDate
            SortOrder.DUEDATE -> tasks.sortedWith(compareBy<Task> { it.dueDate ?: Date(Long.MAX_VALUE) }.thenByDescending { it.priorityValue() }) // Earliest DueDate first, then by Priority (High to Low)
        }
    }

    // Helper extension to map Priority string to a comparable value (Higher value = Higher priority)
    private fun Task.priorityValue(): Int {
        return when (this.priority) {
            Task.Priority.HIGH.name -> 3
            Task.Priority.MEDIUM.name -> 2
            Task.Priority.LOW.name -> 1
            else -> 0 // NONE
        }
    }


    // Function to load tasks based on filter type
    fun loadTasks(userId: String, filterType: String, categoryName: String? = null) {
        viewModelScope.launch {
            // Reset state when loading new filter
            _sortOrder.value = SortOrder.NONE
            _hideCompleted.value = false
            _searchQuery.value = ""

            val tasksFlow: Flow<FirebaseResult<List<Task>>> = when (filterType) {
                "inbox" -> taskRepository.getTasks(userId)
                "today" -> taskRepository.getTodayTasks(userId)
                "week" -> taskRepository.getWeekTasks(userId)
                "category" -> {
                    if (categoryName != null) {
                        taskRepository.getTasksByCategory(userId, categoryName)
                    } else {
                        // Handle error: category filter requires categoryName
                        Log.e(TAG, "Category filter selected but categoryName is null")
                        // Emit an error or empty success result
                        MutableStateFlow(FirebaseResult.Error(IllegalArgumentException("Category name is required for category filter")))
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown filter type: $filterType. Loading Inbox by default.")
                    taskRepository.getTasks(userId)
                }
            }

            tasksFlow
                .onStart { _rawTasks.value = FirebaseResult.Loading } // Emit Loading before fetching
                // FIX 1: Cast Throwable to Exception
                .catch { e -> _rawTasks.value = FirebaseResult.Error(Exception(e)) } // Catch any exceptions
                .collectLatest { result -> _rawTasks.value = result } // Emit the result to the raw tasks flow
        }
    }

    // Functions to update UI state (called from Fragment menu/search)
    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        Log.d(TAG, "Sort order set to: $order")
    }

    fun setHideCompleted(hide: Boolean) {
        _hideCompleted.value = hide
        Log.d(TAG, "Hide completed tasks set to: $hide")
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        Log.d(TAG, "Search query set to: '$query'")
    }


    fun addTask(userId: String, task: Task) {
        _addTaskResult.postValue(FirebaseResult.Loading)
        viewModelScope.launch {
            taskRepository.addTask(userId, task).collectLatest { result ->
                when(result) {
                    is FirebaseResult.Success -> {
                        val addedTask = result.data // addedTask now contains the ID from Firestore
                        Log.d(TAG, "Task added successfully with ID: ${addedTask.id}")

                        // Post Success<Void> result to the UI
                        _addTaskResult.postValue(FirebaseResult.voidSuccess())

                        // Schedule Reminder using the actual addedTask object with ID
                        // Ensure task is pending and has a future due date for scheduling
                        if (addedTask.status == Task.TaskStatus.PENDING.name && addedTask.dueDate != null) {
                            val reminderTime = ReminderTimeCalculator.calculateReminderTime(addedTask)
                            // Only schedule if the calculated reminder time is in the future
                            if (reminderTime != null && reminderTime.after(Date())) { // Compare with current Date
                                taskReminderScheduler.scheduleReminder(addedTask)
                                Log.d(TAG, "Scheduled reminder for new task: ${addedTask.id} at ${reminderTime}")
                            } else {
                                Log.d(TAG, "New task ${addedTask.id} has reminder time in the past or invalid. Not scheduling.")
                            }
                        } else {
                            Log.d(TAG, "New task ${addedTask.id} does not meet scheduling criteria (not pending or no due date).")
                        }

                        // Refresh the raw tasks list to reflect the addition
                        refreshTasks(userId)
                    }
                    is FirebaseResult.Error -> {
                        Log.e(TAG, "Error adding task: ${result.exception.message}", result.exception)
                        _addTaskResult.postValue(result)
                    }
                    is FirebaseResult.Loading -> {
                        // Typically, repository's addTask shouldn't emit Loading beyond the initial state,
                        // but handling defensively.
                        _addTaskResult.postValue(result)
                    }
                }
            }
        }
    }


    fun updateTask(userId: String, task: Task) {
        _updateTaskResult.postValue(FirebaseResult.Loading)
        viewModelScope.launch {
            taskRepository.updateTask(userId, task).collectLatest { result ->
                _updateTaskResult.postValue(result)
                // --- Reschedule or Cancel Reminder on Success ---
                if (result is FirebaseResult.Success) {
                    Log.d(TAG, "Task updated successfully: ${task.id}")
                    // Refresh the raw tasks list to reflect the update
                    refreshTasks(userId)

                    // Reminder logic: Schedule if pending and has a future due date, otherwise cancel
                    // FIX 2: Use safe call for task.id
                    task.id?.let { taskId ->
                        if (task.status == Task.TaskStatus.PENDING.name && task.dueDate != null) {
                            val reminderTime = ReminderTimeCalculator.calculateReminderTime(task)
                            if (reminderTime != null && reminderTime.after(Date())) { // Compare with current Date
                                taskReminderScheduler.scheduleReminder(task) // scheduleReminder handles canceling old one for the same task ID
                                Log.d(TAG, "Scheduled/Rescheduled reminder after updating task: ${task.id} at ${reminderTime}")
                            } else {
                                // If reminder time is in the past or invalid, cancel any existing reminder
                                taskReminderScheduler.cancelReminder(taskId)
                                Log.d(TAG, "Cancelled reminder for task: ${task.id} (Calculated time in past or invalid)")
                            }
                        } else {
                            // Task is completed, abandoned, or due date removed - cancel reminder
                            taskReminderScheduler.cancelReminder(taskId)
                            Log.d(TAG, "Cancelled reminder after updating task: ${task.id} (Status changed or due date removed)")
                        }
                    } ?: run {
                        Log.e(TAG, "Cannot schedule/cancel reminder. Task ID is null for updated task: ${task.title}")
                    }

                } else if (result is FirebaseResult.Error) {
                    Log.e(TAG, "Error updating task, reminder scheduling/cancellation might be incorrect.", result.exception)
                }
            }
        }
    }

    fun deleteTask(userId: String, taskId: String) {
        _deleteTaskResult.postValue(FirebaseResult.Loading)
        viewModelScope.launch {
            taskRepository.deleteTask(userId, taskId).collectLatest { result ->
                _deleteTaskResult.postValue(result)
                // --- Cancel Reminder on Success ---
                if (result is FirebaseResult.Success) {
                    Log.d(TAG, "Task deleted successfully: $taskId")
                    taskReminderScheduler.cancelReminder(taskId)
                    Log.d(TAG, "Cancelled reminder after deleting task: $taskId")
                    // Refresh the raw tasks list to reflect the deletion
                    refreshTasks(userId)
                } else if (result is FirebaseResult.Error) {
                    Log.e(TAG, "Error deleting task, reminder cancellation might be incorrect.", result.exception)
                }
            }
        }
    }

    // Helper to refresh the currently loaded tasks
    // FIX 3: Change visibility to internal
    internal fun refreshTasks(userId: String) {
        viewModelScope.launch {
            // Re-trigger the appropriate load function based on current filter/category
            val filterType = currentFilterType
            val categoryName = currentCategoryName

            Log.d(TAG, "Refreshing tasks for filterType=$filterType, categoryName=$categoryName")

            // Find the current state of the filter and category
            // This requires storing the filter/category used in loadTasks
            when (filterType) {
                "inbox" -> loadTasks(userId, "inbox") // Call loadTasks directly
                "today" -> loadTasks(userId, "today")
                "week" -> loadTasks(userId, "week")
                "category" -> {
                    if (categoryName != null) loadTasks(userId, "category", categoryName)
                    else Log.e(TAG, "Cannot refresh category tasks, categoryName is null")
                }
                else -> {
                    Log.e(TAG, "Cannot refresh unknown filter type: $filterType. Defaulting to Inbox refresh.")
                    loadTasks(userId, "inbox")
                }
            }
        }
    }

    // Need to store the current filter type and category name used for loading
    private var currentFilterType: String = "inbox" // Default
    private var currentCategoryName: String? = null


    // Load functions now just set the state and call the generic loadTasks
    fun loadInboxTasks(userId: String) {
        currentFilterType = "inbox"
        currentCategoryName = null
        loadTasks(userId, currentFilterType, currentCategoryName)
    }

    fun loadTodayTasks(userId: String) {
        currentFilterType = "today"
        currentCategoryName = null
        loadTasks(userId, currentFilterType, currentCategoryName)
    }

    fun loadWeekTasks(userId: String) {
        currentFilterType = "week"
        currentCategoryName = null
        loadTasks(userId, currentFilterType, currentCategoryName)
    }

    fun loadTasksByCategory(userId: String, categoryName: String) {
        currentFilterType = "category"
        currentCategoryName = categoryName
        loadTasks(userId, currentFilterType, currentCategoryName)
    }


    companion object {
        private const val TAG = "TaskViewModel"
    }
}