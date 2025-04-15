package com.gawasu.sillyn.data.remote.auth

import android.util.Log
import com.gawasu.sillyn.data.remote.firestore.FirestoreService
import com.gawasu.sillyn.utils.FirebaseResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthServiceImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val firestoreService: FirestoreService
) : AuthService {

    companion object {
        private const val TAG = "AUTH SERVICE IMPL"
    }

    override suspend fun loginWithEmailPassword(email: String, password: String): FirebaseResult<Boolean> {
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

    override suspend fun signUpWithEmailPassword(email: String, password: String): FirebaseResult<Boolean> {
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

                    firestoreService.addTask(user.uid, com.gawasu.sillyn.domain.model.Task(title = "Welcome Task", description = "Get started by creating your first task!"))
                        .collectLatest {}
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

    override suspend fun signInWithGoogle(idToken: String): FirebaseResult<Boolean> {
        Log.d(TAG, "GOOGLE SIGN IN: Start with ID token")
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user
            Log.d(TAG, "GOOGLE SIGN IN: Firebase Auth completed successfully") // Log sau firebaseAuth.signInWithCredential

            if (user != null) {
                val userRef = firestore.collection("user").document(user.uid)
                val snapshot = userRef.get().await()
                Log.d(TAG, "GOOGLE SIGN IN: Firestore user snapshot fetched") // Log sau firestore.collection("user").document(user.uid).get()

                if (!snapshot.exists()) {
                    val userData = mapOf(
                        "displayName" to (user.displayName ?: "Default Name"),
                        "email" to (user.email ?: ""),
                        "photoURL" to (user.photoUrl?.toString() ?: "default_avatar_url")
                    )
                    userRef.set(userData).await()
                    Log.i(TAG, "GOOGLE SIGN IN: User document created in Firestore")

                    firestoreService.addTask(user.uid, com.gawasu.sillyn.domain.model.Task(title = "Welcome Task", description = "Get started by creating your first task!"))
                        .collectLatest {}
                } else {
                    Log.i(TAG, "GOOGLE SIGN IN: User document already exists, skipped creation")
                }
            }
            Log.i(TAG, "GOOGLE SIGN IN: SUCCESS, returning FirebaseResult.Success(true)") // Log trước khi return Success
            FirebaseResult.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "GOOGLE SIGN IN: ERROR - ${e.message}", e)
            FirebaseResult.Error(e)
        }
    }


    override suspend fun sendPasswordResetEmail(email: String): FirebaseResult<Boolean> {
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

    override fun signOut() {
        Log.d(TAG, "FIREBASE AUTH SIGN OUT: Called")
        firebaseAuth.signOut()
        Log.i(TAG, "FIREBASE AUTH SIGN OUT: SUCCESS")
    }

    override fun getCurrentUser() = firebaseAuth.currentUser
}