package com.gawasu.sillyn.data.firebase

import android.util.Log
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.utils.FirebaseResult
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import java.util.Date

class FirestoreTaskService {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val TASKS_SUB_COLLECTION = "tasks"
        private const val TAG = "Task Service"
    }

    suspend fun createSampleTaskForUser(userId: String) {
        try {
            val taskData = hashMapOf(
                "id" to firestore.collection("user").document(userId)
                    .collection("tasks").document().id,
                "title" to "Sample Task",
                "description" to "",
                "status" to "pending",
                "type" to "task",
                "repeatMode" to "none",
                "reminderType" to "onTime",
                "priority" to "3",
                "tags" to listOf<String>(),
                "category" to "general",
                "createAt" to Timestamp.now(),
                "updateAt" to Timestamp.now()
                // Bạn có thể thêm "dueDate" sau nếu muốn
            )

            val taskRef = firestore.collection("user")
                .document(userId)
                .collection("Tasks")
                .document(taskData["id"] as String)

            taskRef.set(taskData).await()
            Log.i(TAG, "SAMPLE TASK: Created successfully for user $userId")
        } catch (e: Exception) {
            Log.e(TAG, "SAMPLE TASK: Error creating sample task - ${e.message}", e)
        }
    }

    // Function to get the Tasks sub-collection reference for a given userId
    private fun getTasksCollection(userId: String) =
        firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(TASKS_SUB_COLLECTION)

    // Function to map Task data class to Firestore document data (for writing)
    private fun taskToDocument(task: Task): HashMap<String, Any?> {
        return hashMapOf(
            "id" to task.id,
            "title" to task.title,
            "description" to task.description,
            "priority" to task.priority.toString(),
            "category" to task.category,
            "tags" to task.tags,
            "dueDate" to task.dueDate?.let { Date(it.time) },
            "repeatMode" to task.repeatMode.toString(),
            "reminderType" to task.reminderType.toString(),
            "status" to task.status.toString(),
            "type" to task.type.toString(),
            "createAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updateAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
    }

    // Function to map Firestore document data to Task data class (for reading)
    /**private fun documentToTask(document:  Map<String, Any?>): Task? {
        return try {
            Task(
                id = document["id"] as? String,
                title = document["title"] as? String ?: "",
                description = document["description"] as? String,
                priority = Task.Priority.valueOf(document["priority"] as? String ?: Task.Priority.NONE.toString()), // String to Enum
                category = document["category"] as? String,
                tags = document["tags"] as? List<String>,
                dueDate = (document["dueDate"] as? com.google.firebase.Timestamp)?.toDate(), // Timestamp to Date
                repeatMode = Task.RepeatMode.valueOf(document["repeatMode"] as? String ?: Task.RepeatMode.NONE.toString()), // String to Enum
                reminderType = Task.ReminderType.valueOf(document["reminderType"] as? String ?: Task.ReminderType.ON_TIME.toString()), // String to Enum
                status = Task.TaskStatus.valueOf(document["status"] as? String ?: Task.TaskStatus.PENDING.toString()), // String to Enum
                type = Task.TaskType.valueOf(document["type"] as? String ?: Task.TaskType.TASK.toString()) // String to Enum
            )
        } catch (e: Exception) {
            null
        }
    }**/


    suspend fun addTask(userId: String, task: Task): FirebaseResult<Task> {
        return try {
            val taskDocument = getTasksCollection(userId).document()
            val taskId = taskDocument.id
            task.id = taskId // Set the ID in the Task object

            taskDocument.set(taskToDocument(task)).await()
            FirebaseResult.Success(task)
        } catch (e: Exception) {
            FirebaseResult.Error(e)
        }
    }

    suspend fun updateTask(userId: String, task: Task): FirebaseResult<Task> {
        return try {
            task.id?.let { taskId ->
                getTasksCollection(userId).document(taskId).update(taskToDocument(task)).await()
                FirebaseResult.Success(task)
            } ?: run {
                FirebaseResult.Error(IllegalArgumentException("Task ID cannot be null for update"))
            }
        } catch (e: Exception) {
            FirebaseResult.Error(e)
        }
    }


    /**suspend fun getTask(userId: String, taskId: String): FirebaseResult<Task?> {
        return try {
            val documentSnapshot = getTasksCollection(userId).document(taskId).get().await()
            if (documentSnapshot.exists()) {
                val taskData = documentSnapshot.data
                taskData?.let {
                    val task = documentToTask(it)
                    FirebaseResult.Success(task)
                } ?: FirebaseResult.Success(null) // Document exists but data is null
            } else {
                FirebaseResult.Success(null) // Document does not exist
            }
        } catch (e: Exception) {
            FirebaseResult.Error(e)
        }
    }**/

    /**suspend fun getAllTasks(userId: String): FirebaseResult<List<Task>> {
        return try {
            val querySnapshot = getTasksCollection(userId).get().await()
            val tasks = querySnapshot.documents.mapNotNull { document ->
                val taskData = document.data
                taskData?.let { documentToTask(it) }
            }
            FirebaseResult.Success(tasks)
        } catch (e: Exception) {
            FirebaseResult.Error(e)
        }
    }**/

    suspend fun deleteTask(userId: String, taskId: String): FirebaseResult<Boolean> {
        return try {
            getTasksCollection(userId).document(taskId).delete().await()
            FirebaseResult.Success(true)
        } catch (e: Exception) {
            FirebaseResult.Error(e)
        }
    }

    fun enableLocalPersistence() {
        val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        firestore.firestoreSettings = settings
    }
}