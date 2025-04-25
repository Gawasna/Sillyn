package com.gawasu.sillyn.data.remote.firestore

import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.domain.model.User
import com.gawasu.sillyn.utils.FirebaseResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FirestoreDataSource @Inject constructor(
    private val firestoreService: FirestoreService
) {
    fun getTasks(userId: String): Flow<FirebaseResult<List<Task>>> = firestoreService.getTasks(userId)
    fun getTaskCategories(userId: String): Flow<FirebaseResult<List<String>>> = firestoreService.getTaskCategories(userId)
    fun addTask(userId: String, task: Task): Flow<FirebaseResult<Void>> = firestoreService.addTask(userId, task)
    fun updateTask(userId: String, task: Task): Flow<FirebaseResult<Void>> = firestoreService.updateTask(userId, task)
    fun deleteTask(userId: String, taskId: String): Flow<FirebaseResult<Void>> = firestoreService.deleteTask(userId, taskId)
    fun getUser(userId: String): Flow<FirebaseResult<User>> = firestoreService.getUser(userId)
}