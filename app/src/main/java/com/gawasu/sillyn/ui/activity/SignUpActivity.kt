package com.gawasu.sillyn.ui.activity

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.gawasu.sillyn.databinding.ActivitySignupBinding
import com.gawasu.sillyn.ui.viewmodel.AppStateManager
import com.gawasu.sillyn.ui.viewmodel.AuthViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var authViewModel: AuthViewModel


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val appStateManager = AppStateManager.getInstance(applicationContext) // Get AppStateManager instance
        authViewModel = ViewModelProvider(this, AuthViewModelFactory(application, appStateManager))
            .get(AuthViewModel::class.java)


        binding.btnSignUp.setOnClickListener { signUp() }
        binding.tvBackToLogin.setOnClickListener { finish() }
        setupObservers()
    }

    private fun signUp() {
        val displayName = binding.etName.text.toString()
        val email = binding.etEmail.text.toString()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        // Validate inputs
        if (TextUtils.isEmpty(displayName)) {
            binding.etName.error = "Tên hiển thị không được để trống"
            return
        }

        if (TextUtils.isEmpty(email)) {
            binding.etEmail.error = "Email không được để trống"
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Email không hợp lệ"
            return
        }

        if (TextUtils.isEmpty(password)) {
            binding.etPassword.error = "Mật khẩu không được để trống"
            return
        }

        if (password.length < 6) {
            binding.etPassword.error = "Mật khẩu phải có ít nhất 6 ký tự"
            return
        }

        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Mật khẩu xác nhận không khớp"
            return
        }

        // Show loading
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSignUp.isEnabled = false

        authViewModel.signUpWithEmail(displayName, email, password)
    }

    private fun setupObservers() {
        authViewModel.signUpLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnSignUp.isEnabled = !isLoading
        }

        authViewModel.signUpSuccess.observe(this) { isSuccess ->
            if (isSuccess) {
                navigateToMainActivity()
            }
        }

        authViewModel.signUpError.observe(this) { errorMessage ->
            errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    companion object {
        private const val TAG = "SignUpActivity"
    }
}