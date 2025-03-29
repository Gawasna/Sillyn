package com.gawasu.sillyn.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.gawasu.sillyn.databinding.ActivitySplashBinding
import com.gawasu.sillyn.ui.viewmodel.AppStateManager
import com.gawasu.sillyn.ui.viewmodel.SplashViewModel
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
                                Intent(this@SplashActivity, LoginActivity::class.java)
                            }
                            SplashViewModel.NavigationTarget.MAIN -> {
                                Intent(this@SplashActivity, MainActivity::class.java)
                            }

                            SplashViewModel.NavigationTarget.ONBOARDING -> {
                                Intent(this@SplashActivity, MainActivity::class.java)
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