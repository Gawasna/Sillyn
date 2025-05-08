package com.gawasu.sillyn.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.receiver.ReminderReceiver
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

// Sử dụng Hilt để cung cấp instance
@Singleton
class ReminderScheduler @Inject constructor(
    private val context: Context // @ApplicationContext injected by Hilt
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val TAG = "ReminderScheduler"

    companion object {
        const val ACTION_SHOW_REMINDER = "com.gawasu.sillyn.ACTION_SHOW_REMINDER"
        const val EXTRA_TASK_ID = "extra_task_id"
        // Có thể thêm các extra khác nếu cần truyền dữ liệu tối thiểu qua Intent
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_DEADLINE = "extra_task_deadline"
    }

    // Tính toán thời điểm nhắc nhở thực tế dựa trên dueDate và reminderType
    private fun calculateReminderTime(task: Task): Long? {
        val dueDate = task.dueDate?.time ?: return null // Need a due date

        // Nếu task đã hoàn thành hoặc quá hạn, không cần lên lịch
        if (task.status == Task.TaskStatus.COMPLETED.name || task.status == Task.TaskStatus.OVERDUE.name) {
            return null
        }

        // Lấy thời điểm hiện tại
        val currentTime = System.currentTimeMillis()

        // Nếu dueDate đã ở trong quá khứ, không lên lịch reminder on-time/early cho nó
        if (dueDate < currentTime) {
            // TODO: Xử lý task quá hạn? Có thể trigger notification ngay lập tức hoặc đánh dấu quá hạn.
            // Đối với task lặp, cần tính toán lần lặp tiếp theo nếu dueDate đã quá khứ
            Log.w(TAG, "Task ${task.id} dueDate is in the past: ${task.dueDate}. Cannot schedule standard reminder.")
            return null
        }

        // Tính toán thời điểm nhắc nhở dựa trên reminderType (ON_TIME, EARLY)
        val reminderTime = when (task.reminderType) {
            Task.ReminderType.ON_TIME.name -> dueDate // Nhắc đúng giờ
            Task.ReminderType.EARLY.name -> {
                // TODO: Cần định nghĩa "EARLY" là bao lâu trước dueDate.
                // Ví dụ: 15 phút trước. Bạn cần thêm field cho khoảng thời gian nhắc sớm.
                val earlyMinutes = 15 // Ví dụ: Nhắc sớm 15 phút
                dueDate - (earlyMinutes * 60 * 1000)
            }
            else -> dueDate // Default to ON_TIME if type is unknown
        }

        // Đảm bảo thời điểm nhắc nhở không ở trong quá khứ
        if (reminderTime < currentTime) {
            Log.w(TAG, "Calculated reminder time for task ${task.id} is in the past: ${reminderTime}. Not scheduling.")
            return null // Không lên lịch nếu thời điểm tính toán lại ở quá khứ
        }

        return reminderTime
    }


    fun scheduleReminder(task: Task) {
        val reminderTime = calculateReminderTime(task)

        if (reminderTime == null) {
            Log.d(TAG, "Task ${task.id} (${task.title}) does not need scheduling or time is invalid. Canceling any existing alarm.")
            cancelReminder(task.id)
            return
        }

        Log.d(TAG, "Attempting to schedule reminder for task ${task.id} (${task.title}) at ${java.util.Date(reminderTime)}")

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SHOW_REMINDER
            // Truyền dữ liệu tối thiểu qua Intent extras. Worker sẽ fetch full data.
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_TASK_TITLE, task.title)
            putExtra(EXTRA_TASK_DEADLINE, task.dueDate?.time) // Optional: Pass original deadline
            // Uri data helps make PendingIntent unique across extras changes, but hashCode is also used
            // data = Uri.withAppendedPath(Uri.parse("content://com.gawasu.sillyn/tasks"), task.id)
        }

        // Tạo PendingIntent. Sử dụng task ID hashcode làm requestCode.
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(), // Unique request code per task
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // Lên lịch alarm
        // Target SDK 34 yêu cầu quyền SCHEDULE_EXACT_ALARM hoặc USE_EXACT_ALARM cho setExact/setExactAndAllowWhileIdle
        // setAlarmClock là lựa chọn tốt cho user-facing alarms
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms() -> {
                // Sử dụng setAlarmClock nếu có quyền exact alarms (API 31+)
                val alarmClockInfo = AlarmManager.AlarmClockInfo(reminderTime, pendingIntent) // pendingIntent thứ 2 là optional FullScreenIntent
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent) // pendingIntent thứ 2 là khi alarm trigger
                Log.d(TAG, "Scheduled AlarmClock for task ${task.id} at ${java.util.Date(reminderTime)}")
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                // Fallback cho API 23+ khi không có quyền exact (hoặc API < 31)
                // setAndAllowWhileIdle hoạt động tốt hơn setExact trong chế độ Doze
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime, pendingIntent)
                Log.d(TAG, "Scheduled setAndAllowWhileIdle alarm for task ${task.id} at ${java.util.Date(reminderTime)}")
            }
            else -> {
                // Fallback cho các API cũ hơn M
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminderTime, pendingIntent)
                Log.d(TAG, "Scheduled exact alarm for task ${task.id} at ${java.util.Date(reminderTime)} (old API)")
            }
        }
    }

    fun cancelReminder(taskId: String?) {
        if (taskId.isNullOrBlank()) {
            Log.w(TAG, "Cannot cancel reminder, task ID is null or blank.")
            return
        }
        Log.d(TAG, "Attempting to cancel reminder for task $taskId")
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SHOW_REMINDER
            // Important: Intent extras should match if using them for uniqueness,
            // but matching by requestCode is usually sufficient here.
            // data = Uri.withAppendedPath(Uri.parse("content://com.gawasu.sillyn/tasks"), taskId) // Match data URI if used in schedule
        }

        // Phải tạo lại PendingIntent giống hệt cái đã dùng để set alarm (requestCode và Intent)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            // Hủy cả thông báo nếu nó đang hiển thị
            NotificationHelper(context).cancelNotification(taskId)
            Log.d(TAG, "Canceled alarm for task $taskId")
        } else {
            Log.d(TAG, "No pending alarm found for task $taskId to cancel.")
        }
    }

    // Hàm này sẽ được gọi từ RescheduleRemindersWorker
    // Nó cần quyền truy cập Repository để lấy danh sách task
    // Chúng ta sẽ truyền TaskRepositoryInterface vào Worker
    // và Worker sẽ gọi scheduleReminder cho từng task.
    // Nên không cần hàm này trực tiếp ở đây nữa.
}