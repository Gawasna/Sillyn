package com.gawasu.sillyn.ui.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawasu.sillyn.data.repository.AuthRepository
import com.gawasu.sillyn.utils.FirebaseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val TAG = "AuthViewModel"
    val loginResult = MutableLiveData<FirebaseResult<Boolean>>()
    val signUpResult = MutableLiveData<FirebaseResult<Boolean>>()
    val resetPasswordResult = MutableLiveData<FirebaseResult<Boolean>>()

    fun loginWithEmailPassword(email: String, password: String) {
        Log.d(TAG, "loginWithEmailPassword called for email: $email")
        loginResult.postValue(FirebaseResult.Loading)
        viewModelScope.launch {
            val result = authRepository.loginWithEmailPassword(email, password)
            Log.d(TAG, "loginWithEmailPassword result: $result") // Log result
            loginResult.postValue(result)
        }
    }

    fun signUpWithEmailPassword(email: String, password: String) {
        Log.d(TAG, "signUpWithEmailPassword called for email: $email")
        signUpResult.postValue(FirebaseResult.Loading)
        viewModelScope.launch {
            val result = authRepository.signUpWithEmailPassword(email, password)
            Log.d(TAG, "signUpWithEmailPassword result: $result") // Log result
            signUpResult.postValue(result)
        }
    }

    fun signInWithGoogle(idToken: String) {
        Log.d(TAG, "signInWithGoogle called")
        loginResult.postValue(FirebaseResult.Loading)
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(idToken)
            Log.d(TAG, "signInWithGoogle result: $result") // Log result
            loginResult.postValue(result)
        }
    }

    fun sendPasswordResetEmail(email: String) {
        Log.d(TAG, "sendPasswordResetEmail called for email: $email")
        resetPasswordResult.postValue(FirebaseResult.Loading)
        viewModelScope.launch {
            val result = authRepository.sendPasswordResetEmail(email)
            Log.d(TAG, "sendPasswordResetEmail result: $result") // Log result
            resetPasswordResult.postValue(result)
        }
    }

    fun getCurrentUser() = authRepository.getCurrentUser()
}