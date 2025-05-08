package com.gawasu.sillyn.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.gawasu.sillyn.worker.RescheduleRemindersWorker // Import your Worker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint // Use Hilt if you need to inject anything, though not strictly necessary just for enqueuing work
class BootReceiver : BroadcastReceiver() {

    // @Inject lateinit var someDependency: SomeDependency // Inject if needed

    private val TAG = "BootReceiver"
    private val RESCHEDULE_WORK_TAG = "reschedule_reminders_work"

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "BootReceiver onReceive. Action: ${intent?.action}")

        // Check if the relevant actions occurred
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            // Important: Although this Receiver is @AndroidEntryPoint,
            // we don't need to use goAsync() here because enqueuing work is fast.
            // The actual heavy lifting is done by the Worker.

            // Define constraints for the work (optional but recommended)
            val constraints = Constraints.Builder()
                // .setRequiredNetworkType(NetworkType.CONNECTED) // Require network if fetching from Firestore
                // .setRequiresBatteryNotLow(true) // Don't run if battery is low
                .build()

            // Create a Work Request for the RescheduleRemindersWorker
            val rescheduleWorkRequest = OneTimeWorkRequestBuilder<RescheduleRemindersWorker>()
                .addTag(RESCHEDULE_WORK_TAG) // Tag for easy identification/cancellation
                // .setConstraints(constraints) // Apply constraints
                // .setInitialDelay(...) // Optional: Add a delay
                .build()

            // Enqueue the work request. Use KEEP if work is already enqueued.
            WorkManager.getInstance(context).enqueueUniqueWork(
                RESCHEDULE_WORK_TAG, // Unique name for the work
                ExistingWorkPolicy.KEEP, // If work with this name exists, keep the old one
                rescheduleWorkRequest
            )

            Log.d(TAG, "RescheduleRemindersWorker enqueued.")
        }
    }
}