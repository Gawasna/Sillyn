package com.gawasu.sillyn.ui.fragment

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.gawasu.sillyn.R // Import R file của bạn
import com.gawasu.sillyn.databinding.FragmentPermissionsBinding
import com.gawasu.sillyn.ui.viewmodel.OnboardingViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class PermissionsFragment : Fragment() {

    private var _binding: FragmentPermissionsBinding? = null
    private val binding get() = _binding!!

    // Lấy ViewModel từ Activity để chia sẻ trạng thái
    private val onboardingViewModel: OnboardingViewModel by activityViewModels()

    // Define the permissions required
    private val requiredPermissions = mutableListOf<String>().apply {
        // Add storage permission (if still needed based on your API level and usage)
        // Note: For API 30+, READ_EXTERNAL_STORAGE requires specific access, check documentation.
        // If targeting 30+, you might need MANAGE_EXTERNAL_STORAGE or use Scoped Storage APIs.
        // For simplicity here, keeping the original example but be mindful of API differences.
        // add(Manifest.permission.READ_EXTERNAL_STORAGE) // Giữ lại quyền storage nếu cần

        // Add notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Add exact alarm permission for Android 12+ (API 31+) if required
        // Note: This permission is requested differently (Settings screen)
        // We check for it but don't request it directly via ActivityResultContracts.RequestMultiplePermissions
        // add(Manifest.permission.SCHEDULE_EXACT_ALARM) // Không thêm vào danh sách request runtime
        // add(Manifest.permission.USE_EXACT_ALARM) // Không thêm vào danh sách request runtime
    }.toTypedArray() // Convert to Array<String>


    // Activity Result Launcher for multiple permissions
    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsStatus ->
            // Check status of all requested permissions
            val allGranted = permissionsStatus.all { it.value }
            Log.d(TAG, "Runtime permission results: $permissionsStatus. All granted: $allGranted")

            // Check and update UI for all required permissions (runtime + special)
            updatePermissionStatusUI()

            // Update ViewModel state based on ALL required permissions (runtime + special)
            checkAndReportOverallPermissionState()
        }

    // Activity Result Launcher for the special "Exact Alarm" permission (opens settings)
    private val requestExactAlarmPermissionLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // User returned from settings. Check the permission status again.
            Log.d(TAG, "Returned from exact alarm settings.")
            updatePermissionStatusUI()
            checkAndReportOverallPermissionState()
        }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionsBinding.inflate(inflater, container, false)
        val view = binding.root

        binding.btnRequestPermissions.setOnClickListener {
            // Trigger requesting runtime permissions (storage, notifications if applicable)
            // Special permissions like exact alarm are handled separately (user directed to settings)
            requestRuntimePermissions()
        }

        // Set click listener for the info icon next to Alarms permission
        binding.ivPermissionInfoAlarms.setOnClickListener {
            showExactAlarmPermissionSettingsDialog()
        }


        // Initial check and UI update
        updatePermissionStatusUI()
        checkAndReportOverallPermissionState()

        return view
    }

    override fun onResume() {
        super.onResume()
        // Re-check permission states when Fragment resumes (e.g., after returning from Settings)
        updatePermissionStatusUI()
        checkAndReportOverallPermissionState()
    }

    private fun updatePermissionStatusUI() {
        Log.d(TAG, "Updating permission status UI...")

        // --- Update Storage Permission Status (if included in requiredPermissions) ---
        // val isStorageGranted = if (requiredPermissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE)) {
        //     ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        // } else true // Assume granted if not required
        // binding.ivPermissionStatusStorage.setImageResource(getStatusIcon(isStorageGranted))

        // --- Update Notifications Permission Status (API 33+) ---
        val isNotificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true // Granted by default on older APIs
        binding.ivPermissionStatusNotifications.setImageResource(getStatusIcon(isNotificationsGranted))

        // --- Update Exact Alarm Permission Status (API 31+) ---
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val isExactAlarmGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true // Granted by default on older APIs

        binding.ivPermissionStatusAlarms.setImageResource(getStatusIcon(isExactAlarmGranted))
        // Show info icon only if exact alarm permission is needed and not granted
        binding.ivPermissionInfoAlarms.visibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isExactAlarmGranted) View.VISIBLE else View.GONE


        // Update main text and button based on overall status (handled in checkAndReportOverallPermissionState)
    }

    private fun getStatusIcon(isGranted: Boolean): Int {
        return if (isGranted) {
            R.drawable.priority_indicator_low // Thay bằng icon "đã cấp" của bạn
        } else {
            R.drawable.priority_indicator_high // Thay bằng icon "bị từ chối" của bạn
        }
    }

    // Check if ALL required permissions are granted and update ViewModel
    private fun checkAndReportOverallPermissionState() {
        var allRequiredPermissionsGranted = true

        // Check Storage (if applicable)
        // if (requiredPermissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE)) {
        //     if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        //         allRequiredPermissionsGranted = false
        //     }
        // }

        // Check Notifications (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                allRequiredPermissionsGranted = false
            }
        }

        // Check Exact Alarm (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                allRequiredPermissionsGranted = false
            }
        }

        // Report overall status to ViewModel
        onboardingViewModel.onPermissionsGranted(allRequiredPermissionsGranted)
        Log.d(TAG, "Overall permissions granted: $allRequiredPermissionsGranted")

        // Update UI elements based on overall status
        if (allRequiredPermissionsGranted) {
            binding.tvPermissionsTitle.text = "Cảm ơn!"
            binding.tvPermissionsDescription.text = "Tất cả các quyền cần thiết đã được cấp. Bạn có thể tiếp tục."
            binding.btnRequestPermissions.text = "Tiếp tục" // Nút giờ dùng để chuyển trang
            binding.btnRequestPermissions.isEnabled = true // Luôn enabled khi tất cả quyền đã có
            binding.tvPermissionWarning.visibility = View.GONE
        } else {
            binding.tvPermissionsTitle.text = "Chúng tôi cần một số quyền"
            binding.tvPermissionsDescription.text = "Vui lòng cấp các quyền dưới đây để ứng dụng hoạt động tốt nhất."
            binding.btnRequestPermissions.text = "Tiếp tục (Yêu cầu quyền)" // Nút yêu cầu quyền hoặc hướng dẫn
            binding.btnRequestPermissions.isEnabled = true // Cho phép nhấn nút để trigger request
            binding.tvPermissionWarning.visibility = View.VISIBLE // Hiển thị cảnh báo
        }

        // If there are any runtime permissions to request, the button action is to request them.
        // If all runtime permissions are granted but a special one (like Exact Alarm) is missing,
        // the button action might still lead to a dialog explaining the missing permission
        // or guiding the user. For simplicity, the button always triggers the request flow
        // but its text indicates its primary purpose at this stage.
    }


    private fun requestRuntimePermissions() {
        // Filter out permissions that are already granted and special permissions (like exact alarm)
        // that require a different handling flow (opening settings).
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
                    && it != Manifest.permission.SCHEDULE_EXACT_ALARM // Exclude special permissions
                    && it != Manifest.permission.USE_EXACT_ALARM // Exclude special permissions
            // Add other special permissions here if applicable
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting runtime permissions: ${permissionsToRequest.joinToString()}")
            requestMultiplePermissionsLauncher.launch(permissionsToRequest)
        } else {
            Log.d(TAG, "No runtime permissions to request. Checking special permissions.")
            // If no runtime permissions needed, but overall permission check failed,
            // it must be due to a special permission (like exact alarm).
            // Guide the user or just update UI. The overall status check
            // will handle enabling the "Continue" button only when ALL are met.
            checkAndReportOverallPermissionState() // Re-run check to update UI
            // You could optionally show a dialog here if specific special permissions are missing.
        }
    }

    // Dialog giải thích lý do cần các quyền runtime
    // Có thể tùy chỉnh để giải thích từng quyền cụ thể
    private fun showRationaleDialog(permissions: List<String>) {
        val message = "Ứng dụng cần các quyền sau đây để hoạt động:\n\n" +
                permissions.joinToString("\n") { permissionName ->
                    when(permissionName) {
                        Manifest.permission.READ_EXTERNAL_STORAGE -> "- Truy cập Bộ nhớ: Để lưu trữ dữ liệu ngoại tuyến."
                        Manifest.permission.POST_NOTIFICATIONS -> "- Hiển thị Thông báo: Để gửi nhắc nhở nhiệm vụ."
                        // Add other permission explanations
                        else -> "- Quyền không xác định: Vui lòng cấp quyền."
                    }
                } + "\nVui lòng cấp quyền khi được hỏi."

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cần quyền ứng dụng")
            .setMessage(message)
            .setPositiveButton("Đồng ý") { dialog, _ ->
                // Trigger the actual permission request again after rationale
                requestMultiplePermissionsLauncher.launch(permissions.toTypedArray())
                dialog.dismiss()
            }
            .setNegativeButton("Hủy") { dialog, _ ->
                dialog.dismiss()
                // User denied rationale. Update UI and status.
                updatePermissionStatusUI()
                checkAndReportOverallPermissionState()
            }
            .show()
    }

    // Dialog hướng dẫn cấp quyền Exact Alarm trong Settings
    private fun showExactAlarmPermissionSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cần quyền \"Đặt báo thức và nhắc nhở\"")
            .setMessage("Để nhắc nhở nhiệm vụ đúng giờ, vui lòng cấp quyền \"Đặt báo thức và nhắc nhở\" cho ứng dụng trong cài đặt.")
            .setPositiveButton("Mở cài đặt") { dialog, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        // Intent để mở màn hình cấp quyền Exact Alarm cho app của bạn
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        // Tùy chọn: Set data URI để mở thẳng đến app của bạn (API 31+)
                        intent.setData(Uri.fromParts("package", requireContext().getPackageName(), null));

                        requestExactAlarmPermissionLauncher.launch(intent) // Sử dụng launcher
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not open exact alarm settings", e)
                        // Fallback: Open general app settings if the specific intent fails
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", requireContext().getPackageName(), null)
                        intent.data = uri
                        requestExactAlarmPermissionLauncher.launch(intent)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Hủy") { dialog, _ ->
                dialog.dismiss()
                // User declined to go to settings. Update UI and status.
                updatePermissionStatusUI()
                checkAndReportOverallPermissionState()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "PermissionsFragment"
        // Placeholder permissions, actual list is in requiredPermissions mutable list
        // private const val PERMISSION_TO_REQUEST = Manifest.permission.READ_EXTERNAL_STORAGE // No longer needed as a single const
    }
}