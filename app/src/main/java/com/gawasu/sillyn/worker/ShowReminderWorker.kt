package com.gawasu.sillyn.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gawasu.sillyn.data.repository.TaskRepositoryInterface
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.util.NotificationHelper
import com.gawasu.sillyn.util.ReminderScheduler
import com.gawasu.sillyn.utils.FirebaseResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar
import java.util.Date

@HiltWorker
class ShowReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskRepository: TaskRepositoryInterface, // Inject Repository
    private val notificationHelper: NotificationHelper, // Inject NotificationHelper
    private val reminderScheduler: ReminderScheduler // Inject ReminderScheduler
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "ShowReminderWorker"

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(ReminderScheduler.EXTRA_TASK_ID)
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        // Retrieve minimal data passed from Receiver (optional, primarily for logs/fallback)
        val taskTitleFallback = inputData.getString(ReminderScheduler.EXTRA_TASK_TITLE)
        val taskDeadlineFallback = inputData.getLong(ReminderScheduler.EXTRA_TASK_DEADLINE, -1L)

        if (taskId.isNullOrBlank()) {
            Log.e(TAG, "Worker received no task ID.")
            return Result.failure()
        }

        if (userId.isNullOrBlank()) {
            Log.e(TAG, "No Firebase user available. Aborting reminder.")
            return Result.failure()
        }

        Log.d(TAG, "ShowReminderWorker starting for Task ID: $taskId")

        // Fetch the latest task data from the repository
        // Assuming you have a method like getTaskById in your repository
        // If repository only provides streams, you might need to adapt or add a suspend function
        // For demonstration, let's assume a direct fetch method or adapt the Flow
        val taskResult = taskRepository.getTaskById(userId, taskId).firstOrNull() // Cần thêm getTaskById vào Repo
        val task = (taskResult as? FirebaseResult.Success)?.data

        if (task == null || task.status != Task.TaskStatus.PENDING.name) {
            Log.d(TAG, "Task $taskId not found, not pending, or completed. Not showing reminder.")
            // Ensure alarm is cancelled if task is not pending
            reminderScheduler.cancelReminder(taskId)
            return Result.success() // Task not pending, worker finished successfully
        }

        // --- Show the notification ---
        val title = task.title.ifBlank { taskTitleFallback ?: "Nhiệm vụ" }
        val content = task.description ?: task.title // Use description or title as content
        val notificationContent = if (task.dueDate != null) {
            "Đến hạn: ${java.text.SimpleDateFormat("HH:mm dd/MM", java.util.Locale.getDefault()).format(task.dueDate)}"
        } else {
            "Nhiệm vụ cần hoàn thành"
        }

        notificationHelper.showReminderNotification(
            taskId,
            title,
            notificationContent
            // Pass more data if notification actions need it immediately (e.g., Task object Parcelable)
            // But fetching in NotificationActionWorker is safer if data is complex
        )
        Log.d(TAG, "Notification shown for task $taskId")

        // --- Handle repeating tasks ---
        if (task.repeatMode != Task.RepeatMode.NONE.name) {
            // TODO: Implement logic to calculate the next reminder time based on task.repeatMode
            // This is complex and depends on the repeat rule details (e.g., repeat every N days, specific days of week, etc.)
            // The logic should calculate the *next* valid date/time >= System.currentTimeMillis()
            val nextReminderTime = calculateNextReminderTime(task) // Cần implement hàm này

            if (nextReminderTime != null) {
                // Update the task in Firestore/local DB with the new next reminder time
                // Or store the next reminder time separately if the Task object structure should not change
                // For simplicity here, let's assume you might update task.dueDate to the next occurrence
                // taskRepository.updateTaskNextReminderTime(taskId, nextReminderTime) // Cần thêm hàm này

                // Schedule the next reminder
                // You might need to create a new Task object or update the existing one with the new dueDate
                // and call scheduleReminder with that updated/new task object.
                // For simplicity, let's assume you just need the ID and next time to schedule
                val nextTaskOccurrence = task.copy(dueDate = Date(nextReminderTime)) // Create a copy for scheduling
                reminderScheduler.scheduleReminder(nextTaskOccurrence)
                Log.d(TAG, "Scheduled next reminder for repeating task ${task.id} at ${java.util.Date(nextReminderTime)}")
            } else {
                Log.d(TAG, "Task ${task.id} is repeating but could not calculate next reminder time.")
                // Optional: Cancel alarm if next time cannot be determined (e.g., reached end of repeats)
            }
        }


        return Result.success()
    }

    // TODO: Implement calculateNextReminderTime based on Task.repeatMode
    // This function is crucial and complex for repeating tasks.
    // It needs to consider: NONE, DAILY, WEEKLY, MONTHLY, YEARLY, NUMBER, DAY_IN_WEEK.
    // Example for DAILY: Add 1 day to the current reminder time until it's in the future.
    // Example for WEEKLY (specific days): Find the next closest day of the week.
    private fun calculateNextReminderTime(task: Task): Long? {
        // This is a placeholder. Implement based on your Task.repeatMode enum logic.
        // Example simple daily repeat logic:
        if (task.repeatMode == Task.RepeatMode.DAILY.name && task.dueDate != null) {
            val calendar = Calendar.getInstance().apply {
                time = task.dueDate // Start from the last scheduled or original due date
                add(Calendar.DAY_OF_YEAR, 1) // Add one day
            }
            // Keep adding days until the next reminder is in the future
            while (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            return calendar.timeInMillis
        }
        // TODO: Add logic for other repeat modes

        Log.w(TAG, "calculateNextReminderTime: Repeat mode ${task.repeatMode} not fully implemented or task.dueDate is null.")
        return null // No logic for this repeat mode or cannot calculate
    }
}