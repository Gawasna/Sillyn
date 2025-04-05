package com.gawasu.sillyn.data.remote

import com.gawasu.sillyn.data.repository.TaskRepository
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.domain.model.User
import com.gawasu.sillyn.utils.FirebaseResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FirestoreDataSource @Inject constructor(
    private val firestoreService: FirestoreService
) : TaskRepository { // Implement TaskRepository interface

    override fun getTasks(userId: String): Flow<FirebaseResult<List<Task>>> {
        return firestoreService.getTasks(userId)
    }

    override fun addTask(userId: String, task: Task): Flow<FirebaseResult<Void>> {
        return firestoreService.addTask(userId, task)
    }

    override fun updateTask(userId: String, task: Task): Flow<FirebaseResult<Void>> {
        return firestoreService.updateTask(userId, task)
    }

    override fun deleteTask(userId: String, taskId: String): Flow<FirebaseResult<Void>> {
        return firestoreService.deleteTask(userId, taskId)
    }

    // ... (Implement other TaskRepository methods if needed)

    // Example for UserRepository if you create UserRepository interface
    fun getUser(userId: String): Flow<FirebaseResult<User>> {
        return firestoreService.getUser(userId)
    }
}