package com.gawasu.sillyn.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.common.api.ApiException
// TODO: Check unsed import

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        Log.w(TAG, "Initialize firebase authentication: $auth")
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        setupListeners()
    }

    private fun setupListeners() {
        binding.emailLoginButton.setOnClickListener {
            //loginWithEmailPassword()
            Toast.makeText(this, "Đăng nhập Email Clicked", Toast.LENGTH_SHORT).show()
            // Sau này: Xử lý đăng nhập bằng email
        }

        binding.googleLoginButton.setOnClickListener {
            //signInWithGoogle()
            Toast.makeText(this, "Google Login Clicked", Toast.LENGTH_SHORT).show()
            // Sau này: Xử lý đăng nhập bằng Google
        }

        binding.createAccountTextView.setOnClickListener {
            Toast.makeText(this, "Tạo tài khoản Clicked", Toast.LENGTH_SHORT).show()
            // Sau này: Chuyển đến màn hình đăng ký
        }

        binding.continueWithoutAccountButton.setOnClickListener {
            Toast.makeText(this, "Tiếp tục không tài khoản Clicked", Toast.LENGTH_SHORT).show()

            // Lưu trạng thái "không phải lần đầu mở app" và "offline mode"
            val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit()
                .putBoolean("isFirstLaunch", false) // Đánh dấu không còn là lần đầu
                .putBoolean("useOfflineMode", true)  // Lưu trạng thái offline mode
                .apply()

            startActivity(Intent(this, TaskListActivity::class.java)) // Chuyển đến màn hình chính
            finish()
        }
    }

    private fun loginWithEmailPassword() {
        val email = binding.emailEditText.text.toString()
        val password = binding.passwordEditText.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Vui long nhap email va mat khau", Toast.LENGTH_SHORT).show()
            return
        }
        //binding.progressBarLogin.visibility = android.view.View.VISIBLE
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                //binding.progressBarLogin.visibility = android.view.View.GONE
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithEmail:success")
                    val user = auth.currentUser
                    // TODO: Navigate to Main Activity/Fragment
                    Toast.makeText(this, "Đăng nhập thành công.", Toast.LENGTH_SHORT).show()
                    finish() // Close LoginActivity after successful login
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(baseContext, "Đăng nhập thất bại: ${task.exception?.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun loginWithGoogle() {
        //binding.progressbar
        val signInIntent= googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun handleGoogleSignInResult() {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
            val account = task.getResult(ApiException::class.java)!!
            Log.d(TAG, "Google signin account: ${account.email}")

            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            // Google Sign-in failed, update UI appropriately
            Log.w(TAG, "Google signin failed", e)
            //binding.progressBarLogin.visibility = android.view.View.GONE
            Toast.makeText(this, "Đăng nhập Google thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                //binding.progressBarLogin.visibility = android.view.View.GONE
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "firebaseAuthWithGoogle:success")
                    val user = auth.currentUser
                    // TODO: Navigate to Main Activity/Fragment
                    Toast.makeText(this, "Đăng nhập Google thành công.", Toast.LENGTH_SHORT).show()
                    finish() // Close LoginActivity after successful login
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "firebaseAuthWithGoogle:failure", task.exception)
                    Toast.makeText(baseContext, "Đăng nhập Google thất bại Firebase: ${task.exception?.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // User is already signed in, navigate to main activity directly
            // TODO: Navigate to Main Activity/Fragment
            Toast.makeText(this, "Đã đăng nhập, chuyển đến ứng dụng...", Toast.LENGTH_SHORT).show()
            finish() // Close LoginActivity and go to main app screen
        }
    }

    companion object {
        private const val TAG = "LoginActivity"
    }
}