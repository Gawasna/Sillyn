package com.gawasu.sillyn.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gawasu.sillyn.data.AppState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min

class SplashViewModel(
    application: Application,
    private val appStateManager: AppStateManager
) : AndroidViewModel(application) {
    // Progress
    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    // Text status
    private val _statusText = MutableStateFlow("Đang khởi tạo ứng dụng")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    // Navigation
    private val _navigationTarget = MutableStateFlow<NavigationTarget?>(null)
    val navigationTarget: StateFlow<NavigationTarget?> = _navigationTarget.asStateFlow()

    private var dotCount = 0 // For dot animation

    init {
        viewModelScope.launch {
            Log.d(TAG, "Starting splash sequence")

            // Launch dot animation separately
            launch {
                animateDots()
            }

            // Phase 1: Animate progress while loading app state
            animateProgress(0, 70, 700)

            // Phase 2: Determine where to navigate
            val currentState = appStateManager.currentState.value
            Log.d(TAG, "Current app state: $currentState")

            if (currentState == AppState.FirstLaunch) {
                // Mark first launch completed immediately after determining it's the first launch.
                // This ensures next launch won't be marked as first launch again.
                appStateManager.markFirstLaunchCompleted()
                Log.d(TAG, "Marked first launch as completed.")
                // Note: appStateManager.markFirstLaunchCompleted() updates its internal state,
                // but currentState variable here holds the value *before* the update.
                // The navigation decision below is correct based on the *initial* determination.
            }

            // Complete the progress animation
            animateProgress(70, 100, 100)

            // Set navigation
            _navigationTarget.value = when (currentState) {
                AppState.FirstLaunch -> NavigationTarget.ONBOARDING
                AppState.OnboardingComplete -> NavigationTarget.LOGIN
                AppState.LoggedIn -> NavigationTarget.MAIN
                AppState.LoggedInOffline -> NavigationTarget.MAIN
                AppState.NoAccount -> NavigationTarget.LOGIN
                null -> NavigationTarget.LOGIN
            }

            Log.d(TAG, "Navigation target: ${_navigationTarget.value}")
        }
    }

    private suspend fun animateProgress(start: Int, end: Int, duration: Long) {
        val steps = end - start
        val stepTime = duration / steps.coerceAtLeast(1)

        for (i in 0..steps) {
            val progressValue = start + i
            _progress.value = min(progressValue, 100)

            // Update text based on progress
            updateStatusText(progressValue)

            delay(stepTime)
        }
    }

    private fun updateStatusText(progress: Int) {
        val baseText = when {
            progress < 30 -> "Đang khởi tạo ứng dụng"
            progress < 60 -> "Đang kết nối tới server"
            progress < 90 -> "Đang tải dữ liệu"
            else -> "Hoàn tất chuẩn bị"
        }
        val dots = ".".repeat(dotCount)
        _statusText.value = baseText + dots
    }

    private suspend fun animateDots() {
        while (true) {
            dotCount = (dotCount + 1) % 4 // 0 -> 1 -> 2 -> 3 -> 0
            updateStatusText(_progress.value)
            delay(75) // cập nhật mỗi nửa giây
        }
    }

    enum class NavigationTarget {
        ONBOARDING,
        LOGIN,
        MAIN
    }

    companion object {
        private const val TAG = "SplashViewModel"
    }
}

class SplashViewModelFactory(
    private val application: Application,
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
