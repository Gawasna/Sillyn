package com.gawasu.sillyn.application

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

@HiltAndroidApp
class SillynApplication : Application() {

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
        Log.i(TAG, "Firestore cache size: $CACHE_SIZE_LIMIT bytes")
    }

    companion object {
        private const val TAG = "SILLYN APPLICATION"
    }
}
