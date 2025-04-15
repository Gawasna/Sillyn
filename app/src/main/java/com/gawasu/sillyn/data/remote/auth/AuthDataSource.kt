package com.gawasu.sillyn.data.remote.auth

import com.gawasu.sillyn.utils.FirebaseResult
import com.google.firebase.auth.FirebaseUser
import javax.inject.Inject

class AuthDataSource @Inject constructor(
    private val authService: AuthService
) {
    suspend fun loginWithEmailPassword(email: String, password: String): FirebaseResult<Boolean> = authService.loginWithEmailPassword(email, password)
    suspend fun signUpWithEmailPassword(email: String, password: String): FirebaseResult<Boolean> = authService.signUpWithEmailPassword(email, password)
    suspend fun sendPasswordResetEmail(email: String): FirebaseResult<Boolean> = authService.sendPasswordResetEmail(email)
    suspend fun signInWithGoogle(idToken: String): FirebaseResult<Boolean> = authService.signInWithGoogle(idToken)
    fun signOut() {
        authService.signOut()
    }
    fun getCurrentUser(): FirebaseUser? = authService.getCurrentUser()
}