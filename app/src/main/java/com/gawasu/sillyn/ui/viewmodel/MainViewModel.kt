package com.gawasu.sillyn.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawasu.sillyn.data.repository.AuthRepository
import com.gawasu.sillyn.data.repository.TaskRepository
import com.gawasu.sillyn.domain.model.User
import com.gawasu.sillyn.utils.FirebaseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _userInfo = MutableLiveData<FirebaseResult<User>>()
    val userInfo: LiveData<FirebaseResult<User>> = _userInfo

    private val _navigateToAuth = MutableLiveData<Boolean>()
    val navigateToAuth: LiveData<Boolean> = _navigateToAuth

    private val _toolbarTitle = MutableLiveData<String>()
    val toolbarTitle: LiveData<String> = _toolbarTitle

    private val _isOptionsMenuVisible = MutableLiveData<Boolean>()
    val isOptionsMenuVisible: LiveData<Boolean> = _isOptionsMenuVisible

    private val _taskCategories = MutableLiveData<FirebaseResult<List<String>>>()
    val taskCategories: LiveData<FirebaseResult<List<String>>> = _taskCategories

    init {
        fetchTaskCategories()
    }

    fun checkLoggedInAndFetchUser() {
        val currentUser = authRepository.getCurrentUser()
        if (currentUser != null) {
            fetchUserInfo(currentUser.uid)
        } else {
            _navigateToAuth.value = true
        }
    }

    fun check() {
        val currentUser = authRepository.getCurrentUser()
        if (currentUser != null) {
            Log.d(TAG, "User is logged in: ${currentUser.uid}")
            fetchUserInfo(currentUser.uid)
            fetchTaskCategories()
        } else {
            Log.d(TAG, "No user is logged in.")
            _navigateToAuth.value = true
        }
    }

    private fun fetchUserInfo(userId: String) {
        _userInfo.postValue(FirebaseResult.Loading)
        viewModelScope.launch {
            taskRepository.getUser(userId)
                .collectLatest { result ->
                    _userInfo.postValue(result)
                }
        }
    }

    private fun fetchTaskCategories() {
        _taskCategories.postValue(FirebaseResult.Loading)
        val currentUser = authRepository.getCurrentUser()
        if (currentUser != null) {
            Log.d(TAG, "Fetching task categories for user: ${currentUser.uid}")
            viewModelScope.launch {
                taskRepository.getTaskCategories(currentUser.uid)
                    .collectLatest { result ->
                        Log.d(TAG, "Task categories result: $result")
                        _taskCategories.postValue(result)
                    }
            }
        } else {
            Log.e(TAG, "No current user to fetch categories")
            _taskCategories.postValue(FirebaseResult.Error(Exception("No current user to fetch categories")))
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.getCurrentUser()?.let { user ->
                Log.d("MainViewModel", "Logging out user: ${user.email}")
            } ?: Log.d("MainViewModel", "No user to logout.")
            authRepository.signOut()
            _navigateToAuth.value = true
        }
    }

    fun isUserLoggedIn(): Boolean {
        return authRepository.getCurrentUser() != null
    }

    fun fetchCurrentUser() {
        authRepository.getCurrentUser()?.let {
            fetchUserInfo(it.uid)
        } ?: run {
            _navigateToAuth.value = true
        }
    }

    fun setToolbarTitle(title: String) {
        _toolbarTitle.value = title
    }

    fun setOptionsMenuVisibility(isVisible: Boolean) {
        _isOptionsMenuVisible.value = isVisible
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}
