package com.gawasu.sillyn.ui.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.activity.viewModels
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.ActivityAuthenticationBinding
import com.gawasu.sillyn.ui.fragment.AuthenticationOptionsFragment
import com.gawasu.sillyn.ui.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import com.gawasu.sillyn.utils.FirebaseResult

@AndroidEntryPoint
class AuthenticationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthenticationBinding
    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var fragmentManager: FragmentManager
    private var isInitialLoginCheckDone = false // Flag to prevent multiple checks

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthenticationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fragmentManager = supportFragmentManager

        // Load AuthenticationOptionsFragment as the initial fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container_authentication, AuthenticationOptionsFragment())
                .commit()
        }
        Log.d(TAG, "onCreate: AuthenticationActivity onCreate called") // Log onCreate
        observeViewModel() // **Setup observers in onCreate**
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: AuthenticationActivity onStart called - SUPER ONSTART CALLED") // Log onStart - SUPER ONSTART CALLED
        if (!isInitialLoginCheckDone) { // Check the flag
            checkInitialLoginState()
            isInitialLoginCheckDone = true // Set flag after first check
        }
        Log.d(TAG, "onStart: checkInitialLoginState() called from onStart") // Log after calling checkInitialLoginState

    }


    private fun checkInitialLoginState() {
        Log.d(TAG, "Checking initial login state...")
        if (authViewModel.getCurrentUser() != null) {
            Log.i(TAG, "User already logged in, navigating to MainActivity directly from AuthenticationActivity")
            navigateToMainActivity()
        } else {
            Log.i(TAG, "User not logged in, showing Authentication Options")
        }
    }

    private fun observeViewModel() {
        Log.d(TAG, "observeViewModel: STARTING observeViewModel in AuthenticationActivity") // Log at start of observeViewModel
        Log.d(TAG, "observeViewModel: Setting up loginResult observer") // Log before setting up loginResult observer
        authViewModel.loginResult.observe(this) { result ->
            Log.d(TAG, "loginResult Observer triggered in Activity, result: $result") // Log when observer is triggered in Activity
            when (result) {
                is FirebaseResult.Loading -> {
                    Log.d(TAG, "Login Loading...")
                    // TODO: Show loading indicator (nếu cần ở Activity level)
                }
                is FirebaseResult.Success -> {
                    // Login successful
                    Log.i(TAG, "Login Success in Activity, navigating to MainActivity")
                    navigateToMainActivity()
                }
                is FirebaseResult.Error -> {
                    // Login failed - Fragment sẽ handle hiển thị lỗi, Activity có thể không cần handle ở đây
                    val errorMessage = result.exception.localizedMessage ?: getString(R.string.authentication_error, "Unknown error")
                    Log.e(TAG, "Login Failed in Activity: $errorMessage")
                    // Toast.makeText(this, getString(R.string.login_failed, errorMessage), Toast.LENGTH_LONG).show()
                    // TODO: Hide loading indicator
                }
            }
        }

        Log.d(TAG, "observeViewModel: Setting up signUpResult observer") // Log before setting up signUpResult observer
        authViewModel.signUpResult.observe(this) { result ->
            when (result) {
                is FirebaseResult.Loading -> {
                    Log.d(TAG, "Signup Loading...")
                    // TODO: Show loading indicator if needed
                }
                is FirebaseResult.Success -> {
                    // Signup successful - You might navigate to MainActivity directly after signup as well, or stay in auth flow.
                    // For this example, we'll stay in auth flow and let user login after signup.
                    Log.i(TAG, "Signup Success, staying in AuthenticationActivity for login")
                    // Optionally navigate to MainActivity after signup as well
                    // navigateToMainActivity()
                }
                is FirebaseResult.Error -> {
                    // Signup failed
                    val errorMessage = result.exception.localizedMessage ?: getString(R.string.authentication_error, "Unknown error")
                    Log.e(TAG, "Signup Failed: $errorMessage", result.exception)
                    // Toast.makeText(this, getString(R.string.signup_failed, errorMessage), Toast.LENGTH_LONG).show()
                    // TODO: Hide loading indicator
                }
            }
        }
        Log.d(TAG, "observeViewModel: FINISHED setting up observers") // Log after setting up observers
    }

    private fun navigateToMainActivity() {
        Log.d(TAG, "navigateToMainActivity called from Activity") // Log before starting MainActivity
        startActivity(Intent(this, MainActivity::class.java))
        Log.i(TAG, "Navigating to MainActivity from Activity...")
        finish()
    }

    companion object{
        private const val TAG = "AUTHENTICATION ACTIVITY"
    }
}