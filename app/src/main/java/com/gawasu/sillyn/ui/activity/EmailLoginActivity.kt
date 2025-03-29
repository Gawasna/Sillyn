package com.gawasu.sillyn.ui.activity

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.gawasu.sillyn.databinding.ActivityEmailLoginBinding
import com.gawasu.sillyn.ui.viewmodel.AppStateManager
import com.gawasu.sillyn.ui.viewmodel.AuthViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class EmailLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmailLoginBinding
    private lateinit var authViewModel: AuthViewModel


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmailLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val appStateManager = AppStateManager.getInstance(applicationContext) // Get AppStateManager instance
        authViewModel = ViewModelProvider(this, AuthViewModelFactory(application, appStateManager))
            .get(AuthViewModel::class.java)


        // Set up button listeners
        binding.btnLogin.setOnClickListener {
            loginWithEmail()
        }
        binding.tvBackToLogin.setOnClickListener { finish() }
        setupObservers()
    }

    private fun loginWithEmail() {
        val email = binding.etEmail.text.toString()
        val password = binding.etPassword.text.toString()

        // Validate inputs
        if (TextUtils.isEmpty(email)) {
            binding.etEmail.error = "Email không được để trống"
            return
        }

        if (TextUtils.isEmpty(password)) {
            binding.etPassword.error = "Mật khẩu không được để trống"
            return
        }

        // Show loading
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false

        authViewModel.loginWithEmail(email, password)
    }

    private fun setupObservers() {
        authViewModel.emailLoginLoading.observe(this) { isLoading ->
            binding.btnLogin.isEnabled = !isLoading
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        authViewModel.emailLoginSuccess.observe(this) { isSuccess ->
            if (isSuccess) {
                navigateToMainActivity()
            }
        }

        authViewModel.emailLoginError.observe(this) { errorMessage ->
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
        private const val TAG = "EmailLoginActivity"
    }
}