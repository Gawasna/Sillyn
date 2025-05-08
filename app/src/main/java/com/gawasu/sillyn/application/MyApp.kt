package com.gawasu.sillyn.application

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import javax.inject.Inject

@HiltAndroidApp
class SillynApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        Log.d(TAG, "Khởi tạo ứng dụng...")
        val CACHE_SIZE_LIMIT = 100L * 1024 * 1024
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(CACHE_SIZE_LIMIT)
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings
        Log.i(TAG, "Firestore persistence enabled: ${settings.isPersistenceEnabled}")
    }

    // *** FIX: Thay thế fun getWorkManagerConfiguration() bằng val workManagerConfiguration ***
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory) // Use the injected HiltWorkerFactory
            // .setMinimumLoggingLevel(android.util.Log.DEBUG) // Optional: Set log level for WorkManager
            .build()


    companion object {
        private const val TAG = "SILLYN APPLICATION"
    }
}