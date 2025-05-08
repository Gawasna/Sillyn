package com.gawasu.sillyn.ui.activity

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.gawasu.sillyn.databinding.ActivitySplashBinding
import com.gawasu.sillyn.ui.fragment.IntroFragment
import com.gawasu.sillyn.ui.viewmodel.AppStateManager
import com.gawasu.sillyn.ui.viewmodel.SplashViewModel
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding

    private lateinit var prefs: SharedPreferences

    private val splashViewModel: SplashViewModel by viewModels {
        SplashViewModelFactory(application, AppStateManager.getInstance(applicationContext))
    }

    companion object {
        private const val KEY_FIRST_LAUNCH = "is_first_launch"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val progressBar = binding.splashProgressBar
        val statusTextView = binding.tvLoadingText // Thêm dòng này để lấy TextView cập nhật status

        // Nếu cần kiểm tra Internet thì mở đoạn này
//        if (!isNetworkAvailable()) {
//            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
//            return
//        }

        lifecycleScope.launch {

            launch {
                splashViewModel.progress.collect { progress ->
                    progressBar.progress = progress
                }
            }

            launch {
                splashViewModel.statusText.collect { text ->
                    statusTextView.text = text
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
                            SplashViewModel.NavigationTarget.ONBOARDING -> {
                                Intent(this@SplashActivity, OnboardingActivity::class.java)
                            }
                        }
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    private fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
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
