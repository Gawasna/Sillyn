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
import com.gawasu.sillyn.databinding.FragmentEmailLoginBinding // Đảm bảo binding đúng
import com.gawasu.sillyn.ui.fragment.EmailSignupFragment
import com.gawasu.sillyn.ui.viewmodel.AuthViewModel
import com.gawasu.sillyn.utils.FirebaseResult
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EmailLoginFragment : Fragment() {
    private var _binding: FragmentEmailLoginBinding? = null
    private val binding get() = _binding!!
    // Sử dụng activityViewModels nếu AuthViewModel được share scope với Activity
    // Hoặc viewModels() nếu mỗi Fragment có ViewModel riêng (ít phổ biến cho auth)
    private val authViewModel: AuthViewModel by activityViewModels()

    companion object {
        private const val TAG = "EmailLoginFragment" // Cập nhật TAG
    }

    // Không cần newInstance với arg isSignup nữa

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmailLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        observeViewModel()
        // Không cần setupUI vì không còn chế độ signup trong fragment này
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            performLogin()
        }
        binding.btnBack.setOnClickListener {
            // Quay lại màn hình AuthenticationOptionsFragment
            parentFragmentManager.popBackStack()
        }
        binding.tvGoToSignup.setOnClickListener {
            // Chuyển sang Fragment đăng ký
            showEmailSignupFragment()
        }
    }

    private fun observeViewModel() {
        // Chỉ quan sát loginResult ở đây để xử lý lỗi hoặc loading.
        // Việc điều hướng khi thành công sẽ do Activity xử lý.
        authViewModel.loginResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is FirebaseResult.Loading -> {
                    Log.d(TAG, "Login Loading...")
                    // TODO: Hiển thị loading indicator
                }
                is FirebaseResult.Success -> {
                    Log.d(TAG, "Login Success from Fragment. Activity will handle navigation.")
                    // Activity observer sẽ lo phần điều hướng.
                    // TODO: Ẩn loading indicator nếu có.
                }
                is FirebaseResult.Error -> {
                    Log.e(TAG, "Login Failed in Fragment", result.exception)
                    val errorMessage = result.exception.localizedMessage ?: getString(R.string.authentication_error, "Unknown error")
                    Toast.makeText(requireContext(), getString(R.string.login_failed, errorMessage), Toast.LENGTH_LONG).show()
                    // TODO: Ẩn loading indicator nếu có.
                }
            }
        }
        // Không quan sát signUpResult ở đây nữa
    }

    private fun performLogin() {
        val email = binding.etEmail.text.toString()
        val password = binding.etPassword.text.toString()

        if (validateInput(email, password)) {
            authViewModel.loginWithEmailPassword(email, password)
        }
    }

    // Giữ lại hàm validateInput, bỏ qua phần liên quan đến signup
    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.invalid_email)
            return false
        } else {
            binding.tilEmail.error = null
        }

        if (password.isEmpty() || password.length < 6) {
            binding.tilPassword.error = getString(R.string.invalid_password)
            return false
        } else {
            binding.tilPassword.error = null
        }
        return true
    }

    private fun showEmailSignupFragment() {
        val fragment = EmailSignupFragment() // Tạo instance của Fragment Đăng ký mới
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container_authentication, fragment)
        transaction.addToBackStack(null) // Thêm vào back stack để có thể quay lại
        transaction.commit()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}