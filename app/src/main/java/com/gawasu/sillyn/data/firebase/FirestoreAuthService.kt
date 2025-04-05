package com.gawasu.sillyn.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.gawasu.sillyn.utils.FirebaseResult
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import java.lang.Exception

class FirestoreAuthService {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "FIREBASE AUTH SERVICES"
    }

    //TODO: Remove Log When Project is DONE

    suspend fun loginWithEmailPassword(email: String, password: String): FirebaseResult<Boolean> {
        Log.d(TAG, "MAIL SIGN IN: Start with email: $email")
        return try {
            firebaseAuth.signInWithEmailAndPassword(email, password).await()
            Log.i(TAG, "MAIL SIGN IN: SUCCESS")
            FirebaseResult.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "MAIL SIGN IN: ERROR - ${e.message}", e)
            FirebaseResult.Error(e)
        }
    }

    suspend fun signUpWithEmailPassword(email: String, password: String): FirebaseResult<Boolean> {
        Log.d(TAG, "SIGN UP: Start with email: $email")
        return try {
            firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            Log.i(TAG, "SIGN UP: SUCCESS")
            FirebaseResult.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "SIGN UP: ERROR - ${e.message}", e)
            FirebaseResult.Error(e)
        }
    }

    suspend fun signInWithGoogle(idToken: String): FirebaseResult<Boolean> {
        Log.d(TAG, "GOOGLE SIGN IN: Start with ID token")
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential).await()
            Log.i(TAG, "GOOGLE SIGN IN: SUCCESS")
            FirebaseResult.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "GOOGLE SIGN IN: ERROR - ${e.message}", e)
            FirebaseResult.Error(e)
        }
    }

    suspend fun sendPasswordResetEmail(email: String): FirebaseResult<Boolean> {
        Log.d(TAG, "PASSWORD RESET: Start with email: $email")
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Log.i(TAG, "PASSWORD RESET: SUCCESS")
            FirebaseResult.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "PASSWORD RESET: ERROR - ${e.message}", e)
            FirebaseResult.Error(e)
        }
    }

    fun getCurrentUser() = firebaseAuth.currentUser
}