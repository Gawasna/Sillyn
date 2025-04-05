package com.gawasu.sillyn.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class Task(
    var id: String? = null,
    var title: String = "",
    var description: String? = null,
    var priority: String = Priority.NONE.name,
    var category: String? = null,
    var tags: List<String>? = null,
    var dueDate: Date? = null,
    var repeatMode: String = RepeatMode.NONE.name,
    var reminderType: String = ReminderType.ON_TIME.name,
    var status: String = TaskStatus.PENDING.name,
    var type: String = TaskType.TASK.name
) : Parcelable {

    enum class Priority {
        NONE, LOW, MEDIUM, HIGH
    }

    enum class RepeatMode {
        NONE, DAILY, WEEKLY, MONTHLY, YEARLY, NUMBER, DAY_IN_WEEK
    }

    enum class ReminderType {
        ON_TIME, EARLY
    }

    enum class TaskStatus {
        PENDING, COMPLETED, OVERDUE, ABANDONED
    }

    enum class TaskType {
        TASK, NOTE
    }
}