package com.gawasu.sillyn.data.repository

import com.gawasu.sillyn.data.remote.FirestoreDataSource
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.utils.FirebaseResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource
) : TaskRepository {

    override fun getTasks(userId: String): Flow<FirebaseResult<List<Task>>> {
        return firestoreDataSource.getTasks(userId)
    }

    override fun addTask(userId: String, task: Task): Flow<FirebaseResult<Void>> {
        return firestoreDataSource.addTask(userId, task)
    }

    override fun updateTask(userId: String, task: Task): Flow<FirebaseResult<Void>> {
        return firestoreDataSource.updateTask(userId, task)
    }

    override fun deleteTask(userId: String, taskId: String): Flow<FirebaseResult<Void>> {
        return firestoreDataSource.deleteTask(userId, taskId)
    }

    // ... (Implement other TaskRepository methods if needed)
}