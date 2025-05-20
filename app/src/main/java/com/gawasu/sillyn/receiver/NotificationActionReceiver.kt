package com.gawasu.sillyn.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gawasu.sillyn.data.local.UserIdProvider
import com.gawasu.sillyn.data.repository.TaskRepository
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
import java.util.NoSuchElementException

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var taskRepository: TaskRepository
    @Inject lateinit var userIdProvider: UserIdProvider

    private val TAG = "NotificationActionRcvr"

    companion object {
        const val ACTION_COMPLETE_TASK = "com.gawasu.sillyn.ACTION_COMPLETE_TASK"
        const val ACTION_DISMISS_NOTIFICATION = "com.gawasu.sillyn.ACTION_DISMISS_NOTIFICATION"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_USER_ID = "extra_user_id"
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "NotificationActionReceiver onReceive. Action: ${intent?.action}")

        val taskId = intent?.getStringExtra(EXTRA_TASK_ID)
        val notificationId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, -1) ?: -1
        val userId = intent?.getStringExtra(EXTRA_USER_ID) ?: userIdProvider.getUserId()


        if (taskId != null && notificationId != -1 && userId != null) {
            val pendingResult: PendingResult = goAsync()

            coroutineScope.launch {
                try {
                    when (intent?.action) {
                        ACTION_COMPLETE_TASK -> {
                            Log.d(TAG, "Handling Complete Task action for task ID: $taskId")

                            // --- Try/Catch block for fetching task ---
                            val taskResult = try {
                                taskRepository.getTaskById(userId, taskId).first()
                            } catch (e: NoSuchElementException) {
                                Log.w(TAG, "Task $taskId flow completed without emitting (NoSuchElementException). Assuming task not found.", e)
                                FirebaseResult.Success(null) // Treat as task not found
                            } catch (e: Exception) {
                                Log.e(TAG, "Error fetching task $taskId: ${e.message}", e)
                                FirebaseResult.Error(e)
                            }
                            // ---------------------------------------------

                            when(taskResult) {
                                is FirebaseResult.Success -> {
                                    val task = taskResult.data
                                    if (task != null) {
                                        Log.d(TAG, "Task $taskId fetched successfully.")
                                        val updatedTask = task.copy(
                                            status = Task.TaskStatus.COMPLETED.name,
                                        )

                                        // --- ADDED: Try/Catch around updateTask().first() ---
                                        try {
                                            // Attempt to update the task. This is where the *second* NoSuchElementException might occur.
                                            taskRepository.updateTask(userId, updatedTask).first()
                                            Log.d(TAG, "Task $taskId marked as completed (update flow emitted).")
                                        } catch (e: NoSuchElementException) {
                                            // Update flow completed without emitting. Log a warning, but proceed,
                                            // assuming the update operation itself was initiated successfully.
                                            Log.w(TAG, "Update task flow for $taskId completed without emitting (NoSuchElementException). Assuming update was initiated.", e)
                                        } catch (e: Exception) {
                                            // Catch any other exceptions during the update process
                                            Log.e(TAG, "Error calling updateTask($taskId): ${e.message}", e)
                                            // Log the error but still proceed to play sound/dismiss,
                                            // as the update might still happen or this is a final release minimal change.
                                            // If update failure should *stop* sound/dismiss, call pendingResult.finish() here.
                                            // For now, we proceed.
                                        }
                                        // ------------------------------------------------------

                                        // These calls should happen regardless of whether updateTask().first() emitted or threw NoSuchElementException
                                        // playCompletionSound will call pendingResult.finish() after sound playback or on error
                                        playCompletionSound(context, pendingResult)
                                        // Dismiss the notification
                                        dismissNotification(context, notificationId)

                                        // Cancel any further scheduled reminders (If implemented)

                                    } else {
                                        // Task was null (either not found initially or NoSuchElementException was caught during fetch)
                                        Log.w(TAG, "Task $taskId not found or fetch failed gracefully. Finishing receiver.")
                                        pendingResult.finish() // Finish here if task was null
                                    }
                                }
                                is FirebaseResult.Error -> {
                                    Log.e(TAG, "Error fetching task $taskId... Finishing receiver.", taskResult.exception)
                                    pendingResult.finish()
                                }
                                is FirebaseResult.Loading -> {
                                    // Should not happen with .first() or the catch blocks above
                                    Log.d(TAG, "Unexpected Loading state after fetch for task $taskId. Finishing receiver.")
                                    pendingResult.finish()
                                }
                            }

                        }
                        ACTION_DISMISS_NOTIFICATION -> {
                            Log.d(TAG, "Handling Dismiss Notification action for task ID: $taskId. Dismissing notification and finishing receiver.")
                            dismissNotification(context, notificationId)
                            pendingResult.finish() // Finish directly for dismiss action
                        }
                        else -> {
                            Log.w(TAG, "Unhandled action: ${intent?.action}. Finishing receiver.")
                            pendingResult.finish()
                        }
                    }
                } catch (e: Exception) {
                    // This outer catch block will catch any unhandled exception *after* the inner logic,
                    // including potential exceptions not caught by the inner updateTask try/catch.
                    Log.e(TAG, "Unexpected exception in NotificationActionReceiver: ${e.message}", e)
                    pendingResult.finish() // Ensure finish is called on any unexpected exception
                }
            }
        } else {
            Log.e(TAG, "NotificationActionReceiver missing Task ID ($taskId), Notification ID ($notificationId), or User ID ($userId). Cannot process action. Exiting.")
        }
    }

    // playCompletionSound function remains the same, manages pendingResult.finish()
    private fun playCompletionSound(context: Context, pendingResult: PendingResult) {
        var mediaPlayer: MediaPlayer? = null
        try {
            mediaPlayer = MediaPlayer.create(context.applicationContext, R.raw.ting)

            mediaPlayer?.setOnCompletionListener { mp ->
                Log.d(TAG, "Completion sound playback finished.")
                mp.release()
                Log.d(TAG, "MediaPlayer released.")
                pendingResult.finish() // Call finish() here after sound is done
                Log.d(TAG, "PendingResult.finish() called after sound playback.")
            }

            mediaPlayer?.start()
            Log.d(TAG, "Completion sound playback started.")

        } catch (e: Exception) {
            Log.e(TAG, "Error playing completion sound", e)
            mediaPlayer?.release()
            pendingResult.finish() // Call finish() on error
            Log.d(TAG, "PendingResult.finish() called after sound playback error.")
        }
    }

    private fun dismissNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
        Log.d(TAG, "Notification with ID $notificationId dismissed.")
    }
}