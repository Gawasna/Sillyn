package com.gawasu.sillyn.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // Sử dụng activityViewModels để share ViewModel với Activity
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.FragmentEmailSignupBinding // Import binding mới
import com.gawasu.sillyn.ui.viewmodel.AuthViewModel
import com.gawasu.sillyn.utils.FirebaseResult
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EmailSignupFragment : Fragment() {
    private var _binding: FragmentEmailSignupBinding? = null // Binding mới
    private val binding get() = _binding!!
    // Sử dụng activityViewModels nếu AuthViewModel được share scope với Activity
    private val authViewModel: AuthViewModel by activityViewModels()

    companion object {
        private const val TAG = "EmailSignupFragment" // TAG mới
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmailSignupBinding.inflate(inflater, container, false) // Inflate layout mới
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnSignup.setOnClickListener {
            performSignup()
        }
        binding.btnBack.setOnClickListener {
            // Quay lại màn hình trước (AuthenticationOptionsFragment hoặc EmailLoginFragment nếu đến từ đó)
            parentFragmentManager.popBackStack()
        }
        binding.tvGoToLogin.setOnClickListener {
            // Quay lại Fragment đăng nhập (hoặc pop stack hoàn toàn về options rồi push login)
            // Cách đơn giản nhất là pop back stack 2 lần nếu bạn muốn quay về options
            // hoặc chỉ pop 1 lần nếu bạn muốn quay về màn hình login
            parentFragmentManager.popBackStack() // Pop về màn hình gọi nó (có thể là AuthenticationOptionsFragment)
            // Option 2: Pop về AuthOptions rồi push Login (phức tạp hơn, popStack() 2 lần hoặc dùng name)
            // val firstFragment = parentFragmentManager.getBackStackEntryAt(0) // Assuming AuthOptions is the first entry
            // parentFragmentManager.popBackStack(firstFragment.id, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            // showEmailLoginFragment() // Cần một hàm để push EmailLoginFragment, hoặc đơn giản là popBackStack()
        }
    }

    private fun observeViewModel() {
        // Chỉ quan sát signUpResult ở đây
        authViewModel.signUpResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is FirebaseResult.Loading -> {
                    Log.d(TAG, "Signup Loading...")
                    // TODO: Hiển thị loading indicator
                }
                is FirebaseResult.Success -> {
                    Log.d(TAG, "Signup Success, returning to AuthenticationOptionsFragment")
                    Toast.makeText(requireContext(), getString(R.string.signup_successful_login_please), Toast.LENGTH_LONG).show()
                    // Sau khi đăng ký thành công, quay lại màn hình lựa chọn hoặc màn hình đăng nhập
                    parentFragmentManager.popBackStack() // Quay lại màn hình trước đó
                    // TODO: Ẩn loading indicator nếu có.
                }
                is FirebaseResult.Error -> {
                    Log.e(TAG, "Signup Failed", result.exception)
                    val errorMessage = result.exception.localizedMessage ?: getString(R.string.authentication_error, "Unknown error")
                    Toast.makeText(requireContext(), getString(R.string.signup_failed, errorMessage), Toast.LENGTH_LONG).show()
                    // TODO: Ẩn loading indicator nếu có.
                }
            }
        }
        // Không quan sát loginResult ở đây nữa
    }


    private fun performSignup() {
        val email = binding.etEmail.text.toString()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString() // Lấy confirm password

        if (validateInput(email, password, confirmPassword)) { // Cập nhật hàm validate
            authViewModel.signUpWithEmailPassword(email, password)
        }
    }

    // Hàm validateInput mới cho Signup (có thêm xác nhận mật khẩu)
    private fun validateInput(email: String, password: String, confirmPassword: String): Boolean {
        var isValid = true

        // Validate Email
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.invalid_email)
            isValid = false
        } else {
            binding.tilEmail.error = null
        }

        // Validate Password
        if (password.isEmpty() || password.length < 6) {
            binding.tilPassword.error = getString(R.string.invalid_password)
            isValid = false
        } else {
            binding.tilPassword.error = null
        }

        // Validate Confirm Password
        if (confirmPassword.isEmpty() || confirmPassword.length < 6) { // Có thể cần check độ dài tối thiểu giống password
            binding.tilConfirmPassword.error = getString(R.string.invalid_password) // Hoặc string khác
            isValid = false
        } else if (password != confirmPassword) {
            binding.tilConfirmPassword.error = getString(R.string.passwords_do_not_match) // String mới
            isValid = false
        }
        else {
            binding.tilConfirmPassword.error = null
        }


        return isValid
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}