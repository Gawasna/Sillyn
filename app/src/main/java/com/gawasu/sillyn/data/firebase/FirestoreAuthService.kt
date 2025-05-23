package com.gawasu.sillyn.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.gawasu.sillyn.utils.FirebaseResult
import com.google.firebase.Timestamp
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.lang.Exception

class FirestoreAuthService {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val firestoreTaskService: FirestoreTaskService = FirestoreTaskService()

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
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user

            if (user != null) {
                val userRef = firestore.collection("user").document(user.uid)
                val snapshot = userRef.get().await()

                if (!snapshot.exists()) {
                    val userData = mapOf(
                        "displayName" to (user.displayName ?: "Default Name"),
                        "email" to email,
                        "photoURL" to (user.photoUrl?.toString() ?: "default_avatar_url")
                    )
                    userRef.set(userData).await()
                    Log.i(TAG, "SIGN UP: User document created in Firestore")

                    // 👇 Tạo task mẫu sau khi tạo user
                    firestoreTaskService.createSampleTaskForUser(user.uid)
                } else {
                    Log.i(TAG, "SIGN UP: User document already exists, skipped creation")
                }
            }

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
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user

            if (user != null) {
                val userRef = firestore.collection("User").document(user.uid)
                val snapshot = userRef.get().await()

                if (!snapshot.exists()) {
                    val userData = mapOf(
                        "displayName" to (user.displayName ?: "Default Name"),
                        "email" to (user.email ?: ""),
                        "photoURL" to (user.photoUrl?.toString() ?: "default_avatar_url")
                    )
                    userRef.set(userData).await()
                    Log.i(TAG, "GOOGLE SIGN IN: User document created in Firestore")
                    firestoreTaskService.createSampleTaskForUser(user.uid)
                } else {
                    Log.i(TAG, "GOOGLE SIGN IN: User document already exists, skipped creation")
                }
            }

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