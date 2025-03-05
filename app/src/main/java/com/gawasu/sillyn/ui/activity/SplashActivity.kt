package com.gawasu.sillyn.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gawasu.sillyn.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        CoroutineScope(Dispatchers.Main).launch {
            delay(2000)

            val isFirstLaunch = isFirstTimeAppLaunch() // Kiểm tra lần đầu mở app
            val isLoggedInOrOffline = checkLoginState() // Kiểm tra trạng thái đăng nhập/offline

            val nextActivityIntent = when {
                isFirstLaunch -> {
                    Intent(this@SplashActivity, LoginActivity::class.java) // Lần đầu, vào LoginActivity
                }
                isLoggedInOrOffline -> {
                    Intent(this@SplashActivity, TaskListActivity::class.java) // Đã login/offline, vào TaskListActivity (màn hình chính giả định)
                }
                else -> {
                    Intent(this@SplashActivity, LoginActivity::class.java) // Chưa login/offline và không phải lần đầu, vào LoginActivity
                }
            }

            startActivity(nextActivityIntent)
            finish()
        }
    }

    private fun isFirstTimeAppLaunch(): Boolean {
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean("isFirstLaunch", true) // Mặc định là true (lần đầu)
    }


    private fun checkLoginState(): Boolean {
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean("isLoggedIn", false) || sharedPrefs.getBoolean("useOfflineMode", false)
    }
}