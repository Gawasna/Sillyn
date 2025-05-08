package com.gawasu.sillyn.data.remote.firestore

import android.util.Log
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.domain.model.User
import com.gawasu.sillyn.utils.FirebaseResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import javax.inject.Inject

class FirestoreServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : FirestoreService {

    companion object {
        const val TAG = "FIRESTORE_SERVICES"
        const val GET_TASKS = "GET_TASKS"
        const val GET_TASK_CATEGORIES = "GET_TASK_CATEGORIES"
        const val ADD_TASK = "ADD_TASK"
        const val UPDATE_TASK = "UPDATE_TASK"
        const val DELETE_TASK = "DELETE_TASK"
        const val GET_USER = "GET_USER"
        const val GET_TODAY_TASKS = "GET_TODAY_TASKS"
        const val GET_WEEK_TASKS = "GET_WEEK_TASKS"
        const val GET_TASKS_BY_CATEGORY = "GET_TASKS_BY_CATEGORY"
        const val GET_UPCOMING_TASKS = "GET_UPCOMING_TASKS"
        const val GET_TASK_BY_ID = "GET_TASK_BY_ID"
    }

    override fun getTasks(userId: String): Flow<FirebaseResult<List<Task>>> = callbackFlow {
        Log.d(TAG, GET_TASKS + " is getting called")
        val collectionRef = firestore.collection("user").document(userId).collection("tasks")
        val snapshotListener = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(FirebaseResult.Error(error))
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val tasks = snapshot.documents.mapNotNull { document ->
                    document.toObject(Task::class.java)?.apply {
                        id = document.id // Set document ID as task ID
                    }
                }
                trySend(FirebaseResult.Success(tasks))
            } else {
                trySend(FirebaseResult.Error(Exception("Tasks data not found")))
            }
        }
        awaitClose {
            snapshotListener.remove()
        }
    }

    override fun getTaskCategories(userId: String): Flow<FirebaseResult<List<String>>> = callbackFlow {
        Log.d(TAG, GET_TASK_CATEGORIES + " is getting called")
        val collectionRef = firestore.collection("user").document(userId).collection("tasks")
        val snapshotListener = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(FirebaseResult.Error(error))
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val categories = mutableSetOf<String>() // Use Set to store unique categories
                for (document in snapshot.documents) {
                    val category = document.getString("category")
                    if (!category.isNullOrBlank()) { // Chỉ lấy category không null và không rỗng
                        categories.add(category)
                    }
                }
                trySend(FirebaseResult.Success(categories.toList())) // Convert Set to List for emission
            } else {
                trySend(FirebaseResult.Error(Exception("No tasks data found to extract categories")))
            }
        }

        awaitClose {
            snapshotListener.remove()
        }
    }

    override fun addTask(userId: String, task: Task): Flow<FirebaseResult<Task>> = callbackFlow {
        Log.d(TAG, ADD_TASK + " is getting called")
        try {
            val taskDocument = firestore.collection("user").document(userId).collection("tasks").document()
            task.id = taskDocument.id
            taskDocument.set(task).await()
            trySend(FirebaseResult.Success(task))
        } catch (e: Exception) {
            trySend(FirebaseResult.Error(e))
        }
        close() // Đóng channel sau khi hoàn thành
    }

    override fun updateTask(userId: String, task: Task): Flow<FirebaseResult<Void>> = callbackFlow {
        Log.d(TAG, UPDATE_TASK + " is getting called")
        try {
            firestore.collection("user").document(userId).collection("tasks").document(task.id ?: "").set(task).await()
            FirebaseResult.Success(null)
        } catch (e: Exception) {
            trySend(FirebaseResult.Error(e))
        }
        close()
    }

    override fun deleteTask(userId: String, taskId: String): Flow<FirebaseResult<Void>> = callbackFlow {
        Log.d(TAG, DELETE_TASK + " is getting called")
        try {
            firestore.collection("user").document(userId).collection("tasks").document(taskId).delete().await()
            FirebaseResult.Success(null)
        } catch (e: Exception) {
            trySend(FirebaseResult.Error(e))
        }
        close()
    }

    override fun getUser(userId: String): Flow<FirebaseResult<User>>  = callbackFlow {
        Log.d(TAG, GET_USER + " is getting called")
        val documentRef = firestore.collection("user").document(userId)
        val snapshotListener = documentRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(FirebaseResult.Error(error))
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val user = snapshot.toObject(User::class.java)?.copy(userId = snapshot.id) // Use copy to handle immutability
                FirebaseResult.Success(user)
            } else {
                trySend(FirebaseResult.Error(Exception("User data not found")))
            }
        }
        awaitClose {
            snapshotListener.remove()
        }
    }

    override fun getTodayTasks(userId: String): Flow<FirebaseResult<List<Task>>> = callbackFlow {
        Log.d(TAG, GET_TODAY_TASKS + " is getting called")
        val collectionRef = firestore.collection("user").document(userId).collection("tasks")
        // Calculate start and end of today
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.time // Timestamp at the beginning of today

        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfToday = calendar.time // Timestamp at the beginning of tomorrow (exclusive end)

        // Firestore query for tasks where dueDate is >= startOfToday and < endOfToday
        val query = collectionRef
            .whereGreaterThanOrEqualTo("dueDate", startOfToday)
            .whereLessThan("dueDate", endOfToday)
            .orderBy("dueDate", com.google.firebase.firestore.Query.Direction.ASCENDING) // Order by date

        val snapshotListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(FirebaseResult.Error(error))
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val tasks = snapshot.documents.mapNotNull { document ->
                    document.toObject(Task::class.java)?.apply {
                        id = document.id
                    }
                }
                trySend(FirebaseResult.Success(tasks))
            } else {
                trySend(FirebaseResult.Error(Exception("Today's tasks data not found")))
            }
        }
        awaitClose { snapshotListener.remove() }
    }

    override fun getWeekTasks(userId: String): Flow<FirebaseResult<List<Task>>> = callbackFlow {
        Log.d(TAG, GET_WEEK_TASKS + " is getting called")
        val collectionRef = firestore.collection("user").document(userId).collection("tasks")
        // Calculate start of today and end of the next 7 days (inclusive)
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.time // Timestamp at the beginning of today

        calendar.add(Calendar.DAY_OF_YEAR, 7) // Add 7 days
        val endOfNext7Days = calendar.time // Timestamp at the beginning of day 8 (exclusive end)

        // Firestore query for tasks where dueDate is >= startOfToday and < endOfNext7Days
        val query = collectionRef
            .whereGreaterThanOrEqualTo("dueDate", startOfToday)
            .whereLessThan("dueDate", endOfNext7Days)
            .orderBy("dueDate", com.google.firebase.firestore.Query.Direction.ASCENDING) // Order by date

        val snapshotListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(FirebaseResult.Error(error))
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val tasks = snapshot.documents.mapNotNull { document ->
                    document.toObject(Task::class.java)?.apply {
                        id = document.id
                    }
                }
                trySend(FirebaseResult.Success(tasks))
            } else {
                trySend(FirebaseResult.Error(Exception("Week's tasks data not found")))
            }
        }
        awaitClose { snapshotListener.remove() }
    }

    override fun getTasksByCategory(userId: String, category: String): Flow<FirebaseResult<List<Task>>> = callbackFlow {
        Log.d(TAG, GET_TASKS_BY_CATEGORY + " is getting called --> " + category)
        val collectionRef = firestore.collection("user").document(userId).collection("tasks")

        // Firestore query for tasks where category field matches the provided category
        val query = collectionRef
            .whereEqualTo("category", category)
            .orderBy("dueDate", com.google.firebase.firestore.Query.Direction.ASCENDING) // Optional: order by due date

        val snapshotListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(FirebaseResult.Error(error))
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val tasks = snapshot.documents.mapNotNull { document ->
                    document.toObject(Task::class.java)?.apply {
                        id = document.id
                    }
                }
                trySend(FirebaseResult.Success(tasks))
            } else {
                // Note: An empty snapshot is not an error for category queries
                // It just means there are no tasks in that category.
                // Only send Error if the overall snapshot is null unexpectedly.
                if (snapshot == null ) {
                    trySend(FirebaseResult.Success(emptyList())) // Send empty list for no tasks
                } else {
                    trySend(FirebaseResult.Error(Exception("Tasks data for category '$category' not found")))
                }
            }
        }
        awaitClose { snapshotListener.remove() }
    }

    override fun getUpcomingTasksWithDeadlines(userId: String): Flow<FirebaseResult<List<Task>>> = callbackFlow {
        Log.d(TAG, GET_UPCOMING_TASKS + " is getting called")
        val collectionRef = firestore.collection("user").document(userId).collection("tasks")

        val now = Calendar.getInstance().time

        val query = collectionRef
            .whereGreaterThan("dueDate", now)
            .orderBy("dueDate", com.google.firebase.firestore.Query.Direction.ASCENDING)

        val snapshotListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(FirebaseResult.Error(error))
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val tasks = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Task::class.java)?.apply { id = doc.id }
                }
                trySend(FirebaseResult.Success(tasks))
            } else {
                trySend(FirebaseResult.Error(Exception("No upcoming tasks found")))
            }
        }

        awaitClose { snapshotListener.remove() }
    }

    override fun getTasksById(userId: String, taskId: String): Flow<FirebaseResult<Task?>> = callbackFlow {
        Log.d(TAG, GET_TASK_BY_ID + " is getting called")
        val documentRef = firestore.collection("user").document(userId).collection("tasks").document(taskId)

        val snapshotListener = documentRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(FirebaseResult.Error(error))
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val task = snapshot.toObject(Task::class.java)?.apply {
                    id = snapshot.id
                }
                trySend(FirebaseResult.Success(task))
            } else {
                trySend(FirebaseResult.Error(Exception("Task with ID $taskId not found")))
            }
        }

        awaitClose { snapshotListener.remove() }
    }

}