package com.gawasu.sillyn.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawasu.sillyn.data.repository.TaskRepository
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.utils.FirebaseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _tasks = MutableLiveData<FirebaseResult<List<Task>>>()
    val tasks: LiveData<FirebaseResult<List<Task>>> = _tasks

    private val _addTaskResult = MutableLiveData<FirebaseResult<Void>>()
    val addTaskResult: LiveData<FirebaseResult<Void>> = _addTaskResult

    private val _updateTaskResult = MutableLiveData<FirebaseResult<Void>>()
    val updateTaskResult: LiveData<FirebaseResult<Void>> = _updateTaskResult

    private val _deleteTaskResult = MutableLiveData<FirebaseResult<Void>>()
    val deleteTaskResult: LiveData<FirebaseResult<Void>> = _deleteTaskResult

    fun getTasks(userId: String) {
        viewModelScope.launch {
            taskRepository.getTasks(userId).collectLatest { result ->
                _tasks.postValue(result)
            }
        }
    }

    fun addTask(userId: String, task: Task) {
        viewModelScope.launch {
            taskRepository.addTask(userId, task).collectLatest { result ->
                _addTaskResult.postValue(result)
            }
        }
    }

    fun updateTask(userId: String, task: Task) {
        viewModelScope.launch {
            taskRepository.updateTask(userId, task).collectLatest { result ->
                _updateTaskResult.postValue(result)
            }
        }
    }

    fun deleteTask(userId: String, taskId: String) {
        viewModelScope.launch {
            taskRepository.deleteTask(userId, taskId).collectLatest { result ->
                _deleteTaskResult.postValue(result)
            }
        }
    }
}