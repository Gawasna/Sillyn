package com.gawasu.sillyn.data.repository

import com.gawasu.sillyn.data.remote.firestore.FirestoreDataSource
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.utils.FirebaseResult
import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource
) : TaskRepositoryInterface {
    override fun getTasks(userId: String): Flow<FirebaseResult<List<Task>>> = firestoreDataSource.getTasks(userId)

    //override fun addTask(userId: String, task: Task): Flow<FirebaseResult<Void>> = firestoreDataSource.addTask(userId, task)
    override fun addTask(userId: String, task: Task): Flow<FirebaseResult<Task>> = firestoreDataSource.addTask(userId, task)

    override fun updateTask(userId: String, task: Task): Flow<FirebaseResult<Void>> = firestoreDataSource.updateTask(userId, task)
    override fun deleteTask(userId: String, taskId: String): Flow<FirebaseResult<Void>> = firestoreDataSource.deleteTask(userId, taskId)
    override fun getUser(userId: String): Flow<FirebaseResult<com.gawasu.sillyn.domain.model.User>> = firestoreDataSource.getUser(userId)
    override fun getTaskCategories(userId: String): Flow<FirebaseResult<List<String>>> = firestoreDataSource.getTaskCategories(userId)

    override fun getTodayTasks(userId: String): Flow<FirebaseResult<List<Task>>> = firestoreDataSource.getTodayTasks(userId)
    override fun getWeekTasks(userId: String): Flow<FirebaseResult<List<Task>>> = firestoreDataSource.getWeekTasks(userId)
    override fun getTasksByCategory(userId: String, category: String): Flow<FirebaseResult<List<Task>>> = firestoreDataSource.getTasksByCategory(userId, category)

    override fun getTaskById(userId: String, taskId: String): Flow<FirebaseResult<Task?>> = firestoreDataSource.getTaskById(userId, taskId)
    override fun getUpcomingTasksWithDeadlines(userId: String): Flow<FirebaseResult<List<Task>>> = firestoreDataSource.getUpcomingTasksWithDeadlines(userId)

    override fun getTasksInRange(userId: String, startDate: Date, endDate: Date): Flow<FirebaseResult<List<Task>>> = firestoreDataSource.getTasksInRange(userId, startDate, endDate)
}