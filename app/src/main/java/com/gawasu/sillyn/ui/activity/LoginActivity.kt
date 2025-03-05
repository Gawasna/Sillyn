package com.gawasu.sillyn.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gawasu.sillyn.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.emailLoginButton.setOnClickListener {
            Toast.makeText(this, "Đăng nhập Email Clicked", Toast.LENGTH_SHORT).show()
            // Sau này: Xử lý đăng nhập bằng email
        }

        binding.googleLoginButton.setOnClickListener {
            Toast.makeText(this, "Google Login Clicked", Toast.LENGTH_SHORT).show()
            // Sau này: Xử lý đăng nhập bằng Google
        }

        binding.createAccountTextView.setOnClickListener {
            Toast.makeText(this, "Tạo tài khoản Clicked", Toast.LENGTH_SHORT).show()
            // Sau này: Chuyển đến màn hình đăng ký
        }

        binding.continueWithoutAccountButton.setOnClickListener {
            Toast.makeText(this, "Tiếp tục không tài khoản Clicked", Toast.LENGTH_SHORT).show()

            // Lưu trạng thái "không phải lần đầu mở app" và "offline mode"
            val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit()
                .putBoolean("isFirstLaunch", false) // Đánh dấu không còn là lần đầu
                .putBoolean("useOfflineMode", true)  // Lưu trạng thái offline mode
                .apply()

            startActivity(Intent(this, TaskListActivity::class.java)) // Chuyển đến màn hình chính
            finish()
        }
    }
}