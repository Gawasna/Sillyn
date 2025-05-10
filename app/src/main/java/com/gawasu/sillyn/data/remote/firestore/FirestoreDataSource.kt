package com.gawasu.sillyn.data.remote.firestore

import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.domain.model.User
import com.gawasu.sillyn.utils.FirebaseResult
import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject

class FirestoreDataSource @Inject constructor(
    private val firestoreService: FirestoreService
) {
    fun getTasks(userId: String): Flow<FirebaseResult<List<Task>>> = firestoreService.getTasks(userId)
    fun getTaskCategories(userId: String): Flow<FirebaseResult<List<String>>> = firestoreService.getTaskCategories(userId)

    //fun addTask(userId: String, task: Task): Flow<FirebaseResult<Void>> = firestoreService.addTask(userId, task)
    fun addTask(userId: String, task: Task): Flow<FirebaseResult<Task>> = firestoreService.addTask(userId, task)

    fun updateTask(userId: String, task: Task): Flow<FirebaseResult<Void>> = firestoreService.updateTask(userId, task)
    fun deleteTask(userId: String, taskId: String): Flow<FirebaseResult<Void>> = firestoreService.deleteTask(userId, taskId)
    fun getUser(userId: String): Flow<FirebaseResult<User>> = firestoreService.getUser(userId)

    fun getTodayTasks(userId: String): Flow<FirebaseResult<List<Task>>> = firestoreService.getTodayTasks(userId)
    fun getWeekTasks(userId: String): Flow<FirebaseResult<List<Task>>> = firestoreService.getWeekTasks(userId)
    fun getTasksByCategory(userId: String, category: String): Flow<FirebaseResult<List<Task>>> = firestoreService.getTasksByCategory(userId, category)

    fun getTaskById(userId: String, taskId: String): Flow<FirebaseResult<Task?>> = firestoreService.getTasksById(userId, taskId)
    fun getUpcomingTasksWithDeadlines(userId: String): Flow<FirebaseResult<List<Task>>> = firestoreService.getUpcomingTasksWithDeadlines(userId)

    fun getTasksInRange(userId: String, startDate: Date, endDate: Date): Flow<FirebaseResult<List<Task>>> = firestoreService.getTasksInRange(userId, startDate, endDate)
}