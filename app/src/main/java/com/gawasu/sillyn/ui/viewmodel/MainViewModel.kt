package com.gawasu.sillyn.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawasu.sillyn.data.repository.AuthRepository
import com.gawasu.sillyn.data.repository.TaskRepository
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

    private val _userInfo = MutableLiveData<FirebaseResult<com.gawasu.sillyn.domain.model.User>>()
    val userInfo: LiveData<FirebaseResult<com.gawasu.sillyn.domain.model.User>> = _userInfo

    private val _navigateToAuth = MutableLiveData<Boolean>()
    val navigateToAuth: LiveData<Boolean> = _navigateToAuth

    private val _toolbarTitle = MutableLiveData<String>()
    val toolbarTitle: LiveData<String> = _toolbarTitle

    private val _isOptionsMenuVisible = MutableLiveData<Boolean>()
    val isOptionsMenuVisible: LiveData<Boolean> = _isOptionsMenuVisible


    fun checkLoggedInAndFetchUser() {
        val currentUser = authRepository.getCurrentUser()
        if (currentUser != null) {
            fetchUserInfo(currentUser.uid) // Fetch user info if logged in
        } else {
            _navigateToAuth.value = true // Navigate to Authentication if not logged in
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


    fun logout() {
        viewModelScope.launch {
            authRepository.getCurrentUser()?.let { user ->
                Log.d("MainViewModel", "Logging out user: ${user.email}")
            } ?: Log.d("MainViewModel", "No user to logout.")
            authRepository.signOut() // Assuming you have a signOut function in AuthRepository/AuthService
            _navigateToAuth.value = true // Navigate to Authentication after logout
        }
    }


    fun setToolbarTitle(title: String) {
        _toolbarTitle.value = title
    }

    fun setOptionsMenuVisibility(isVisible: Boolean) {
        _isOptionsMenuVisible.value = isVisible
    }
}