package com.gawasu.sillyn.receiver

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes // Import AudioAttributes
import android.media.AudioManager // Import AudioManager (optional, for stream type)
import android.net.Uri // Import Uri
import android.os.Build
import android.provider.Settings // Import Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gawasu.sillyn.R // Import your R file
import com.gawasu.sillyn.data.local.UserIdProvider // Import UserIdProvider
import com.gawasu.sillyn.data.repository.TaskRepository // Import TaskRepository
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.util.ReminderTimeCalculator // Import ReminderTimeCalculator
import com.gawasu.sillyn.utils.FirebaseResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlinx.coroutines.flow.first // Import first

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    // Inject dependencies using Hilt
    @Inject lateinit var taskRepository: TaskRepository
    @Inject lateinit var userIdProvider: UserIdProvider // Inject UserIdProvider

    private val TAG = "ReminderReceiver"
    private val NOTIFICATION_CHANNEL_ID = "task_reminder_channel"
    private val NOTIFICATION_CHANNEL_NAME = "Task Reminders"
    private val NOTIFICATION_CHANNEL_DESCRIPTION = "Notifications for upcoming and overdue tasks"

    // Keys for Intent extras (should match keys used when creating PendingIntents)
    private val EXTRA_TASK_ID = "extra_task_id"

    // CoroutineScope for background work in onReceive
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "ReminderReceiver onReceive. Action: ${intent?.action}")

        if (intent?.action == "com.gawasu.sillyn.ACTION_SHOW_REMINDER") { // Match the action used by scheduler
            val taskId = intent.getStringExtra(EXTRA_TASK_ID)
            if (taskId != null) {
                // Get User ID from persistent storage
                val userId = userIdProvider.getUserId()
                if (userId != null) {
                    // Important: Use goAsync() to tell the system we need more time
                    // for background work (fetching task from repo) than allowed for standard receivers.
                    // The BroadcastReceiver.PendingResult is managed by the coroutine scope.
                    val pendingResult: PendingResult = goAsync()

                    coroutineScope.launch {
                        try {
                            // Fetch the task details from the repository
                            // Use .first() to get the latest value and stop observing
                            val taskResult = taskRepository.getTaskById(userId, taskId).first()
                            when (taskResult) {
                                is FirebaseResult.Loading -> {
                                    // Handle loading if necessary (e.g., show a placeholder notification)
                                    // For .first(), this state might be skipped or short-lived
                                    Log.d(TAG, "Loading task $taskId for reminder...")
                                }
                                is FirebaseResult.Success -> {
                                    val task = taskResult.data
                                    // Only show notification if task exists AND is still PENDING
                                    if (task != null && task.status == Task.TaskStatus.PENDING.name) {
                                        Log.d(TAG, "Task $taskId loaded successfully. Showing notification.")
                                        showNotification(context, task, userId) // Pass userId for actions
                                    } else {
                                        Log.d(TAG, "Task $taskId not found, completed, or abandoned. Not showing reminder.")
                                        // In a real app, you might cancel the alarm here if task is not PENDING.
                                    }
                                    // Finish the async operation once data is processed
                                    pendingResult.finish()
                                }
                                is FirebaseResult.Error -> {
                                    //Log.e(TAG, "Error fetching task $taskId for reminder: ${result.exception.message}", result.exception)
                                    // Finish the async operation on error
                                    pendingResult.finish()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception in ReminderReceiver coroutine: ${e.message}", e)
                            pendingResult.finish() // Ensure finish is called on exception
                        }
                    }
                } else {
                    Log.e(TAG, "User ID not found for reminder. Cannot fetch task.")
                }
            } else {
                Log.e(TAG, "Reminder Intent missing Task ID extra.")
            }
        }
    }

    private fun showNotification(context: Context, task: Task, userId: String) {
        // Ensure the channel is created/updated before showing the notification
        createNotificationChannel(context, task.priority) // Pass priority to channel creation if needed for sound setting

        // Generate a unique notification ID based on task ID
        val notificationId = task.id.hashCode()

        // --- Create PendingIntents for Notification Actions ---
        // (Code for completePendingIntent, dismissPendingIntent, completeAction, dismissAction remains the same)
        // Action: Complete Task
        val completeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_COMPLETE_TASK
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, task.id)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId) // Pass notification ID to dismiss it
            putExtra(NotificationActionReceiver.EXTRA_USER_ID, userId) // Pass User ID
        }
        val completePendingIntent: PendingIntent = PendingIntent.getBroadcast(
            context,
            // Request code must be unique per action type and task ID combination
            (task.id.hashCode() + NotificationActionReceiver.ACTION_COMPLETE_TASK.hashCode()),
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val completeAction = NotificationCompat.Action.Builder(
            R.drawable.baseline_check_box_outline_blank_24, // Replace with your icon
            context.getString(R.string.action_complete), // Need "Hoàn thành" string
            completePendingIntent
        ).build()

        // Action: Dismiss Notification
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS_NOTIFICATION
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, task.id) // Optional, but good practice
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId) // Pass notification ID to dismiss it
            // No need for userId here if only dismissing
        }
        val dismissPendingIntent: PendingIntent = PendingIntent.getBroadcast(
            context,
            // Request code must be unique per action type and task ID combination
            (task.id.hashCode() + NotificationActionReceiver.ACTION_DISMISS_NOTIFICATION.hashCode()),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val dismissAction = NotificationCompat.Action.Builder(
            R.drawable.info_circle_svgrepo_com_p, // Replace with your icon
            context.getString(R.string.action_dismiss), // Need "Bỏ qua" string
            dismissPendingIntent
        ).build()

        // TODO: Add Snooze Action

        // --- Build Notification Content ---
        // (Code for notificationTitle, notificationText, contentIntent, contentPendingIntent remains the same)
        val timeDifferenceLabel = ReminderTimeCalculator.getTimeDifferenceLabel(task.reminderType, context)
        val title = task.title
        val notificationTitle = if (timeDifferenceLabel.isNotBlank()) {
            context.getString(R.string.notification_title_early, title, timeDifferenceLabel) // "Nhiệm vụ: {title} sau {timeDiff} nữa"
        } else {
            context.getString(R.string.notification_title_ontime, title) // "Nhắc nhở: {title} đến hạn"
        }

        val dueDateText = task.dueDate?.let {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            context.getString(R.string.notification_duedate, dateFormat.format(it)) // "Đến hạn: {date}"
        } ?: ""

        val priorityText = when (task.priority) {
            Task.Priority.LOW.name -> context.getString(R.string.priority_low)
            Task.Priority.MEDIUM.name -> context.getString(R.string.priority_medium)
            Task.Priority.HIGH.name -> context.getString(R.string.priority_high)
            else -> context.getString(R.string.priority_none)
        }
        val priorityLabel = context.getString(R.string.notification_priority, priorityText) // "Độ ưu tiên: {priority}"

        // --- Add Description if present ---
        val descriptionText = if (!task.description.isNullOrBlank()) {
            task.description
        } else {
            ""
        }

        val notificationTextParts = listOf(
            dueDateText,
            priorityLabel,
            descriptionText.orEmpty()
        ).filter { it.isNotBlank() }
        val notificationText = notificationTextParts.joinToString("\n")

        val contentIntent = context.packageManager.getLaunchIntentForPackage(context.getPackageName())
        contentIntent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Clear activity stack

        val contentPendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            // Request code for content intent, unique per task ID
            task.id.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )


        // Build the notification
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.bell_svgrepo_com) // Replace with your app's notification icon
            .setContentTitle(notificationTitle)
            .setContentText(notificationText) // Set the combined text
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText)) // Allow expanding the text
            .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority for urgent tasks
            .setAutoCancel(true) // Automatically dismiss the notification when clicked
            .setContentIntent(contentPendingIntent) // Set intent for clicking body
            .addAction(completeAction) // Add action buttons
            .addAction(dismissAction)
            // Add more actions here (e.g., Snooze)
            .setCategory(NotificationCompat.CATEGORY_REMINDER) // Hint to the system about the notification type


        // --- Set Default Sound, Vibrate, Lights ---
        // Note: This sets the defaults on the *builder*.
        // The channel configuration in createNotificationChannel ultimately controls
        // whether sound/vibrate/lights are enabled for the channel itself.
        // We set it here as a good practice, but the channel setting is dominant.
        builder.setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)

        // --- ADDED: Override sound/vibration/lights settings on the builder if priority is NONE ---
        if (task.priority == Task.Priority.NONE.name) {
            Log.d(TAG, "Task priority is NONE. Silencing notification sound and disabling vibrate/lights.")
            // Explicitly set sound/vibration/lights to null/0 for this specific notification instance
            builder.setSound(null)
            builder.setVibrate(longArrayOf(0)) // Disable vibration
            builder.setLights(0, 0, 0) // Disable lights
        }
        // ---------------------------------------------------------


        // Check for POST_NOTIFICATIONS permission on API 33+ before showing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted, cannot show notification for task ID: ${task.id}")
                // Optionally show a less intrusive notification or log a warning
                return
            }
        }

        // Show the notification
        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
            Log.d(TAG, "Notification shown for task ID: ${task.id}, Notification ID: $notificationId")
        }
    }

    // Create Notification Channel for Android 8.0 (Oreo) and higher
    // We pass priority here, but the primary sound configuration should be on the channel itself.
    // We will configure the channel to have the default sound.
    private fun createNotificationChannel(context: Context, taskPriority: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Importance level: High is suitable for reminders that should make sound and appear prominently.
            val importance = NotificationManager.IMPORTANCE_HIGH

            // Get the NotificationManager service
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Check if the channel already exists. If so, no need to recreate unless settings change.
            // For simplicity and ensuring settings are applied, we can create it every time,
            // but Android only creates it if it doesn't exist or updates it if settings change
            // (though explicit updates might be needed for some properties).
            // A safer way for complex apps might be to create channels in Application class or a dedicated setup point.
            // For this minimal update, we'll let it run here, it's generally fine.

            // Create the channel
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                importance
            ).apply {
                description = NOTIFICATION_CHANNEL_DESCRIPTION
                enableLights(true) // Enable lights
                lightColor = context.getColor(R.color.purple_500) // Set light color
                enableVibration(true) // Enable vibration patterns

                // --- UPDATED: Set Default System Sound for the Channel ---
                // This makes the channel capable of playing the default notification sound.
                // Any notification using this channel will inherit this unless overridden on the builder.
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION) // For notifications
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION) // For sounds accompanying events
                    //.setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED) // Optional: enforce audibility
                    .build()

                // Use the default notification sound URI
                val defaultSoundUri: Uri? = Settings.System.DEFAULT_NOTIFICATION_URI

                if (defaultSoundUri != null) {
                    setSound(defaultSoundUri, audioAttributes)
                    Log.d(TAG, "Notification channel sound set to default system sound.")
                } else {
                    // Fallback or log if default sound URI is null (shouldn't happen normally)
                    setSound(null, null) // Keep it silent if default URI is not found
                    Log.w(TAG, "Default notification sound URI not found. Channel sound set to null.")
                }
                // --------------------------------------------------------

                // Optional: Set vibration pattern. DEFAULT_VIBRATE uses system default.
                // val vibrationPattern = longArrayOf(0, 250, 200, 250) // Example pattern: delay, vibrate, delay, vibrate
                // setVibrationPattern(vibrationPattern)
                // setVibrationPattern(NotificationCompat.DEFAULT_VIBRATE) // Using default is simplest
            }

            // Register the channel with the system
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel '$NOTIFICATION_CHANNEL_ID' created/updated.")
        }
    }
}