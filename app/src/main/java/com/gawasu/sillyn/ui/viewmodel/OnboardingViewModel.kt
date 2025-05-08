package com.gawasu.sillyn.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OnboardingViewModel(private val appStateManager: AppStateManager) : ViewModel() {

    private val totalSteps = 2 // Ví dụ: 1 Intro + 1 Permissions
    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _onboardingComplete = MutableStateFlow(false)
    val onboardingComplete: StateFlow<Boolean> = _onboardingComplete.asStateFlow()

    // State cho Permissions Fragment
    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    init {
        Log.d(TAG, "OnboardingViewModel created")
    }

    fun nextStep() {
        if (_onboardingComplete.value) {
            Log.w(TAG, "Attempted to go next after completion")
            return // Already finished
        }

        val next = _currentStep.value + 1
        if (next < totalSteps) {
            _currentStep.value = next
            Log.d(TAG, "Moved to step: $next")
        } else {
            // This is the last step, mark onboarding as complete
            Log.d(TAG, "Reached last step. Marking onboarding complete.")
            markOnboardingComplete()
        }
    }

    fun skipOnboarding() {
        Log.d(TAG, "Skipping onboarding.")
        markOnboardingComplete()
    }

    private fun markOnboardingComplete() {
        if (!_onboardingComplete.value) {
            appStateManager.markOnboardingCompleted()
            _onboardingComplete.value = true
            Log.d(TAG, "Onboarding marked as complete in AppStateManager.")
        }
    }

    // --- Logic cho PermissionsFragment ---
    fun onPermissionsGranted(granted: Boolean) {
        _permissionsGranted.value = granted
        Log.d(TAG, "Permissions granted state updated: $granted")
        // Tùy logic: tự động sang bước tiếp theo nếu granted,
        // hoặc yêu cầu người dùng nhấn Next sau khi granted
        // Ở đây ta sẽ yêu cầu người dùng nhấn Next.
    }

    // Factory cho ViewModel
    class Factory(private val appStateManager: AppStateManager) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return OnboardingViewModel(appStateManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "OnboardingViewModel"
    }
}