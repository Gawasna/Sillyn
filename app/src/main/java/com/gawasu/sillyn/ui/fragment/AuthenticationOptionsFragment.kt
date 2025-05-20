package com.gawasu.sillyn.ui.fragment

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // Sử dụng activityViewModels để share ViewModel
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.FragmentAuthenticationOptionsBinding
import com.gawasu.sillyn.ui.fragment.EmailSignupFragment
import com.gawasu.sillyn.ui.fragment.EmailLoginFragment
import com.gawasu.sillyn.ui.viewmodel.AuthViewModel
import com.gawasu.sillyn.utils.FirebaseResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AuthenticationOptionsFragment : Fragment() {
    private var _binding: FragmentAuthenticationOptionsBinding? = null
    private val binding get() = _binding!!
    // Sử dụng activityViewModels nếu AuthViewModel được share scope với Activity
    private val authViewModel: AuthViewModel by activityViewModels()
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    companion object {
        private const val TAG = "AuthOptionsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthenticationOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGoogleSignIn()
        setupClickListeners()
        // observeViewModel() được xử lý ở Activity
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val intent = result.data
                intent?.let {
                    val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(it)
                    handleGoogleSignInResult(task)
                }
            } else {
                Toast.makeText(requireContext(), "Đăng nhập Google bị hủy", Toast.LENGTH_SHORT).show() // Cập nhật string
            }
        }
    }


    private fun setupClickListeners() {
        binding.btnLoginEmail.setOnClickListener {
            showEmailLoginFragment() // Chuyển đến màn hình Login
        }
        binding.btnLoginGoogle.setOnClickListener {
            signInWithGoogle()
        }
        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordFragment()
        }
        binding.tvSignUp.setOnClickListener {
            showEmailSignupFragment() // Chuyển đến màn hình Đăng ký MỚI
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun handleGoogleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account: GoogleSignInAccount = completedTask.getResult(ApiException::class.java)
            account?.idToken?.let { idToken ->
                // Kích hoạt Google Sign-in trong ViewModel
                authViewModel.signInWithGoogle(idToken)
            } ?: run {
                Toast.makeText(requireContext(), "Đăng nhập Google thất bại: ID Token rỗng", Toast.LENGTH_LONG).show() // Cập nhật string
                Log.e(TAG, "Google Sign-in failed: Account ID Token is null")
            }
        } catch (e: ApiException) {
            val errorMessage = e.localizedMessage ?: "Lỗi không xác định"
            Toast.makeText(requireContext(), "Đăng nhập Google thất bại: $errorMessage", Toast.LENGTH_LONG).show() // Cập nhật string
            Log.e(TAG, "Google Sign-in failed", e)
        }
    }

    // Hàm hiển thị Fragment Đăng nhập (không còn tham số isSignup)
    private fun showEmailLoginFragment() {
        val fragment = EmailLoginFragment() // Chỉ tạo EmailLoginFragment
        replaceFragment(fragment)
    }

    // Hàm hiển thị Fragment Đăng ký (MỚI)
    private fun showEmailSignupFragment() {
        val fragment = EmailSignupFragment() // Tạo instance của Fragment Đăng ký
        replaceFragment(fragment)
    }


    private fun showForgotPasswordFragment() {
        Toast.makeText(requireContext(), "Chức năng Quên mật khẩu chưa được triển khai", Toast.LENGTH_SHORT).show() // Cập nhật string
        // TODO: Implement Forgot Password Fragment
    }


    private fun replaceFragment(fragment: Fragment) {
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