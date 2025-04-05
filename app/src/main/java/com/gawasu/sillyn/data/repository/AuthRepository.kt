package com.gawasu.sillyn.data.repository

import com.gawasu.sillyn.data.firebase.FirestoreAuthService
import com.gawasu.sillyn.utils.FirebaseResult
import android.util.Log

class AuthRepository(private val firestoreAuthService: FirestoreAuthService) {

    companion object {
        private const val TAG = "AUTH REPOSITORY"
    }

    //TODO: Remove Log When Project is DONE

    suspend fun loginWithEmailPassword(email: String, password: String): FirebaseResult<Boolean> {
        Log.d(TAG, "LOGIN: Called with email: $email")
        val result = firestoreAuthService.loginWithEmailPassword(email, password)
        Log.d(TAG, "LOGIN: Result - $result")
        return result
    }

    suspend fun signUpWithEmailPassword(email: String, password: String): FirebaseResult<Boolean> {
        Log.d(TAG, "SIGN UP: Called with email: $email")
        val result = firestoreAuthService.signUpWithEmailPassword(email, password)
        Log.d(TAG, "SIGN UP: Result - $result")
        return result
    }

    suspend fun sendPasswordResetEmail(email: String): FirebaseResult<Boolean> {
        Log.d(TAG, "PASSWORD RESET: Called with email: $email")
        val result = firestoreAuthService.sendPasswordResetEmail(email)
        Log.d(TAG, "PASSWORD RESET: Result - $result")
        return result
    }

    suspend fun signInWithGoogle(idToken: String): FirebaseResult<Boolean> {
        Log.d(TAG, "GOOGLE SIGN IN: Called with ID token")
        val result = firestoreAuthService.signInWithGoogle(idToken)
        Log.d(TAG, "GOOGLE SIGN IN: Result - $result")
        return result
    }

    fun getCurrentUser() = firestoreAuthService.getCurrentUser()
}
