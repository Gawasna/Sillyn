package com.gawasu.sillyn.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gawasu.sillyn.databinding.ActivityTaskListBinding

class TaskListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskListBinding
    //TEST TEST TEST PLEASE DONT FORGET
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hiển thị lời chào (ví dụ: có thể lấy tên người dùng sau này)
        binding.greetingTextView.text = "Chào mừng bạn đến với Sillyn Todolist!"

        setupListeners()
    }

    private fun setupListeners() {
        binding.logoutButton.setOnClickListener {
            // Xử lý "đăng xuất" (quay lại màn hình đăng nhập và reset trạng thái)
            val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit()
                .putBoolean("isLoggedIn", false)   // Đặt logged in về false
                .putBoolean("useOfflineMode", false) // Đặt offline mode về false (nếu muốn)
                .apply()

            startActivity(Intent(this, LoginActivity::class.java)) // Quay lại LoginActivity
            finish() // Đóng TaskListActivity
        }
    }
}