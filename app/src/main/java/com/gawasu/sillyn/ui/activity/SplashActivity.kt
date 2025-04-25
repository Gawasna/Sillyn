package com.gawasu.sillyn.ui.activity

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.gawasu.sillyn.databinding.ActivitySplashBinding
import com.gawasu.sillyn.ui.viewmodel.AppStateManager
import com.gawasu.sillyn.ui.viewmodel.SplashViewModel
import com.google.android.gms.auth.api.Auth
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding

    private val splashViewModel: SplashViewModel by viewModels {
        SplashViewModelFactory(application, AppStateManager.getInstance(applicationContext)) // Provide AppStateManager instance
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val progressBar = binding.splashProgressBar

        // Kiểm tra kết nối Internet
//        if (!isNetworkAvailable()) {
//            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
//            return // Dừng lại nếu không có kết nối internet
//        }

        lifecycleScope.launch {
            launch {
                splashViewModel.progress.collect { progress ->
                    progressBar.progress = progress
                }
            }

            launch {
                splashViewModel.navigationTarget.collect { target ->
                    target?.let {
                        val intent = when (it) {
                            SplashViewModel.NavigationTarget.LOGIN -> {
                                Intent(this@SplashActivity, AuthenticationActivity::class.java)
                            }
                            SplashViewModel.NavigationTarget.MAIN -> {
                                Intent(this@SplashActivity, MainActivity::class.java)
                            }
                            SplashViewModel.NavigationTarget.ONBOARDING -> TODO()
                        }
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }
    }

    // Kiểm tra kết nối mạng
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }
}


class SplashViewModelFactory(
    private val application: android.app.Application,
    private val appStateManager: AppStateManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SplashViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SplashViewModel(application, appStateManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
