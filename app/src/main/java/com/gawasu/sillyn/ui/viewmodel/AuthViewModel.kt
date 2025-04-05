package com.gawasu.sillyn.ui.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawasu.sillyn.data.repository.AuthRepository
import com.gawasu.sillyn.utils.FirebaseResult
import kotlinx.coroutines.launch

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    val loginResult = MutableLiveData<FirebaseResult<Boolean>>()
    val signUpResult = MutableLiveData<FirebaseResult<Boolean>>()
    val resetPasswordResult = MutableLiveData<FirebaseResult<Boolean>>()

    fun loginWithEmailPassword(email: String, password: String) {
        loginResult.postValue(FirebaseResult.Loading)
        viewModelScope.launch {
            val result = authRepository.loginWithEmailPassword(email, password)
            loginResult.postValue(result)
        }
    }

    fun signUpWithEmailPassword(email: String, password: String) {
        signUpResult.postValue(FirebaseResult.Loading)
        viewModelScope.launch {
            val result = authRepository.signUpWithEmailPassword(email, password)
            signUpResult.postValue(result)
        }
    }

    fun signInWithGoogle(idToken: String) {
        loginResult.postValue(FirebaseResult.Loading)
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(idToken)
            loginResult.postValue(result)
        }
    }

    fun sendPasswordResetEmail(email: String) {
        resetPasswordResult.postValue(FirebaseResult.Loading)
        viewModelScope.launch {
            val result = authRepository.sendPasswordResetEmail(email)
            resetPasswordResult.postValue(result)
        }
    }

    fun getCurrentUser() = authRepository.getCurrentUser()
}