package com.gawasu.sillyn.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gawasu.sillyn.ui.viewmodel.SplashViewModel
import kotlinx.coroutines.launch
import com.gawasu.sillyn.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {
    private val viewModel: SplashViewModel by viewModels()
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val progressBar = binding.splashProgressBar

        lifecycleScope.launch {
            launch {
                viewModel.progress.collect { progress ->
                    progressBar.progress = progress
                }
            }

            launch {
                viewModel.navigateToLogin.collect { shouldNavigate ->
//                    shouldNavigate?.let {
//                        if (it) {
//                            startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
//                        } else {
//                            startActivity(Intent(this@SplashActivity, TaskListActivity::class.java))
//                        }
//                        finish()
//                    }
                    shouldNavigate?.let {
                        startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    }
                }
            }
        }
    }
}
