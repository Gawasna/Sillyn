package com.gawasu.sillyn.data.repository

import android.util.Log
import com.gawasu.sillyn.data.remote.auth.AuthDataSource
import com.gawasu.sillyn.utils.FirebaseResult
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val authDataSource: AuthDataSource
) : AuthRepositoryInterface {

    companion object {
        private const val TAG = "AUTH REPOSITORY"
    }

    //TODO: Remove Log When Project is DONE

    override suspend fun loginWithEmailPassword(email: String, password: String): FirebaseResult<Boolean> {
        Log.d(TAG, "LOGIN: Called with email: $email")
        val result = authDataSource.loginWithEmailPassword(email, password)
        Log.d(TAG, "LOGIN: Result - $result")
        return result
    }

    override suspend fun signUpWithEmailPassword(email: String, password: String): FirebaseResult<Boolean> {
        Log.d(TAG, "SIGN UP: Called with email: $email")
        val result = authDataSource.signUpWithEmailPassword(email, password)
        Log.d(TAG, "SIGN UP: Result - $result")
        return result
    }

    override suspend fun sendPasswordResetEmail(email: String): FirebaseResult<Boolean> {
        Log.d(TAG, "PASSWORD RESET: Called with email: $email")
        val result = authDataSource.sendPasswordResetEmail(email)
        Log.d(TAG, "PASSWORD RESET: Result - $result")
        return result
    }

    override suspend fun signInWithGoogle(idToken: String): FirebaseResult<Boolean> {
        Log.d(TAG, "GOOGLE SIGN IN: Called with ID token")
        val result = authDataSource.signInWithGoogle(idToken)
        Log.d(TAG, "GOOGLE SIGN IN: Result - $result")
        return result
    }

    override fun signOut() {
        Log.d(TAG, "LOG OUT: Called")
        authDataSource.signOut()
        Log.d(TAG, "LOG OUT: SUCCESS")
    }

    override fun getCurrentUser() = authDataSource.getCurrentUser()
}