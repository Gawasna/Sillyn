package com.gawasu.sillyn.data.remote.auth

import com.gawasu.sillyn.utils.FirebaseResult
import com.google.firebase.auth.FirebaseUser

interface AuthService {
    suspend fun loginWithEmailPassword(email: String, password: String): FirebaseResult<Boolean>
    suspend fun signUpWithEmailPassword(email: String, password: String): FirebaseResult<Boolean>
    suspend fun sendPasswordResetEmail(email: String): FirebaseResult<Boolean>
    suspend fun signInWithGoogle(idToken: String): FirebaseResult<Boolean>
    fun signOut()
    fun getCurrentUser(): FirebaseUser?
}