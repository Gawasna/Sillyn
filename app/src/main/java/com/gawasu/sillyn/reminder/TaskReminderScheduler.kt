package com.gawasu.sillyn.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.receiver.ReminderReceiver
import com.gawasu.sillyn.util.ReminderTimeCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // Đảm bảo chỉ có 1 instance
class TaskReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager // Inject AlarmManager
) {

    private val TAG = "TaskReminderScheduler"

    // Action cho Intent trigger ReminderReceiver
    private val ACTION_SHOW_REMINDER = "com.gawasu.sillyn.ACTION_SHOW_REMINDER"

    // Key cho Task ID trong Intent extras
    private val EXTRA_TASK_ID = "extra_task_id"

    /**
     * Schedules a reminder for a given task if it has a future due date and reminder type.
     * Cancels any existing reminder for the same task first.
     */
    fun scheduleReminder(task: Task) {
        // Ensure task has an ID and a due date
        val taskId = task.id ?: run {
            Log.e(TAG, "Cannot schedule reminder for task without ID")
            return
        }
        val dueDate = task.dueDate ?: run {
            Log.d(TAG, "Task '${task.title}' has no due date, not scheduling reminder.")
            cancelReminder(taskId) // Cancel any old reminder if due date was removed
            return
        }

        // Ensure the task is still pending
        if (task.status != Task.TaskStatus.PENDING.name) {
            Log.d(TAG, "Task '${task.title}' status is not PENDING, not scheduling reminder.")
            cancelReminder(taskId) // Cancel if task is completed/abandoned/etc.
            return
        }

        // Calculate the actual time to trigger the alarm
        val reminderTime = ReminderTimeCalculator.calculateReminderTime(task)

        if (reminderTime == null) {
            Log.d(TAG, "Calculated reminder time for '${task.title}' is in the past or invalid, not scheduling.")
            cancelReminder(taskId) // Cancel any old reminder if the new time is invalid
            return
        }

        // First, cancel any existing alarm for this task to avoid duplicates
        cancelReminder(taskId)
        Log.d(TAG, "Cancelled potentially existing reminder for task ID: $taskId")


        // Create the Intent for the ReminderReceiver
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SHOW_REMINDER // Set a custom action
            putExtra(EXTRA_TASK_ID, taskId) // Pass the task ID
            // Add other extras if needed for Receiver without fetching Task from Repo
            // However, fetching in Receiver is safer as Task might change after scheduling.
        }

        // Create a unique PendingIntent for this task ID
        // Request code should be unique per task. Using task ID hash or just a generated int is common.
        // A simple hash of the task ID string is often sufficient.
        val requestCode = taskId.hashCode() // Unique request code based on task ID

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode, // Unique request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or // Update existing pending intent if it exists
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0 // Required for API 23+
        )

        // Schedule the alarm
        val triggerTimeMillis = reminderTime.time
        Log.d(TAG, "Scheduling reminder for task '${task.title}' (ID: $taskId) at ${Date(triggerTimeMillis)}")

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // Use setExactAndAllowWhileIdle for API 31+
                // Requires SCHEDULE_EXACT_ALARM permission (handled by PermissionsFragment)
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, // Wake up the device
                        triggerTimeMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled exact alarm for task ID: $taskId (API 31+)")
                } else {
                    Log.e(TAG, "Exact alarm permission not granted, cannot schedule for task ID: $taskId")
                    // Optionally inform the user they need to grant the permission
                    // showPermissionNeededNotification() // Maybe later
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                // Use setExactAndAllowWhileIdle for API 23-30 (Doze mode)
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled exact alarm for task ID: $taskId (API 23-30)")
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
                // Use setExact for API 19-22
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled exact alarm for task ID: $taskId (API 19-22)")
            }
            else -> {
                // Use set for older APIs
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled alarm for task ID: $taskId (API < 19)")
            }
        }
    }

    /**
     * Cancels the reminder for a given task ID.
     */
    fun cancelReminder(taskId: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SHOW_REMINDER
            // The extras don't strictly matter for cancellation, but using the same
            // intent structure as when setting it is good practice.
            putExtra(EXTRA_TASK_ID, taskId)
        }

        // Use the same request code as when scheduling
        val requestCode = taskId.hashCode()

        // The PendingIntent flags must match the ones used when setting the alarm EXACTLY for cancellation to work reliably.
        // This is a common pitfall. FLAG_NO_CREATE is important here.
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or // Don't create if it doesn't exist
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Cancelled reminder for task ID: $taskId")
            pendingIntent.cancel() // Also cancel the PendingIntent itself
        } else {
            Log.d(TAG, "No existing reminder found to cancel for task ID: $taskId")
        }
    }
}
