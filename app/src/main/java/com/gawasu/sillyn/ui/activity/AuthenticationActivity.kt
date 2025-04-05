package com.gawasu.sillyn.ui.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import com.gawasu.sillyn.R
import com.gawasu.sillyn.data.firebase.FirestoreAuthService
import com.gawasu.sillyn.data.repository.AuthRepository
import com.gawasu.sillyn.databinding.ActivityAuthenticationBinding
import com.gawasu.sillyn.ui.fragment.AuthenticationOptionsFragment
import com.gawasu.sillyn.ui.viewmodel.AuthViewModel
import com.gawasu.sillyn.ui.viewmodel.AuthViewModelFactory
import com.gawasu.sillyn.utils.FirebaseResult

class AuthenticationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthenticationBinding
    private lateinit var authViewModel: AuthViewModel
    private lateinit var fragmentManager: FragmentManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthenticationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fragmentManager = supportFragmentManager

        // Initialize ViewModel
        val authRepository = AuthRepository(FirestoreAuthService())
        val factory = AuthViewModelFactory(authRepository)
        authViewModel = ViewModelProvider(this, factory)[AuthViewModel::class.java]

        // Load AuthenticationOptionsFragment as the initial fragment
        if (savedInstanceState == null) { // Prevent fragment from being added multiple times on configuration changes
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AuthenticationOptionsFragment()) // Load AuthenticationOptionsFragment
                .commit()
        }


        observeViewModel()

        // Check if user is already logged in
        if (authViewModel.getCurrentUser() != null) {
            navigateToMainActivity()
        }
    }

    private fun observeViewModel() {
        authViewModel.loginResult.observe(this) { result ->
            when (result) {
                is FirebaseResult.Loading -> {
                    // TODO: Show loading indicator
                }
                is FirebaseResult.Success -> {
                    // Login successful
                    navigateToMainActivity()
                }
                is FirebaseResult.Error -> {
                    // Login failed
                    val errorMessage = result.exception.localizedMessage ?: getString(R.string.authentication_error, "Unknown error")
                    Toast.makeText(this, getString(R.string.login_failed, errorMessage), Toast.LENGTH_LONG).show()
                    // TODO: Hide loading indicator
                }
            }
        }
    }

    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}