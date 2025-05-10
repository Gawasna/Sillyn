package com.gawasu.sillyn.data.repository

import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.domain.model.User
import com.gawasu.sillyn.utils.FirebaseResult
import kotlinx.coroutines.flow.Flow
import java.util.Date

interface TaskRepositoryInterface {
    fun getTasks(userId: String): Flow<FirebaseResult<List<Task>>>

    //fun addTask(userId: String, task: Task): Flow<FirebaseResult<Void>>
    fun addTask(userId: String, task: Task): Flow<FirebaseResult<Task>>

    fun updateTask(userId: String, task: Task): Flow<FirebaseResult<Void>>
    fun deleteTask(userId: String, taskId: String): Flow<FirebaseResult<Void>>
    fun getUser(userId: String): Flow<FirebaseResult<User>>
    fun getTaskCategories(userId: String): Flow<FirebaseResult<List<String>>>

    fun getTodayTasks(userId: String): Flow<FirebaseResult<List<Task>>>
    fun getWeekTasks(userId: String): Flow<FirebaseResult<List<Task>>>
    fun getTasksByCategory(userId: String, category: String): Flow<FirebaseResult<List<Task>>>

    fun getTaskById(userId: String, taskId: String): Flow<FirebaseResult<Task?>>
    fun getUpcomingTasksWithDeadlines(userId: String): Flow<FirebaseResult<List<Task>>>

    fun getTasksInRange(userId: String, startDate: Date, endDate: Date): Flow<FirebaseResult<List<Task>>>
}