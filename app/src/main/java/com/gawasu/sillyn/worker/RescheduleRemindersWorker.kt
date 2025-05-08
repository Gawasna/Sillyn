package com.gawasu.sillyn.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gawasu.sillyn.data.local.UserIdProvider // Import UserIdProvider
import com.gawasu.sillyn.data.repository.TaskRepository // Import TaskRepository
import com.gawasu.sillyn.reminder.TaskReminderScheduler // Import TaskReminderScheduler
import com.gawasu.sillyn.util.ReminderTimeCalculator
import com.gawasu.sillyn.utils.FirebaseResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first // Use first() to get a single result from Flow
import java.util.Date

@HiltWorker
class RescheduleRemindersWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskRepository: TaskRepository, // Inject Repository
    private val taskReminderScheduler: TaskReminderScheduler, // Inject Scheduler
    private val userIdProvider: UserIdProvider // Inject UserIdProvider
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "RescheduleRemindersWkr"

    override suspend fun doWork(): Result {
        Log.d(TAG, "RescheduleRemindersWorker started.")

        // Get User ID from persistent storage
        val userId = userIdProvider.getUserId()
        if (userId == null) {
            Log.e(TAG, "User ID not found. Cannot reschedule reminders.")
            return Result.failure() // Cannot proceed without user ID
        }

        return try {
            // Fetch all upcoming tasks with deadlines from the repository
            // Assuming getUpcomingTasksWithDeadlines returns a Flow of FirebaseResult<List<Task>>
            // We use .first() because we only need the current list, not observe changes
            val tasksResult = taskRepository.getUpcomingTasksWithDeadlines(userId).first()

            when (tasksResult) {
                is FirebaseResult.Success -> {
                    val tasksToReschedule = tasksResult.data.orEmpty()
                    Log.d(TAG, "Found ${tasksToReschedule.size} upcoming tasks to reschedule.")

                    // Loop through tasks and reschedule reminders
                    tasksToReschedule.forEach { task ->
                        // Check if task still has a future due date/reminder time
                        val reminderTime = ReminderTimeCalculator.calculateReminderTime(task)
                        if (reminderTime != null && reminderTime.after(System.currentTimeMillis().toDate())) {
                            taskReminderScheduler.scheduleReminder(task)
                            Log.d(TAG, "Rescheduled reminder for task: ${task.title} (ID: ${task.id})")
                        } else {
                            Log.d(TAG, "Task ${task.id} (${task.title}) is in the past or invalid, not rescheduling.")
                            // Optionally cancel any old reminder if it exists (though scheduleReminder does this too)
                            taskReminderScheduler.cancelReminder(task.id!!) // Task must have ID here
                        }
                    }
                    Log.d(TAG, "RescheduleRemindersWorker finished successfully.")
                    Result.success() // Indicate success
                }
                is FirebaseResult.Error -> {
                    Log.e(TAG, "Error fetching tasks for rescheduling: ${tasksResult.exception.message}", tasksResult.exception)
                    Result.retry() // Retry later on error
                }
                is FirebaseResult.Loading -> {
                    // This state shouldn't happen with .first(), but handle defensively
                    Log.d(TAG, "Task list is still loading for rescheduling. Should not happen with .first().")
                    Result.retry() // Maybe retry if this state indicates a temporary issue
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception during RescheduleRemindersWorker execution: ${e.message}", e)
            Result.retry() // Retry on any unexpected exception
        }
    }

    // Helper extension to convert Long millis to Date
    private fun Long.toDate(): Date = Date(this)
}