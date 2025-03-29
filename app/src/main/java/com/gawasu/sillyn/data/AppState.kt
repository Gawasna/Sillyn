package com.gawasu.sillyn.data

// User Authentication States
enum class AppState {
    FirstLaunch,      // App is launched for the first time
    OnboardingComplete, // Onboarding has been viewed but user not logged in
    LoggedIn,         // User is logged in and online
    LoggedInOffline,  // User is logged in but offline
    NoAccount         // User has no account or isn't logged in
}

// Keeping the data class for detailed state info
data class AppStateDetails(
    val isFirstLaunch: Boolean = true,
    val hasCompletedOnboarding: Boolean = false,

    val isLoggedIn: Boolean = false,
    val currentUserEmail: String? = null,
    val currentUserId: String? = null,
    val authProvider: AuthProvider = AuthProvider.NONE,

    val isOnline: Boolean = true,
    val isOfflineModeEnabled: Boolean = false
)

enum class AuthProvider {
    NONE,
    EMAIL,
    GOOGLE,
    ANONYMOUS
}