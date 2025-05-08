package com.gawasu.sillyn.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.gawasu.sillyn.databinding.FragmentIntroBinding

class IntroFragment : Fragment() {

    private var _binding: FragmentIntroBinding? = null
    private val binding get() = _binding!!

    // Factory method để tạo Fragment (hữu ích nếu bạn có nhiều loại IntroFragment)
    // companion object {
    //     fun newInstance(): IntroFragment {
    //         val fragment = IntroFragment()
    //         // Thêm Bundle arguments nếu cần truyền dữ liệu cho Fragment
    //         return fragment
    //     }
    // }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIntroBinding.inflate(inflater, container, false)
        val view = binding.root
        // Ở đây, bạn có thể tùy chỉnh nội dung của Fragment nếu có nhiều màn giới thiệu
        // Ví dụ: dựa vào arguments để load nội dung khác nhau
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}