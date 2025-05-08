package com.gawasu.sillyn.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.gawasu.sillyn.ui.fragment.IntroFragment
import com.gawasu.sillyn.ui.fragment.PermissionsFragment

class OnboardingPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    // Danh sách các Fragment trong Onboarding flow
    private val fragments: List<Fragment> = listOf(
        IntroFragment(), // Màn hình giới thiệu 1
        // IntroFragment(), // Thêm màn hình giới thiệu khác nếu có
        PermissionsFragment() // Màn hình yêu cầu quyền
    )

    override fun getItemCount(): Int {
        return fragments.size
    }

    override fun createFragment(position: Int): Fragment {
        return fragments[position]
    }
}