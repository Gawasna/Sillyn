package com.gawasu.sillyn.utils

import com.gawasu.sillyn.domain.model.Task
import com.google.firebase.Timestamp
import java.util.Date

object TypeConverter {
    // Convert Date to Firestore Timestamp
    fun dateToTimestamp(date: Date?): Timestamp? {
        return date?.let { Timestamp(it) }
    }

    // Convert Firestore Timestamp to Date
    fun timestampToDate(timestamp: Timestamp?): Date? {
        return timestamp?.toDate()
    }

    // Convert Enum Priority to String
    fun priorityToString(priority: Task.Priority): String {
        return priority.name
    }

    // Convert String to Enum Priority
    fun stringToPriority(priorityString: String): Task.Priority {
        return Task.Priority.valueOf(priorityString)
    }

    // Convert Enum RepeatMode to String
    fun repeatModeToString(repeatMode: Task.RepeatMode): String {
        return repeatMode.name
    }

    // Convert String to Enum RepeatMode
    fun stringToRepeatMode(repeatModeString: String): Task.RepeatMode {
        return Task.RepeatMode.valueOf(repeatModeString)
    }

    // Convert Enum ReminderType to String
    fun reminderTypeToString(reminderType: Task.ReminderType): String {
        return reminderType.name
    }

    // Convert String to Enum ReminderType
    fun stringToReminderType(reminderTypeString: String): Task.ReminderType {
        return Task.ReminderType.valueOf(reminderTypeString)
    }

    // Convert Enum TaskStatus to String
    fun taskStatusToString(taskStatus: Task.TaskStatus): String {
        return taskStatus.name
    }

    // Convert String to Enum TaskStatus
    fun stringToTaskStatus(taskStatusString: String): Task.TaskStatus {
        return Task.TaskStatus.valueOf(taskStatusString)
    }

    // Convert Enum TaskType to String
    fun taskTypeToString(taskType: Task.TaskType): String {
        return taskType.name
    }

    // Convert String to Enum TaskType
    fun stringToTaskType(taskTypeString: String): Task.TaskType {
        return Task.TaskType.valueOf(taskTypeString)
    }

}