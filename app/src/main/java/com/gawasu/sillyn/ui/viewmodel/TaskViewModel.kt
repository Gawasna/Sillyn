package com.gawasu.sillyn.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawasu.sillyn.data.repository.TaskRepository
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.utils.FirebaseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> get() = _tasks

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> get() = _error

    fun loadTasks(userId: String) {
        viewModelScope.launch {
            taskRepository.getTasks(userId).collect { result ->
                when (result) {
                    is FirebaseResult.Success -> _tasks.value = result.data ?: emptyList()
                    is FirebaseResult.Error -> _error.value = result.exception.message
                    FirebaseResult.Loading -> TODO()
                }
            }
        }
    }

    fun addTask(userId: String, task: Task) {
        viewModelScope.launch {
            taskRepository.addTask(userId, task).collect { result ->
                if (result is FirebaseResult.Error) {
                    _error.value = result.exception.message
                }
            }
        }
    }

    fun updateTask(userId: String, task: Task) {
        viewModelScope.launch {
            taskRepository.updateTask(userId, task).collect { result ->
                if (result is FirebaseResult.Error) {
                    _error.value = result.exception.message
                }
            }
        }
    }

    fun deleteTask(userId: String, taskId: String) {
        viewModelScope.launch {
            taskRepository.deleteTask(userId, taskId).collect { result ->
                if (result is FirebaseResult.Error) {
                    _error.value = result.exception.message
                }
            }
        }
    }
}