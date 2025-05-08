package com.gawasu.sillyn.ui.fragment

import android.content.ComponentName // Import ComponentName
import android.content.Intent // Import Intent
import android.os.Bundle
import android.provider.Settings // Import Settings
import android.util.Log
import android.widget.Toast
import androidx.preference.Preference // Import Preference
import androidx.preference.PreferenceFragmentCompat
import com.gawasu.sillyn.R
import dagger.hilt.android.AndroidEntryPoint // Nếu bạn dùng Hilt

@AndroidEntryPoint // Nếu bạn dùng Hilt
class SettingsFragment : PreferenceFragmentCompat() {

    companion object {
        private const val TAG = "SettingsFragment"
        private const val PREF_KEY_NOTIFICATION_ACCESS = "grant_notification_access" // Key of the permission preference
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        // Get the permission preference item
        val notificationAccessPreference: Preference? = findPreference(PREF_KEY_NOTIFICATION_ACCESS)

        // Set click listener for the permission preference
        notificationAccessPreference?.setOnPreferenceClickListener {
            // Open the Notification Access settings screen
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                // Optional: add flags to ensure it opens correctly
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                // Handle cases where the intent cannot be handled (e.g., old Android versions)
                Log.e(TAG, "Could not open notification listener settings", e)
                Toast.makeText(requireContext(), "Thiết bị của bạn không hỗ trợ tính năng này.", Toast.LENGTH_SHORT).show()
            }
            true // Return true to indicate the click was handled
        }
    }

    // Update the summary of the permission preference when the fragment resumes
    // This shows the current status (Granted/Not Granted)
    override fun onResume() {
        super.onResume()
        updateNotificationAccessSummary()
    }

    private fun updateNotificationAccessSummary() {
        val notificationAccessPreference: Preference? = findPreference(PREF_KEY_NOTIFICATION_ACCESS)
        notificationAccessPreference?.let {
            if (isNotificationListenerGranted()) {
                it.setSummary("Đã cấp quyền (Bấm để quản lý)") // Customize summary for granted state
            } else {
                it.setSummary("Chưa cấp quyền (Bấm để cấp)") // Customize summary for not granted state
            }
        }
    }

    // Helper function to check if Notification Listener permission is granted
    private fun isNotificationListenerGranted(): Boolean {
        val pkgName = requireContext().packageName
        val flat = Settings.Secure.getString(requireContext().contentResolver, "enabled_notification_listeners")
        if (flat != null && flat.contains(pkgName, ignoreCase = true)) {
            val names = flat.split(":").toTypedArray()
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == pkgName) {
                    return true
                }
            }
        }
        return false
    }


    // Optional: Override onPreferenceTreeClick if you prefer handling clicks there
    /*
    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == PREF_KEY_NOTIFICATION_ACCESS) {
             // Logic để mở cài đặt quyền ở đây
             try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not open notification listener settings", e)
                Toast.makeText(requireContext(), "Thiết bị của bạn không hỗ trợ tính năng này.", Toast.LENGTH_SHORT).show()
            }
            return true // Handled
        }
        // Let the super class handle other preferences (Switch, Checkbox, etc.)
        return super.onPreferenceTreeClick(preference)
    }
    */

}