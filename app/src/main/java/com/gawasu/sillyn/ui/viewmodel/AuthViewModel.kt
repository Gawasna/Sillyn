package com.gawasu.sillyn.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawasu.sillyn.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.launch

class AuthViewModel(
    private val context: Context,
    private val appStateManager: AppStateManager
) : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private lateinit var googleSignInClient: GoogleSignInClient

    // --- Google Sign-in ---
    private val _googleSignInLoading = MutableLiveData<Boolean>()
    val googleSignInLoading: LiveData<Boolean> get() = _googleSignInLoading

    private val _googleSignInSuccess = MutableLiveData<Boolean>()
    val googleSignInSuccess: LiveData<Boolean> get() = _googleSignInSuccess

    private val _googleSignInError = MutableLiveData<String?>()
    val googleSignInError: LiveData<String?> get() = _googleSignInError

    // --- Email Login ---
    private val _emailLoginLoading = MutableLiveData<Boolean>()
    val emailLoginLoading: LiveData<Boolean> get() = _emailLoginLoading

    private val _emailLoginSuccess = MutableLiveData<Boolean>()
    val emailLoginSuccess: LiveData<Boolean> get() = _emailLoginSuccess

    private val _emailLoginError = MutableLiveData<String?>()
    val emailLoginError: LiveData<String?> get() = _emailLoginError

    // --- Sign Up ---
    private val _signUpLoading = MutableLiveData<Boolean>()
    val signUpLoading: LiveData<Boolean> get() = _signUpLoading

    private val _signUpSuccess = MutableLiveData<Boolean>()
    val signUpSuccess: LiveData<Boolean> get() = _signUpSuccess

    private val _signUpError = MutableLiveData<String?>()
    val signUpError: LiveData<String?> get() = _signUpError

    init {
        initializeGoogleSignInClient()
    }

    private fun initializeGoogleSignInClient() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }

    fun signInWithGoogle(launcher: ActivityResultLauncher<Intent>) {
        val signInIntent = googleSignInClient.signInIntent
        launcher.launch(signInIntent)
    }

    fun handleGoogleSignInResult(intent: Intent?) {
        _googleSignInLoading.value = true
        _googleSignInError.value = null // Clear any previous error

        viewModelScope.launch {
            val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
            try {
                val account = task.getResult(ApiException::class.java)
                Log.d(TAG, "Google sign in success: ${account.email}")
                account.idToken?.let { firebaseAuthWithGoogle(it) }
            } catch (e: ApiException) {
                Log.w(TAG, "Google sign in failed", e)
                _googleSignInError.postValue("Đăng nhập Google thất bại: ${e.message}")
                _googleSignInLoading.postValue(false)
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                _googleSignInLoading.postValue(false)
                if (task.isSuccessful) {
                    Log.d(TAG, "firebaseAuthWithGoogle:success")
                    _googleSignInSuccess.postValue(true)
                    // AppStateManager will handle state update via auth listener
                } else {
                    Log.w(TAG, "firebaseAuthWithGoogle:failure", task.exception)
                    _googleSignInError.postValue("Đăng nhập Google thất bại: ${task.exception?.message}")
                    _googleSignInSuccess.postValue(false)
                }
            }
    }

    fun loginWithEmail(email: String, password: String) {
        _emailLoginLoading.value = true
        _emailLoginError.value = null // Clear any previous error

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                _emailLoginLoading.postValue(false)
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success")
                    _emailLoginSuccess.postValue(true)
                    // AppStateManager will handle state update via auth listener
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.exception)

                    val errorMessage = when (task.exception) {
                        is FirebaseAuthInvalidUserException -> "Tài khoản không tồn tại"
                        is FirebaseAuthInvalidCredentialsException -> "Email hoặc mật khẩu không đúng"
                        else -> "Đăng nhập Email thất bại: ${task.exception?.message}"
                    }
                    _emailLoginError.postValue(errorMessage)
                    _emailLoginSuccess.postValue(false)
                }
            }
    }

    fun signUpWithEmail(displayName: String, email: String, password: String) {
        _signUpLoading.value = true
        _signUpError.value = null // Clear any previous error

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "createUserWithEmail:success")

                    val user = auth.currentUser
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(displayName)
                        .build()

                    user?.updateProfile(profileUpdates)
                        ?.addOnCompleteListener { profileTask ->
                            _signUpLoading.postValue(false)
                            if (profileTask.isSuccessful) {
                                Log.d(TAG, "User profile updated")
                                _signUpSuccess.postValue(true)
                                // AppStateManager will handle state update via auth listener
                            } else {
                                Log.w(TAG, "Error updating user profile", profileTask.exception)
                                _signUpError.postValue("Đăng ký thành công, nhưng lỗi cập nhật profile.")
                                _signUpSuccess.postValue(true) // Still consider sign up success
                            }
                        }
                } else {
                    Log.w(TAG, "createUserWithEmail:failure", task.exception)
                    _signUpLoading.postValue(false)

                    val errorMessage = when (task.exception) {
                        is FirebaseAuthWeakPasswordException -> "Mật khẩu quá yếu"
                        is FirebaseAuthUserCollisionException -> "Email đã được sử dụng"
                        else -> "Đăng ký thất bại: ${task.exception?.message}"
                    }
                    _signUpError.postValue(errorMessage)
                    _signUpSuccess.postValue(false)
                }
            }
    }

    fun isLoggedIn(): Boolean {
        return appStateManager.isLoggedIn()
    }

    fun signOut() {
        appStateManager.signOut()
        googleSignInClient.signOut()
    }

    companion object {
        private const val TAG = "AuthViewModel"
    }
}