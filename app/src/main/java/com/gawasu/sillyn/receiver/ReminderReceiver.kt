package com.gawasu.sillyn.receiver

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
                            // Assuming getTaskById returns a Flow of FirebaseResult<Task?>
                            taskRepository.getTaskById(userId, taskId).collect { result ->
                                when (result) {
                                    is FirebaseResult.Loading -> {
                                        // Handle loading if necessary (e.g., show a placeholder notification)
                                        Log.d(TAG, "Loading task $taskId for reminder...")
                                    }
                                    is FirebaseResult.Success -> {
                                        val task = result.data
                                        if (task != null && task.status == Task.TaskStatus.PENDING.name) {
                                            Log.d(TAG, "Task $taskId loaded successfully. Showing notification.")
                                            showNotification(context, task, userId) // Pass userId for actions
                                        } else {
                                            Log.d(TAG, "Task $taskId not found, completed, or abandoned. Not showing reminder.")
                                            // Cancel the alarm if the task is no longer pending
                                            // Note: This requires injecting TaskReminderScheduler here or having a mechanism
                                            // to cancel it. For simplicity now, we just won't show the notification.
                                            // Proper handling might involve a UseCase or Manager called from here.
                                        }
                                        // Finish the async operation once data is processed
                                        pendingResult.finish()
                                    }
                                    is FirebaseResult.Error -> {
                                        Log.e(TAG, "Error fetching task $taskId for reminder: ${result.exception.message}", result.exception)
                                        // Finish the async operation on error
                                        pendingResult.finish()
                                    }
                                }
                                // Assuming collect will eventually finish or handle lifecycle correctly.
                                // For a single fetch, maybe just a suspend function in repo is better than Flow here if not observing changes.
                                // If getTaskById flow emits multiple times, ensure you only show notification once per trigger.
                                // A simple suspend taskRepository.getTaskById(userId, taskId).first() or single() might be better if you only need the current state.
                                // Let's assume getTaskById is suspend fun or we collect only once.
                                if (result !is FirebaseResult.Loading) {
                                    // Stop collecting after first non-Loading result
                                    return@collect
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
        createNotificationChannel(context)
        // Generate a unique notification ID based on task ID
        val notificationId = task.id.hashCode()

        // --- Create PendingIntents for Notification Actions ---
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

        // Example: "Nhiệm vụ: Làm bài tập Android sau 30 phút nữa"
        //           "Đến hạn: 08/05/2025 12:40 PM"
        //           "Độ ưu tiên: Trung bình"

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
            // Có thể thêm nhãn "Mô tả:" hoặc hiển thị trực tiếp
            task.description // Chỉ hiển thị mô tả nếu có
        } else {
            "" // Chuỗi rỗng nếu không có mô tả
        }

        val notificationTextParts = listOf(
            dueDateText,
            priorityLabel,
            descriptionText.orEmpty()
        ).filter { it.isNotBlank() }
        val notificationText = notificationTextParts.joinToString("\n")

        // Optional: Intent to open the app/task details when clicking the notification body
        // Need to decide where to navigate (e.g., MainActivity, straight to TaskFragment list)
        // This requires setting up deep links or navigating via Activity Intents.
        // For simplicity now, let's just open the main activity.
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
            // .setSound(null) // Default sound, or set a specific sound - We will set this later
            // .setDefaults(NotificationCompat.DEFAULT_ALL) // Default lights, vibration, sound (can customize)
            .setCategory(NotificationCompat.CATEGORY_REMINDER) // Hint to the system about the notification type


        // --- Set Default Sound (Requirement 3) ---
        // You can use setSound or setDefaults(NotificationCompat.DEFAULT_SOUND)
        // Using setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE | NotificationCompat.DEFAULT_LIGHTS)
        // is common to get default behavior unless you customize them.
        builder.setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
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
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH // High importance for reminders
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                importance
            ).apply {
                description = NOTIFICATION_CHANNEL_DESCRIPTION
                enableLights(true)
                lightColor = context.getColor(R.color.purple_500) // Replace with your color
                enableVibration(true)
                setSound(null, null) // Use default sound or specify one
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel '$NOTIFICATION_CHANNEL_ID' created.")
        }
    }
}