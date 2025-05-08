package com.gawasu.sillyn.util

import android.content.Context
import com.gawasu.sillyn.domain.model.Task
import java.util.Calendar
import java.util.Date

object ReminderTimeCalculator {

    /**
     * Calculates the actual reminder time based on the task's due date and reminder type.
     * @param task The Task object.
     * @return The calculated reminder time (Date), or null if dueDate is null or reminderType is unrecognized.
     */
    fun calculateReminderTime(task: Task): Date? {
        val dueDate = task.dueDate ?: return null // Cannot schedule without a due date

        val calendar = Calendar.getInstance().apply { time = dueDate }

        when (task.reminderType) {
            Task.ReminderType.ON_TIME.name -> {
                // No change, reminder is exactly at the due date/time
            }
            "EARLY_30M" -> {
                calendar.add(Calendar.MINUTE, -30)
            }
            "EARLY_1H" -> {
                calendar.add(Calendar.HOUR_OF_DAY, -1)
            }
            "EARLY_3H" -> {
                calendar.add(Calendar.HOUR_OF_DAY, -3)
            }
            "EARLY_1D" -> {
                calendar.add(Calendar.DAY_OF_MONTH, -1)
            }
            // Add other cases if you expand reminder types in the future
            else -> {
                // Default to ON_TIME or simply return null if unrecognized
                // For now, we'll default to ON_TIME if the string is not one of the expected ones
                // Or log a warning if needed
            }
        }

        // Ensure the calculated time is not in the past relative to now (with a small buffer)
        // This prevents scheduling alarms for times that have already passed.
        val now = Calendar.getInstance().time
        val calculatedTime = calendar.time

        return if (calculatedTime.after(now) || calculatedTime.equals(now) || calculatedTime.after(Date(System.currentTimeMillis() - 5000))) { // Add small buffer
            calculatedTime
        } else {
            // If calculated time is in the past, we might choose not to schedule it
            // Or schedule it for 'now' depending on desired behavior.
            // For simplicity, let's not schedule past reminders.
            null
        }
    }

    /**
     * Maps standard reminder type string to a user-friendly label (optional, for notification text).
     * Could be in a different utility or string resource map.
     */
    fun getReminderLabel(reminderType: String, context: Context): String {
        return when (reminderType) {
            Task.ReminderType.ON_TIME.name -> context.getString(com.gawasu.sillyn.R.string.reminder_ontime)
            "EARLY_30M" -> context.getString(com.gawasu.sillyn.R.string.reminder_early_30m)
            "EARLY_1H" -> context.getString(com.gawasu.sillyn.R.string.reminder_early_1h)
            "EARLY_3H" -> context.getString(com.gawasu.sillyn.R.string.reminder_early_3h)
            "EARLY_1D" -> context.getString(com.gawasu.sillyn.R.string.reminder_early_1d)
            else -> context.getString(com.gawasu.sillyn.R.string.reminder_ontime) // Default label
        }
    }

    /**
     * Calculates the time difference in a user-friendly format (e.g., "30 phút nữa", "1 ngày nữa").
     * Useful for notification text for EARLY reminders.
     */
    fun getTimeDifferenceLabel(reminderType: String, context: Context): String {
        return when (reminderType) {
            Task.ReminderType.ON_TIME.name -> "" // No difference label for ON_TIME
            "EARLY_30M" -> context.getString(com.gawasu.sillyn.R.string.time_diff_30m) // Cần thêm string này
            "EARLY_1H" -> context.getString(com.gawasu.sillyn.R.string.time_diff_1h) // Cần thêm string này
            "EARLY_3H" -> context.getString(com.gawasu.sillyn.R.string.time_diff_3h) // Cần thêm string này
            "EARLY_1D" -> context.getString(com.gawasu.sillyn.R.string.time_diff_1d) // Cần thêm string này
            else -> ""
        }
    }
}