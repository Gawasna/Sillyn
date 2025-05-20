package com.gawasu.sillyn.ui.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.activity.viewModels // Sử dụng activityViewModels
import androidx.lifecycle.lifecycleScope
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.ActivityAuthenticationBinding
import com.gawasu.sillyn.ui.fragment.AuthenticationOptionsFragment
import com.gawasu.sillyn.ui.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import com.gawasu.sillyn.utils.FirebaseResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AuthenticationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthenticationBinding
    // Sử dụng activityViewModels để ViewModel tồn tại scope của Activity
    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var fragmentManager: FragmentManager
    private var isInitialLoginCheckDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthenticationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fragmentManager = supportFragmentManager

        // Load AuthenticationOptionsFragment as the initial fragment
        if (savedInstanceState == null) {
            // Thêm AuthOptions vào back stack để khi pop các fragment con sẽ quay lại đây
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container_authentication, AuthenticationOptionsFragment())
                .addToBackStack("AuthOptions") // Đặt tên cho back stack entry nếu cần quản lý phức tạp hơn
                .commit()
        }
        Log.d(TAG, "onCreate: AuthenticationActivity onCreate called")
        observeViewModel() // Setup observers in onCreate
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: AuthenticationActivity onStart called")
        // Check trạng thái đăng nhập ban đầu chỉ 1 lần
        if (!isInitialLoginCheckDone) {
            // Add a small delay to ensure UI is ready or just call directly if needed
            // For a quick check, direct call is fine.
            checkInitialLoginState()
            isInitialLoginCheckDone = true
        }
        Log.d(TAG, "onStart: checkInitialLoginState() called from onStart")
    }


    private fun checkInitialLoginState() {
        Log.d(TAG, "Checking initial login state...")
        // getCurrentUser() của ViewModel nên check trạng thái user hiện tại
        if (authViewModel.getCurrentUser() != null) {
            Log.i(TAG, "User already logged in, navigating to MainActivity directly from AuthenticationActivity")
            navigateToMainActivity()
        } else {
            Log.i(TAG, "User not logged in, showing Authentication Options")
            // Ensure AuthOptions is shown if user is not logged in initially and activity is created/recreated
            // This is handled by the savedInstanceState == null check in onCreate,
            // but you might add logic here if onStart implies needing to reset the flow
            // (e.g., if user logs out from MainActivity and returns here).
            // For this quick fix, relying on onCreate's savedInstanceState check is sufficient.
        }
    }

    private fun observeViewModel() {
        Log.d(TAG, "observeViewModel: STARTING observeViewModel in AuthenticationActivity")
        Log.d(TAG, "observeViewModel: Setting up loginResult observer")
        authViewModel.loginResult.observe(this) { result ->
            Log.d(TAG, "loginResult Observer triggered in Activity, result: $result")
            when (result) {
                is FirebaseResult.Loading -> {
                    Log.d(TAG, "Login Loading (Activity)...")
                    Toast.makeText(this, "Đang đăng nhập...", Toast.LENGTH_SHORT).show()
                }
                is FirebaseResult.Success -> {
                    Log.i(TAG, "Login Success in Activity, navigating to MainActivity")
                    Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show() // Cập nhật string
                    lifecycleScope.launch {
                        delay(1500) // Delay 1.5s
                        navigateToMainActivity()
                    }
                }
                is FirebaseResult.Error -> {
                    val errorMessage = result.exception.localizedMessage ?: getString(R.string.authentication_error, "Unknown error")
                    Log.e(TAG, "Login Failed in Activity: $errorMessage")
                    // Toast lỗi đã hiển thị ở Fragment, hoặc bạn có thể hiển thị lại ở đây
                    // Toast.makeText(this, "Đăng nhập thất bại: $errorMessage", Toast.LENGTH_LONG).show()
                }
            }
        }

        Log.d(TAG, "observeViewModel: Setting up signUpResult observer")
        authViewModel.signUpResult.observe(this) { result ->
            Log.d(TAG, "signUpResult Observer triggered in Activity, result: $result")
            when (result) {
                is FirebaseResult.Loading -> {
                    Log.d(TAG, "Signup Loading (Activity)...")
                    Toast.makeText(this, "Đang đăng ký...", Toast.LENGTH_SHORT).show() // Cập nhật string
                    // TODO: Show loading indicator
                }
                is FirebaseResult.Success -> {
                    Log.i(TAG, "Signup Success in Activity. Fragment handled toast.")
                    // Fragment đã hiển thị toast và pop back stack. Activity không cần làm gì thêm ở đây.
                    // Nếu muốn tự động đăng nhập sau signup, bạn có thể gọi loginWithEmailPassword ở đây
                    // hoặc trong ViewModel sau khi signup thành công.
                }
                is FirebaseResult.Error -> {
                    val errorMessage = result.exception.localizedMessage ?: getString(R.string.authentication_error, "Unknown error")
                    Log.e(TAG, "Signup Failed in Activity: $errorMessage", result.exception)
                    // Toast lỗi đã hiển thị ở Fragment
                    // Toast.makeText(this, getString(R.string.signup_failed, errorMessage), Toast.LENGTH_LONG).show()
                    // TODO: Hide loading indicator
                }
            }
        }
        Log.d(TAG, "observeViewModel: FINISHED setting up observers")
    }

    private fun navigateToMainActivity() {
        Log.d(TAG, "navigateToMainActivity called from Activity")
        startActivity(Intent(this, MainActivity::class.java))
        Log.i(TAG, "Navigating to MainActivity from Activity...")
        finish() // Kết thúc AuthenticationActivity sau khi chuyển sang MainActivity
    }

    companion object{
        private const val TAG = "AUTH ACTIVITY" // Cập nhật TAG
    }
}