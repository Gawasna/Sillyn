package com.gawasu.sillyn.data.remote.firestore

import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.domain.model.User
import com.gawasu.sillyn.utils.FirebaseResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirestoreServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : FirestoreService {

    override fun getTasks(userId: String): Flow<FirebaseResult<List<Task>>> = callbackFlow {
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


    override fun addTask(userId: String, task: Task): Flow<FirebaseResult<Void>> = callbackFlow {
        try {
            val taskDocument = firestore.collection("user").document(userId).collection("tasks").document()
            task.id = taskDocument.id // Assign document ID to task ID
            taskDocument.set(task).await()
            FirebaseResult.Success(null)
        } catch (e: Exception) {
            trySend(FirebaseResult.Error(e))
        }
        close()
    }


    override fun updateTask(userId: String, task: Task): Flow<FirebaseResult<Void>> = callbackFlow {
        try {
            firestore.collection("user").document(userId).collection("tasks").document(task.id ?: "").set(task).await()
            FirebaseResult.Success(null)
        } catch (e: Exception) {
            trySend(FirebaseResult.Error(e))
        }
        close()
    }

    override fun deleteTask(userId: String, taskId: String): Flow<FirebaseResult<Void>> = callbackFlow {
        try {
            firestore.collection("user").document(userId).collection("tasks").document(taskId).delete().await()
            FirebaseResult.Success(null)
        } catch (e: Exception) {
            trySend(FirebaseResult.Error(e))
        }
        close()
    }

    override fun getUser(userId: String): Flow<FirebaseResult<User>>  = callbackFlow {
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
}