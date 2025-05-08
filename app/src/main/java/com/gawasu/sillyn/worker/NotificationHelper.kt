package com.gawasu.sillyn.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gawasu.sillyn.R // Giả định bạn có file R
import com.gawasu.sillyn.receiver.NotificationActionReceiver // Import receiver cho actions
import com.gawasu.sillyn.ui.activity.MainActivity // Điểm đến khi click thông báo
import javax.inject.Inject
import javax.inject.Singleton

// Sử dụng Hilt để cung cấp instance
@Singleton
class NotificationHelper @Inject constructor(
    private val context: Context // @ApplicationContext injected by Hilt
) {

    private val CHANNEL_ID = "task_reminder_channel"
    private val CHANNEL_NAME = "Nhắc nhở nhiệm vụ"
    private val CHANNEL_DESCRIPTION = "Thông báo khi nhiệm vụ đến hạn"
    private val TAG = "NotificationHelper"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showReminderNotification(notificationId: String, title: String, content: String) {
        // Sử dụng ID task làm ID thông báo để có thể cập nhật/hủy sau này.
        // Dùng hashCode() để biến String thành Int cho notify().
        val uniqueNotificationIdInt = notificationId.hashCode()

        // --- PendingIntent khi click vào thông báo ---
        // Mở MainActivity và truyền task ID để có thể điều hướng đến chi tiết task
        val viewTaskIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(ReminderScheduler.EXTRA_TASK_ID, notificationId) // Truyền task ID
            // Có thể thêm action custom nếu cần
            // action = "com.gawasu.sillyn.ACTION_VIEW_TASK"
        }

        val viewTaskPendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            ("view_" + notificationId).hashCode(), // Request code unique cho hành động view
            viewTaskIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // --- PendingIntent cho hành động "Hoàn thành" ---
        val completeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_COMPLETE_TASK
            putExtra(ReminderScheduler.EXTRA_TASK_ID, notificationId)
        }
        val completePendingIntent: PendingIntent = PendingIntent.getBroadcast(
            context,
            ("complete_" + notificationId).hashCode(), // Request code unique cho hành động complete
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // --- PendingIntent cho hành động "Đã xem" (Snooze/Dismiss/Mark Read) ---
        // Giả định "Đã xem" chỉ là đóng thông báo và không hành động gì thêm với task
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS_NOTIFICATION
            putExtra(ReminderScheduler.EXTRA_TASK_ID, notificationId)
        }
        val dismissPendingIntent: PendingIntent = PendingIntent.getBroadcast(
            context,
            ("dismiss_" + notificationId).hashCode(), // Request code unique cho hành động dismiss
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )


        // Xây dựng thông báo
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.bell_svgrepo_com) // Thay bằng icon thông báo của bạn (cần là icon trắng, trong suốt)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Độ ưu tiên
            .setContentIntent(viewTaskPendingIntent) // Hành động khi click vào nội dung thông báo
            .setAutoCancel(true) // Tự động đóng khi click ContentIntent
            .setOnlyAlertOnce(true) // Chỉ đổ chuông/rung lần đầu hiển thị notification với ID này
        // .setSound(...) // Cấu hình âm thanh nếu cần
        // .setVibrate(...) // Cấu hình rung nếu cần
        // .setDefaults(NotificationCompat.DEFAULT_ALL) // Sử dụng cài đặt mặc định cho âm thanh, rung, đèn

        // Thêm các hành động (buttons)
        builder.addAction(0, "Hoàn thành", completePendingIntent) // Thay 0 bằng icon nếu có
        builder.addAction(0, "Đã xem", dismissPendingIntent) // Thay 0 bằng icon nếu có
        // builder.addAction(...) // Thêm hành động khác nếu cần (ví dụ: Snooze)

        // Hiển thị thông báo
        with(NotificationManagerCompat.from(context)) {
            // Cần kiểm tra quyền POST_NOTIFICATIONS trên API 33+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    notify(uniqueNotificationIdInt, builder.build())
                    Log.d(TAG, "Notification shown for task $notificationId (API ${Build.VERSION.SDK_INT})")
                } else {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Cannot show notification for task $notificationId.")
                    // Bạn có thể hiển thị một Toast hoặc log để báo cho người dùng biết cần cấp quyền
                }
            } else {
                // Trên các phiên bản cũ hơn API 33, không cần quyền runtime POST_NOTIFICATIONS
                notify(uniqueNotificationIdInt, builder.build())
                Log.d(TAG, "Notification shown for task $notificationId (API ${Build.VERSION.SDK_INT})")
            }
        }
    }

    // Hàm hủy thông báo
    fun cancelNotification(notificationId: String?) {
        if (notificationId.isNullOrBlank()) return
        val uniqueNotificationIdInt = notificationId.hashCode()
        with(NotificationManagerCompat.from(context)) {
            cancel(uniqueNotificationIdInt)
            Log.d(TAG, "Canceled notification for task $notificationId")
        }
    }
}