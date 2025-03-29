package com.gawasu.sillyn.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gawasu.sillyn.databinding.ActivityOnboardingBinding
import com.gawasu.sillyn.ui.viewmodel.AppStateManager

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCompleteOnboarding.setOnClickListener {
            AppStateManager.getInstance(applicationContext).markOnboardingCompleted()
            startActivity(Intent(this, LoginActivity::class.java)) // Go to LoginActivity after onboarding
            finish()
        }
    }
}