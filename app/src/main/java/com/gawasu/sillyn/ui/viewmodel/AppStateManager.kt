package com.gawasu.sillyn.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.gawasu.sillyn.data.AppState
import com.gawasu.sillyn.data.AppStateDetails
import com.gawasu.sillyn.data.AuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class AppStateManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // State streams
    private val _appStateDetails = MutableLiveData<AppStateDetails>()
    val appStateDetails: LiveData<AppStateDetails> get() = _appStateDetails

    private val _currentState = MutableLiveData<AppState>()
    val currentState: LiveData<AppState> get() = _currentState

    init {
        // Load initial state from preferences and auth
        loadInitialState()

        // Set up auth state listener
        auth.addAuthStateListener { firebaseAuth ->
            updateAuthState(firebaseAuth.currentUser)
        }
    }

    // Load initial state from preferences and current auth state
    private fun loadInitialState() {
        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        val hasCompletedOnboarding = prefs.getBoolean(KEY_COMPLETED_ONBOARDING, false)
        val isOfflineModeEnabled = prefs.getBoolean(KEY_OFFLINE_MODE, false)
        val isOnline = hasInternetConnection()

        // Get current user
        val currentUser = auth.currentUser
        val stateDetails = if (currentUser != null) {
            // Determine auth provider based on provider data
            val authProvider = determineAuthProvider(currentUser)

            AppStateDetails(
                isFirstLaunch = isFirstLaunch,
                hasCompletedOnboarding = hasCompletedOnboarding,
                isLoggedIn = true,
                currentUserEmail = currentUser.email,
                currentUserId = currentUser.uid,
                authProvider = authProvider,
                isOnline = isOnline,
                isOfflineModeEnabled = isOfflineModeEnabled
            )
        } else {
            AppStateDetails(
                isFirstLaunch = isFirstLaunch,
                hasCompletedOnboarding = hasCompletedOnboarding,
                isLoggedIn = false,
                isOnline = isOnline,
                isOfflineModeEnabled = isOfflineModeEnabled
            )
        }

        _appStateDetails.postValue(stateDetails)

        // Now determine the overall app state
        val state = determineAppState(stateDetails)
        _currentState.postValue(state)
    }

    // Update app state when auth state changes
    private fun updateAuthState(user: FirebaseUser?) {
        val currentDetails = _appStateDetails.value ?: return

        val newDetails = if (user != null) {
            val authProvider = determineAuthProvider(user)
            currentDetails.copy(
                isLoggedIn = true,
                currentUserEmail = user.email,
                currentUserId = user.uid,
                authProvider = authProvider
            )
        } else {
            currentDetails.copy(
                isLoggedIn = false,
                currentUserEmail = null,
                currentUserId = null,
                authProvider = AuthProvider.NONE
            )
        }

        _appStateDetails.postValue(newDetails)

        // Update login state in preferences
        prefs.edit().apply {
            putBoolean(KEY_LOGGED_IN, newDetails.isLoggedIn)
            if (newDetails.isLoggedIn) {
                putString(KEY_USER_EMAIL, newDetails.currentUserEmail)
                putString(KEY_USER_ID, newDetails.currentUserId)
                putString(KEY_AUTH_PROVIDER, newDetails.authProvider.name)
            } else {
                remove(KEY_USER_EMAIL)
                remove(KEY_USER_ID)
                remove(KEY_AUTH_PROVIDER)
            }
            apply()
        }

        // Update the overall app state
        val newState = determineAppState(newDetails)
        _currentState.postValue(newState)
    }

    // Determine the current app state based on detailed state
    fun determineAppState(details: AppStateDetails = _appStateDetails.value ?: AppStateDetails()): AppState {
        return when {
            details.isFirstLaunch -> AppState.FirstLaunch
            !details.isFirstLaunch && !details.hasCompletedOnboarding -> AppState.OnboardingComplete
            details.isLoggedIn && details.isOnline -> AppState.LoggedIn
            details.isLoggedIn && !details.isOnline -> AppState.LoggedInOffline
            !details.isLoggedIn -> AppState.NoAccount
            else -> AppState.NoAccount // Default fallback
        }
    }

    // Check if internet is available and update state if changed
    fun updateConnectivityState() {
        val currentDetails = _appStateDetails.value ?: return
        val isOnline = hasInternetConnection()

        if (currentDetails.isOnline != isOnline) {
            val newDetails = currentDetails.copy(isOnline = isOnline)
            _appStateDetails.postValue(newDetails)

            // Update the overall app state
            val newState = determineAppState(newDetails)
            _currentState.postValue(newState)
        }
    }

    // Mark first launch as completed
    fun markFirstLaunchCompleted() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()

        val currentDetails = _appStateDetails.value ?: return
        val newDetails = currentDetails.copy(isFirstLaunch = false)
        _appStateDetails.postValue(newDetails)

        // Update the overall app state
        val newState = determineAppState(newDetails)
        _currentState.postValue(newState)
    }

    // Mark onboarding completed
    fun markOnboardingCompleted() {
        prefs.edit().putBoolean(KEY_COMPLETED_ONBOARDING, true).apply()

        val currentDetails = _appStateDetails.value ?: return
        val newDetails = currentDetails.copy(hasCompletedOnboarding = true)
        _appStateDetails.postValue(newDetails)

        // Update the overall app state
        val newState = determineAppState(newDetails)
        _currentState.postValue(newState)
    }

    // Toggle offline mode
    fun setOfflineMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_MODE, enabled).apply()

        val currentDetails = _appStateDetails.value ?: return
        val newDetails = currentDetails.copy(isOfflineModeEnabled = enabled)
        _appStateDetails.postValue(newDetails)
    }

    // Check if user is logged in
    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    // Check if first launch
    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    // Check if onboarding is completed
    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_COMPLETED_ONBOARDING, false)
    }

    // Sign out user
    fun signOut() {
        auth.signOut()
        // Auth state listener will update the state
    }

    // Helper method to determine auth provider from FirebaseUser
    private fun determineAuthProvider(user: FirebaseUser): AuthProvider {
        return when {
            user.isAnonymous -> AuthProvider.ANONYMOUS
            user.providerData.any { it.providerId == "google.com" } -> AuthProvider.GOOGLE
            user.providerData.any { it.providerId == "password" } -> AuthProvider.EMAIL
            else -> AuthProvider.NONE
        }
    }

    // Helper to check internet connectivity
    private fun hasInternetConnection(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    companion object {
        private const val TAG = "AppStateManager"
        private const val PREFS_NAME = "app_state_prefs"

        // Preference keys
        private const val KEY_FIRST_LAUNCH = "is_first_launch"
        private const val KEY_COMPLETED_ONBOARDING = "has_completed_onboarding"
        private const val KEY_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_AUTH_PROVIDER = "auth_provider"
        private const val KEY_OFFLINE_MODE = "offline_mode_enabled"

        @Volatile private var instance: AppStateManager? = null

        fun getInstance(context: Context): AppStateManager {
            return instance ?: synchronized(this) {
                instance ?: AppStateManager(context.applicationContext).also { instance = it }
            }
        }
    }
}