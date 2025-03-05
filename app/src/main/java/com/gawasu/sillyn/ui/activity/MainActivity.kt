package com.gawasu.sillyn.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gawasu.sillyn.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Giữ layout hiện tại của MainActivity, có thể là empty

        // Tạm thời chuyển hướng đến LoginActivity
        startActivity(Intent(this, LoginActivity::class.java))
        finish() // Đóng MainActivity sau khi chuyển hướng
    }
}