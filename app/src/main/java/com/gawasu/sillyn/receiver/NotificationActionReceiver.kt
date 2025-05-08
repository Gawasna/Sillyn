package com.gawasu.sillyn.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gawasu.sillyn.data.local.UserIdProvider // Import UserIdProvider
import com.gawasu.sillyn.data.repository.TaskRepository // Import TaskRepository
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.utils.FirebaseResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.media.MediaPlayer
import android.media.AudioManager
import com.gawasu.sillyn.R

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var taskRepository: TaskRepository
    @Inject lateinit var userIdProvider: UserIdProvider // Inject UserIdProvider

    private val TAG = "NotificationActionRcvr"

    // Define action strings (public static constants)
    companion object {
        const val ACTION_COMPLETE_TASK = "com.gawasu.sillyn.ACTION_COMPLETE_TASK"
        const val ACTION_DISMISS_NOTIFICATION = "com.gawasu.sillyn.ACTION_DISMISS_NOTIFICATION"
        // Add other actions like ACTION_SNOOZE etc.

        // Keys for Intent extras
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_USER_ID = "extra_user_id" // Pass userId if needed for repository actions
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "NotificationActionReceiver onReceive. Action: ${intent?.action}")

        val taskId = intent?.getStringExtra(EXTRA_TASK_ID)
        val notificationId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, -1) ?: -1
        // Attempt to get userId from intent extra first (sent by ReminderReceiver)
        val userId = intent?.getStringExtra(EXTRA_USER_ID) ?: userIdProvider.getUserId() // Fallback to persistent storage


        if (taskId != null && notificationId != -1 && userId != null) {
            val pendingResult: PendingResult = goAsync()

            coroutineScope.launch {
                try {
                    when (intent?.action) {
                        ACTION_COMPLETE_TASK -> {
                            Log.d(TAG, "Handling Complete Task action for task ID: $taskId")
                            // Fetch task first to get current state (optional but safer)
                            val taskResult = taskRepository.getTaskById(userId, taskId).first() // Collect only the first emitted value
                            when(taskResult) {
                                is FirebaseResult.Success -> {
                                    val task = taskResult.data
                                    if (task != null) {
                                        // Update task status to completed
                                        val updatedTask = task.copy(
                                            status = Task.TaskStatus.COMPLETED.name,
                                            // Optionally set a completion date: completedDate = Date()
                                        )
                                        taskRepository.updateTask(userId, updatedTask).first() // Perform update
                                        Log.d(TAG, "Task $taskId marked as completed.")

                                        // --- Play "ting" sound ---
                                        playCompletionSound(context)
                                        // Cancel any further scheduled reminders for this task
                                        // This requires injecting TaskReminderScheduler here, or calling a UseCase.
                                        // For now, we rely on ReminderScheduler not showing notification for non-PENDING tasks.
                                        // Proper implementation: TaskReminderScheduler.cancelReminder(taskId)
                                    } else {
                                        Log.w(TAG, "Task $taskId not found when trying to complete from notification.")
                                    }
                                }
                                is FirebaseResult.Error -> {
                                    Log.e(TAG, "Error fetching task $taskId to complete: ${taskResult.exception.message}")
                                }
                                is FirebaseResult.Loading -> { /* Should not happen with .first() */ }
                            }


                            // Dismiss the notification after action
                            dismissNotification(context, notificationId)
                        }
                        ACTION_DISMISS_NOTIFICATION -> {
                            Log.d(TAG, "Handling Dismiss Notification action for task ID: $taskId")
                            // Just dismiss the notification
                            dismissNotification(context, notificationId)
                        }
                        // Handle other actions here (e.g., SNOOZE)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception in NotificationActionReceiver coroutine: ${e.message}", e)
                } finally {
                    pendingResult.finish() // Ensure finish is called
                }
            }
        } else {
            Log.e(TAG, "NotificationActionReceiver missing Task ID, Notification ID, or User ID.")
        }
    }

    private fun playCompletionSound(context: Context) {
        var mediaPlayer: MediaPlayer? = null
        try {
            // Create MediaPlayer from raw resource
            // Using applicationContext to avoid issues if the Receiver context is short-lived
            mediaPlayer = MediaPlayer.create(context.applicationContext, R.raw.ting)

            // Optional: Set stream type (e.g., STREAM_NOTIFICATION, STREAM_ALARM, STREAM_MUSIC)
            // depending on where you want the sound volume to be controlled.
            // For a UI feedback sound, STREAM_NOTIFICATION or STREAM_SYSTEM might be appropriate.
            // However, MediaPlayer.create often sets a suitable stream type by default.
            // For simplicity, we'll use the default.

            mediaPlayer?.setOnCompletionListener { mp ->
                // Release MediaPlayer resources after playback
                mp.release()
                Log.d(TAG, "Completion sound playback finished and MediaPlayer released.")
            }
            mediaPlayer?.start() // Start playback
            Log.d(TAG, "Completion sound playback started.")

        } catch (e: Exception) {
            Log.e(TAG, "Error playing completion sound", e)
            mediaPlayer?.release() // Release resources even on error
        }
        // Note: Do not call mediaPlayer.release() immediately here, as start() is async.
        // Release must happen after playback is complete (via setOnCompletionListener) or on error.
        // If you don't use setOnCompletionListener, you risk the Receiver finishing before sound plays,
        // or resource leaks if not released.
    }

    private fun dismissNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
        Log.d(TAG, "Notification with ID $notificationId dismissed.")
    }
}