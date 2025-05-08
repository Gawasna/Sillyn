package com.gawasu.sillyn.ui.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.gawasu.sillyn.databinding.ActivityOnboardingBinding
import com.gawasu.sillyn.ui.adapter.OnboardingPagerAdapter
import com.gawasu.sillyn.ui.viewmodel.AppStateManager
import com.gawasu.sillyn.ui.viewmodel.OnboardingViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var viewPager: ViewPager2
    private lateinit var onboardingAdapter: OnboardingPagerAdapter

    private val appStateManager: AppStateManager by lazy { AppStateManager.getInstance(applicationContext) }

    private val onboardingViewModel: OnboardingViewModel by viewModels {
        OnboardingViewModel.Factory(appStateManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewPager = binding.viewPager
        onboardingAdapter = OnboardingPagerAdapter(this)
        viewPager.adapter = onboardingAdapter

        // Tắt vuốt ngang nếu bạn chỉ muốn điều hướng bằng nút
        // viewPager.isUserInputEnabled = false

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        // Lắng nghe sự kiện chuyển trang của ViewPager
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateButtonUI(position)
            }
        })

        // Lắng nghe sự kiện click nút "Tiếp tục" hoặc "Hoàn thành"
        binding.btnNextOrFinish.setOnClickListener {
            onboardingViewModel.nextStep()
        }

        // Lắng nghe sự kiện click nút "Bỏ qua"
        binding.btnSkip.setOnClickListener {
            onboardingViewModel.skipOnboarding()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Quan sát bước hiện tại từ ViewModel để cập nhật ViewPager
                launch {
                    onboardingViewModel.currentStep.collectLatest { step ->
                        viewPager.setCurrentItem(step, true) // Chuyển trang ViewPager có animation
                        updateButtonUI(step)
                    }
                }

                // Quan sát trạng thái hoàn thành Onboarding
                launch {
                    onboardingViewModel.onboardingComplete.collectLatest { isComplete ->
                        if (isComplete) {
                            Log.d(TAG, "Onboarding complete observed. Navigating to Authentication.")
                            navigateToAuthentication()
                        }
                    }
                }

                // Quan sát trạng thái quyền (để có thể thay đổi trạng thái nút Next/Finish)
                launch {
                    onboardingViewModel.permissionsGranted.collectLatest { isGranted ->
                        // Ví dụ: Nếu đang ở trang Permissions và quyền chưa được cấp,
                        // bạn có thể vô hiệu hóa nút "Tiếp tục"
                        if (viewPager.currentItem == onboardingAdapter.itemCount - 1) { // Đang ở trang cuối (Permissions)
                            binding.btnNextOrFinish.isEnabled = isGranted // Chỉ cho phép "Tiếp tục" nếu quyền đã được cấp
                        } else {
                            binding.btnNextOrFinish.isEnabled = true // Mặc định cho phép ở các trang khác
                        }
                    }
                }
            }
        }
    }

    private fun updateButtonUI(currentPosition: Int) {
        val totalSteps = onboardingAdapter.itemCount
        Log.d(TAG, "Updating UI for step $currentPosition/$totalSteps")

        if (currentPosition == totalSteps - 1) {
            // Là trang cuối cùng
            binding.btnNextOrFinish.text = "Hoàn thành"
            binding.btnSkip.visibility = View.GONE // Ẩn nút Bỏ qua ở trang cuối
            // Cập nhật trạng thái enable của nút Hoàn thành dựa trên trạng thái quyền
            binding.btnNextOrFinish.isEnabled = onboardingViewModel.permissionsGranted.value
        } else {
            // Không phải trang cuối cùng
            binding.btnNextOrFinish.text = "Tiếp tục"
            binding.btnSkip.visibility = View.VISIBLE // Hiển thị nút Bỏ qua
            binding.btnNextOrFinish.isEnabled = true // Luôn cho phép tiếp tục ở các trang intro
        }
    }

    private fun navigateToAuthentication() {
        val intent = Intent(this, AuthenticationActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish() // Đóng OnboardingActivity
    }

    companion object {
        private const val TAG = "OnboardingActivity"
    }
}