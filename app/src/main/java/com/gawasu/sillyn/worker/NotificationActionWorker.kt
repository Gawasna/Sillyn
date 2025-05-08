package com.gawasu.sillyn.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gawasu.sillyn.data.repository.TaskRepositoryInterface
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.receiver.NotificationActionReceiver
import com.gawasu.sillyn.util.NotificationHelper
import com.gawasu.sillyn.util.ReminderScheduler
import com.gawasu.sillyn.utils.FirebaseResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull

@HiltWorker
class NotificationActionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskRepository: TaskRepositoryInterface, // Inject Repository
    private val notificationHelper: NotificationHelper, // Inject NotificationHelper
    private val reminderScheduler: ReminderScheduler // Inject Scheduler (needed to cancel alarm if task completed)
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "ActionWorker"

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(ReminderScheduler.EXTRA_TASK_ID)
        val action = inputData.getString("action")

        if (taskId.isNullOrBlank() || action.isNullOrBlank()) {
            Log.e(TAG, "Worker received missing task ID or action.")
            return Result.failure()
        }

        Log.d(TAG, "Handling action: $action for Task ID: $taskId")

        // Fetch the task data if needed for the action
        // val taskResult = taskRepository.getTaskById(taskId).firstOrNull()
        // val task = (taskResult as? FirebaseResult.Success)?.data
        // if (task == null) {
        //     Log.w(TAG, "Task $taskId not found for action $action. Cannot perform action.")
        //     notificationHelper.cancelNotification(taskId) // Cancel notification if task is gone
        //     return Result.failure() // Or success depending on how you define failure
        // }

        val userId: String? = "current_user_id" // <<< THAY THẾ BẰNG LOGIC THỰC TẾ CỦA BẠN
        if (userId.isNullOrBlank()) {
            Log.w(TAG, "No user ID found. Cannot perform action $action for task $taskId.")
            // notificationHelper.cancelNotification(taskId)
            return Result.failure()
        }


        when (action) {
            NotificationActionReceiver.ACTION_COMPLETE_TASK -> {
                Log.d(TAG, "Marking task $taskId as completed.")
                // TODO: Call Repository to update task status to COMPLETED
                // val updateResult = taskRepository.updateTaskStatus(userId, taskId, Task.TaskStatus.COMPLETED.name).firstOrNull()
                // if (updateResult is FirebaseResult.Success) {
                //     Log.d(TAG, "Task $taskId marked as completed successfully.")
                //     // If task was repeating, make sure its future alarms are canceled
                //     reminderScheduler.cancelReminder(taskId) // Cancel any scheduled alarm for this task ID
                //     notificationHelper.cancelNotification(taskId) // Hide the notification
                //     return Result.success()
                // } else {
                //     Log.e(TAG, "Failed to mark task $taskId as completed: ${updateResult}")
                //     // Consider showing a notification error or retrying
                //     return Result.failure()
                // }

                // --- Placeholder: Simulate success ---
                Log.d(TAG, "Placeholder: Task $taskId marked as completed.")
                reminderScheduler.cancelReminder(taskId)
                notificationHelper.cancelNotification(taskId)
                return Result.success()
            }
            NotificationActionReceiver.ACTION_DISMISS_NOTIFICATION -> {
                Log.d(TAG, "Dismissing notification for task $taskId.")
                // Only cancel the notification. No change to task status.
                notificationHelper.cancelNotification(taskId)
                return Result.success()
            }
            // TODO: Add logic for other actions like SNOOZE
            // SNOOZE would involve calculating a new reminder time and calling reminderScheduler.scheduleReminder() again.
            // Example:
            // "ACTION_SNOOZE_15_MIN" -> {
            //     val newTask = task.copy(dueDate = Date(System.currentTimeMillis() + 15 * 60 * 1000))
            //     reminderScheduler.scheduleReminder(newTask)
            //     notificationHelper.cancelNotification(taskId)
            //     return Result.success()
            // }
            else -> {
                Log.w(TAG, "Unknown action received: $action for task $taskId.")
                return Result.failure()
            }
        }
    }

    // TODO: Cần thêm phương thức updateTaskStatus(userId: String, taskId: String, status: String) vào Repository/FirestoreDataSource
    // Ví dụ trong TaskRepositoryInterface:
    // fun updateTaskStatus(userId: String, taskId: String, status: String): Flow<FirebaseResult<Void>>

    // Ví dụ trong FirestoreDataSource:
    // fun updateTaskStatus(userId: String, taskId: String, status: String): Flow<FirebaseResult<Void>> {
    //    return flow {
    //        val docRef = firestore.collection("users").document(userId).collection("tasks").document(taskId)
    //        docRef.update("status", status)
    //            .addOnSuccessListener { emit(FirebaseResult.Success(null)) }
    //            .addOnFailureListener { e -> emit(FirebaseResult.Error(e)) }
    //    }
    //    as Flow<FirebaseResult<Void>> // Explicit cast for Flow
    // }
}