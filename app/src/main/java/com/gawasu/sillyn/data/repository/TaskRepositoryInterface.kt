package com.gawasu.sillyn.data.repository

import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.domain.model.User
import com.gawasu.sillyn.utils.FirebaseResult
import kotlinx.coroutines.flow.Flow

interface TaskRepositoryInterface {
    fun getTasks(userId: String): Flow<FirebaseResult<List<Task>>>
    fun addTask(userId: String, task: Task): Flow<FirebaseResult<Void>>
    fun updateTask(userId: String, task: Task): Flow<FirebaseResult<Void>>
    fun deleteTask(userId: String, taskId: String): Flow<FirebaseResult<Void>>
    fun getUser(userId: String): Flow<FirebaseResult<User>> // Import đầy đủ để tránh conflict nếu có conflict
}