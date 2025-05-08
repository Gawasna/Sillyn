package com.gawasu.sillyn.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawasu.sillyn.data.repository.TaskRepository // Đảm bảo TaskRepository có các hàm cần thiết
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.reminder.TaskReminderScheduler
import com.gawasu.sillyn.util.ReminderTimeCalculator
import com.gawasu.sillyn.utils.FirebaseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val taskReminderScheduler: TaskReminderScheduler
) : ViewModel() {

    private val _tasks = MutableLiveData<FirebaseResult<List<Task>>>()
    val tasks: LiveData<FirebaseResult<List<Task>>> = _tasks

    //private val _addTaskResult = MutableLiveData<FirebaseResult<Void>>()
    //val addTaskResult: LiveData<FirebaseResult<Void>> = _addTaskResult

    // Thay đổi kiểu LiveData từ Void sang Unit
    private val _addTaskResult = MutableLiveData<FirebaseResult<Void>>()
    val addTaskResult: LiveData<FirebaseResult<Void>> = _addTaskResult

    private val _updateTaskResult = MutableLiveData<FirebaseResult<Void>>()
    val updateTaskResult: LiveData<FirebaseResult<Void>> = _updateTaskResult

    private val _deleteTaskResult = MutableLiveData<FirebaseResult<Void>>()
    val deleteTaskResult: LiveData<FirebaseResult<Void>> = _deleteTaskResult

    fun getTasks(userId: String) {
        viewModelScope.launch {
            taskRepository.getTasks(userId).collectLatest { result ->
                _tasks.postValue(result)
            }
        }
    }

    // Hàm tải tất cả tasks (Inbox)
    fun loadInboxTasks(userId: String) {
        viewModelScope.launch {
            taskRepository.getTasks(userId) // Giả định getTasks() trong Repository lấy tất cả
                .onStart { _tasks.postValue(FirebaseResult.Loading) }
                //.catch { e -> _tasks.postValue(FirebaseResult.Error(e)) }
                .collectLatest { result -> _tasks.postValue(result) } // Giả định result từ repo đã là FirebaseResult
        }
    }

    // Hàm tải tasks cho ngày hôm nay
    fun loadTodayTasks(userId: String) {
        viewModelScope.launch {
            taskRepository.getTodayTasks(userId) // Cần thêm hàm này vào TaskRepository
                .onStart { _tasks.postValue(FirebaseResult.Loading) }
                //.catch { e -> _tasks.postValue(FirebaseResult.Error(e)) }
                .collectLatest { result -> _tasks.postValue(result) }
        }
    }

    // Hàm tải tasks cho tuần này (7 ngày tới)
    fun loadWeekTasks(userId: String) {
        viewModelScope.launch {
            taskRepository.getWeekTasks(userId) // Cần thêm hàm này vào TaskRepository
                .onStart { _tasks.postValue(FirebaseResult.Loading) }
                //.catch { e -> _tasks.postValue(FirebaseResult.Error(e)) }
                .collectLatest { result -> _tasks.postValue(result) }
        }
    }

    // Hàm tải tasks theo Category ID
    fun loadTasksByCategory(userId: String, categoryName: String) {
        viewModelScope.launch {
            taskRepository.getTasksByCategory(userId, categoryName) // Cần thêm hàm này vào TaskRepository
                .onStart { _tasks.postValue(FirebaseResult.Loading) }
                //.catch { e -> _tasks.postValue(FirebaseResult.Error(e)) }
                .collectLatest { result -> _tasks.postValue(result) }
        }
    }

    fun addTask(userId: String, task: Task) {
        // Optional: báo loading trước khi gọi repo
        _addTaskResult.postValue(FirebaseResult.Loading)

        viewModelScope.launch {
            // taskRepository.addTask(userId, task) hiện trả về Flow<FirebaseResult<Task>>
            taskRepository.addTask(userId, task).collectLatest { result ->
                when(result) {
                    is FirebaseResult.Success -> {
                        val addedTask = result.data // addedTask bây giờ là đối tượng Task đã có ID từ Firestore
                        Log.d(TAG, "Task added successfully with ID: ${addedTask.id}")

                        // --- Map Success<Task> to Success<Void> for the UI LiveData ---
                        // Đây là phần khắc phục vấn đề kiểu dữ liệu.
                        // Chúng ta tạo một Success với dữ liệu null, và ép kiểu thành FirebaseResult<Void>.
                        @Suppress("UNCHECKED_CAST") // Cần suppress cảnh báo này vì nó liên quan đến Java/Kotlin interop với Void
                        val uiSuccessResult = FirebaseResult.Success(null) as FirebaseResult<Void>
                        _addTaskResult.postValue(uiSuccessResult)


                        // --- Schedule Reminder using the actual addedTask object ---
                        // Logic lên lịch nhắc nhở sử dụng đối tượng Task đầy đủ từ repository
                        if (addedTask.status == Task.TaskStatus.PENDING.name && addedTask.dueDate != null) {
                            // Thêm kiểm tra nhỏ để đảm bảo thời điểm nhắc nhở tính được không quá khứ xa
                            val reminderTime = ReminderTimeCalculator.calculateReminderTime(addedTask)
                            if (reminderTime != null && reminderTime.after(System.currentTimeMillis().toDate())) {
                                taskReminderScheduler.scheduleReminder(addedTask)
                                Log.d(TAG, "Scheduled reminder for new task: ${addedTask.id}")
                            } else {
                                Log.d(TAG, "New task ${addedTask.id} has reminder time in the past or invalid. Not scheduling.")
                            }
                        } else {
                            Log.d(TAG, "New task ${addedTask.id} does not meet scheduling criteria (not pending or no due date).")
                        }
                    }
                    is FirebaseResult.Error -> {
                        Log.e(TAG, "Error adding task: ${result.exception.message}", result.exception)
                        // Trạng thái Error<Nothing> tự tương thích với FirebaseResult<Void>
                        _addTaskResult.postValue(result)
                    }
                    is FirebaseResult.Loading -> {
                        // Repository's addTask shouldn't emit Loading typically, but handle defensively
                        // Trạng thái Loading<Nothing> tự tương thích với FirebaseResult<Void>
                        _addTaskResult.postValue(result)
                    }
                }
            }
        }
    }
    private fun Long.toDate(): Date = Date(this)

    fun updateTask(userId: String, task: Task) {
        viewModelScope.launch {
            taskRepository.updateTask(userId, task).collectLatest { result ->
                _updateTaskResult.postValue(result)
                // --- Reschedule or Cancel Reminder on Success ---
                if (result is FirebaseResult.Success) {
                    if (task.status == Task.TaskStatus.PENDING.name && task.dueDate != null) {
                        // Task is still pending and has a due date, try to schedule/reschedule
                        taskReminderScheduler.scheduleReminder(task) // scheduleReminder handles canceling old one
                        Log.d(TAG, "Scheduled/Rescheduled reminder after updating task: ${task.id}")
                    } else {
                        // Task is completed, abandoned, or due date removed - cancel reminder
                        taskReminderScheduler.cancelReminder(task.id!!) // task.id should not be null for update
                        Log.d(TAG, "Cancelled reminder after updating task: ${task.id} (Status changed or due date removed)")
                    }
                } else if (result is FirebaseResult.Error) {
                    Log.e(TAG, "Error updating task, reminder scheduling/cancellation might be incorrect.", result.exception)
                }
            }
        }
    }

    fun deleteTask(userId: String, taskId: String) {
        viewModelScope.launch {
            taskRepository.deleteTask(userId, taskId).collectLatest { result ->
                _deleteTaskResult.postValue(result)
                // --- Cancel Reminder on Success ---
                if (result is FirebaseResult.Success) {
                    taskReminderScheduler.cancelReminder(taskId)
                    Log.d(TAG, "Cancelled reminder after deleting task: $taskId")
                } else if (result is FirebaseResult.Error) {
                    Log.e(TAG, "Error deleting task, reminder cancellation might be incorrect.", result.exception)
                }
            }
        }
    }

    companion object {
        private const val TAG = "TaskViewModel"
    }
}