package com.gawasu.sillyn.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application):ViewModel() {
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _navigateToLogin = MutableStateFlow(false)
    val navigateToLogin: StateFlow<Boolean> = _navigateToLogin.asStateFlow()

    private val context = application.applicationContext

    init {
        viewModelScope.launch {
            Log.d( null, "delayed")
            delay(3000)
            val isFirstTime = isFirstTimeAppLaunch()
            val hasInternet = hasInternetConnection()
            if (isFirstTime) {
                if (hasInternet) {
                    _navigateToLogin.value = true
                } else {
                    _navigateToLogin.value = false
                }
            } else {
                _navigateToLogin.value = false
            }
            _isLoading.value = false
        }
    }
    private fun isFirstTimeAppLaunch(): Boolean {
        val preferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isFirstLaunch = preferences.getBoolean("is_first_launch", true)
        if (isFirstLaunch) {
            preferences.edit().putBoolean("is_first_launch", false).apply() // Đánh dấu là không còn lần đầu
        }
        return isFirstLaunch
    }

    private fun hasInternetConnection(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
}