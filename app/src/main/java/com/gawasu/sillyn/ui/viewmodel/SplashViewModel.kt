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
    // StateFlow to update ProgressBar
    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    // Navigation destination
    private val _navigationTarget = MutableStateFlow<NavigationTarget?>(null)
    val navigationTarget: StateFlow<NavigationTarget?> = _navigationTarget.asStateFlow()

    init {
        viewModelScope.launch {
            Log.d(TAG, "Starting splash sequence")

            // Phase 1: Animate progress while loading app state
            animateProgress(0, 70, 1000)

            // Phase 2: Determine where to navigate based on app state
            val currentState = appStateManager.currentState.value
            Log.d(TAG, "Current app state: $currentState")

            // Complete the progress animation
            animateProgress(70, 100, 300)

            // Determine navigation target based on app state
            _navigationTarget.value = when (currentState) {
                AppState.FirstLaunch -> NavigationTarget.LOGIN
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
            delay(stepTime)
        }
    }

    // Navigation destinations
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