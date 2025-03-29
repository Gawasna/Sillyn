package com.gawasu.sillyn.ui.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.gawasu.sillyn.databinding.ActivityLoginBinding
import com.gawasu.sillyn.ui.viewmodel.AppStateManager
import com.gawasu.sillyn.ui.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var authViewModel: AuthViewModel

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authViewModel.handleGoogleSignInResult(result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val appStateManager = AppStateManager.getInstance(applicationContext) // Get AppStateManager instance
        authViewModel = ViewModelProvider(this, AuthViewModelFactory(application, appStateManager))
            .get(AuthViewModel::class.java)

        setupListeners()
        setupObservers()
    }

    private fun setupListeners() {
        binding.btnLoginEmail.setOnClickListener {
            Toast.makeText(this, "Đăng nhập Email sẽ được triển khai sau", Toast.LENGTH_SHORT).show()
        }

        binding.btnLoginGoogle.setOnClickListener {
            authViewModel.signInWithGoogle(googleSignInLauncher)
        }

        binding.tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "Chức năng quên mật khẩu sẽ được triển khai sau", Toast.LENGTH_SHORT).show()
        }

        binding.tvOtherLoginMethods.setOnClickListener {
            Toast.makeText(this, "Phương thức đăng nhập khác sẽ được triển khai sau", Toast.LENGTH_SHORT).show()
        }

        binding.tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        binding.tvPrivacyTerms.setOnClickListener {
            Toast.makeText(this, "Điều khoản và chính sách bảo mật", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupObservers() {
        authViewModel.googleSignInLoading.observe(this) { isLoading ->
            binding.btnLoginGoogle.isEnabled = !isLoading
            // You can add a progress bar or loading animation here if needed
        }

        authViewModel.googleSignInSuccess.observe(this) { isSuccess ->
            if (isSuccess) {
                Toast.makeText(this, "Đăng nhập Google thành công", Toast.LENGTH_SHORT).show()
                navigateToMainActivity()
            }
        }

        authViewModel.googleSignInError.observe(this) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }


    override fun onStart() {
        super.onStart()
        if (authViewModel.isLoggedIn()) {
            navigateToMainActivity()
        }
    }

    companion object {
        private const val TAG = "LoginActivity"
    }
}


class AuthViewModelFactory(
    private val application: android.app.Application,
    private val appStateManager: AppStateManager
) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(application.applicationContext, appStateManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}