package com.gawasu.sillyn.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Data class representing a Task item in the Todolist application.
 * It is designed to be compatible with both local storage (SQLite/Room) and Firebase Firestore.
 */
@Parcelize // Annotation to automatically generate Parcelable implementation
data class Task(
    var id: String? = null,
    /**
     * ID of the task.
     * - Type: String? (Nullable String)
     * - Firestore Compatibility: Excellent. Firestore uses String as Document ID.
     * - Local (Room/SQLite) Compatibility: Good. Can be stored as String or Long. String is recommended for Firestore consistency.
     */
    var title: String = "",
    /**
     * Title of the task.
     * - Type: String
     * - Firestore Compatibility: Excellent.
     * - Local (Room/SQLite) Compatibility: Excellent.
     */
    var description: String?= null,
    /**
     * Detailed description of the task.
     * - Type: String? (Nullable String)
     * - Firestore Compatibility: Excellent.
     * - Local (Room/SQLite) Compatibility: Excellent.
     */
    var priority: Priority = Priority.NONE,
    /**
     * Priority level of the task.
     * - Type: Priority (Enum - NONE, LOW, MEDIUM, HIGH)
     * - Firestore Compatibility: Good. Enums are best stored as Strings in Firestore. Conversion needed.
     * - Local (Room/SQLite) Compatibility: Good. Enums can be stored as Strings or Ints. TypeConverters recommended.
     */
    var category: String? = null,
    /**
     * Category name of the task.
     * - Type: String? (Nullable String)
     * - Firestore Compatibility: Excellent.
     * - Local (Room/SQLite) Compatibility: Excellent.
     */
    var tags: List<String>? = null,
    /**
     * List of tags associated with the task.
     * - Type: List<String>? (Nullable List of String)
     * - Firestore Compatibility: Good. Firestore supports Array types.
     * - Local (Room/SQLite) Compatibility: Needs conversion. CSV String or Relationship recommended. CSV String is simpler for MVP.
     */
    var dueDate: Date? = null,
    /**
     * Due date and time of the task.
     * - Type: Date? (Nullable java.util.Date)
     * - Firestore Compatibility: Good. Firestore has Timestamp type. Conversion needed.
     * - Local (Room/SQLite) Compatibility: Good. Stored as Long (milliseconds timestamp) in SQLite. TypeConverters recommended.
     */
    var repeatMode: RepeatMode = RepeatMode.NONE,
    /**
     * Repeat mode of the task.
     * - Type: RepeatMode (Enum - NONE, DAILY, WEEKLY, NUMBER, DAY_IN_WEEK)
     * - Firestore Compatibility: Good. Enums are best stored as Strings in Firestore. Conversion needed.
     * - Local (Room/SQLite) Compatibility: Good. Enums can be stored as Strings or Ints. TypeConverters recommended.
     */
    var reminderType: ReminderType = ReminderType.ON_TIME,
    /**
     * Current status of the task.
     * - Type: TaskStatus (Enum - PENDING, COMPLETED, OVERDUE, ABANDONED)
     * - Firestore Compatibility: Good. Enums are best stored as Strings in Firestore. Conversion needed.
     * - Local (Room/SQLite) Compatibility: Good. Enums can be stored as Strings or Ints. TypeConverters recommended.
     */
    var status: TaskStatus = TaskStatus.PENDING,
    /**
     * Current status of the task.
     * - Type: TaskStatus (Enum - PENDING, COMPLETED, OVERDUE, ABANDONED)
     * - Firestore Compatibility: Good. Enums are best stored as Strings in Firestore. Conversion needed.
     * - Local (Room/SQLite) Compatibility: Good. Enums can be stored as Strings or Ints. TypeConverters recommended.
     */
    var type: TaskType = TaskType.TASK
    /**
     * Type of the task (Task or Note).
     * - Type: TaskType (Enum - TASK, NOTE)
     * - Firestore Compatibility: Good. Enums are best stored as Strings in Firestore. Conversion needed.
     * - Local (Room/SQLite) Compatibility: Good. Enums can be stored as Strings or Ints. TypeConverters recommended.
     */
) : Parcelable { // Implement interface Parcelable (annotation @Parcelize will handle implementation)

    enum class Priority{
        NONE,
        LOW,
        MEDIUM,
        HIGH
    }

    enum class RepeatMode{
        NONE,
        DAILY,
        WEEKLY,
        MONTHLY, // Removed for MVP (if needed)
        YEARLY, // Removed for MVP (if needed)
        NUMBER,
        DAY_IN_WEEK
    }

    enum class ReminderType{
        ON_TIME,
        EARLY
    }

    enum class TaskStatus{
        PENDING,
        COMPLETED, // Corrected enum name to COMPLETED (not COMPLETE)
        OVERDUE,
        ABANDONED
    }

    enum class TaskType{
        TASK,
        NOTE
    }
}