package com.gawasu.sillyn.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SplashViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    // StateFlow để cập nhật ProgressBar & điều hướng UI
    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _navigateToLogin = MutableStateFlow<Boolean?>(null)  // `null` khi chưa quyết định
    val navigateToLogin: StateFlow<Boolean?> = _navigateToLogin.asStateFlow()

    init {
        viewModelScope.launch {
            Log.d("SplashViewModel", "Start Splash Screen")

            // **Giai đoạn 1: Loading UI**
            for (p in 0..50 step 2) {
                _progress.value = p
                delay(30)
            }
            Log.d("SplashViewModel", "UI Loaded")

            // **Giai đoạn 2: Kiểm tra lần đầu mở app**
            val isFirstTime = isFirstTimeAppLaunch()
            for (p in 51..70 step 2) {
                _progress.value = p
                delay(50)
            }
            Log.d("SplashViewModel", "First Time Launch: $isFirstTime")

            // **Giai đoạn 3: Kiểm tra kết nối Internet**
            val hasInternet = hasInternetConnection()
            for (p in 71..100 step 2) {
                _progress.value = p
                delay(50)
            }
            Log.d("SplashViewModel", "Internet Connected: $hasInternet")

            // **Xác định điều hướng**
            _navigateToLogin.value = isFirstTime && hasInternet
        }
    }

    private fun isFirstTimeAppLaunch(): Boolean {
        val preferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isFirstLaunch = preferences.getBoolean("is_first_launch", true)
        if (isFirstLaunch) {
            preferences.edit().putBoolean("is_first_launch", false).apply() // Đánh dấu không còn lần đầu mở app
        }
        return isFirstLaunch
    }

    private fun hasInternetConnection(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
