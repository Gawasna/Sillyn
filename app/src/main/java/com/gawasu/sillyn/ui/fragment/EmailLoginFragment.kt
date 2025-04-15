package com.gawasu.sillyn.ui.auth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.FragmentEmailLoginBinding
import com.gawasu.sillyn.ui.viewmodel.AuthViewModel
import com.gawasu.sillyn.utils.FirebaseResult
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EmailLoginFragment : Fragment() {

    private var _binding: FragmentEmailLoginBinding? = null
    private val binding get() = _binding!!
    private val authViewModel: AuthViewModel by viewModels()
    private var isSignupMode: Boolean = false

    companion object {
        private const val ARG_IS_SIGNUP = "arg_is_signup"
        private const val TAG = "EMAIL LOGIN FRAGMENT"
        fun newInstance(isSignup: Boolean = false): EmailLoginFragment {
            val fragment = EmailLoginFragment()
            val args = Bundle().apply {
                putBoolean(ARG_IS_SIGNUP, isSignup)
            }
            fragment.arguments = args
            return fragment
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            isSignupMode = it.getBoolean(ARG_IS_SIGNUP, false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmailLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupUI() {
        if (isSignupMode) {
            binding.btnLogin.visibility = View.GONE
            binding.btnSignup.visibility = View.VISIBLE
        } else {
            binding.btnLogin.visibility = View.VISIBLE
            binding.btnSignup.visibility = View.GONE
        }
    }


    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            performLogin()
        }
        binding.btnSignup.setOnClickListener {
            performSignup()
        }
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun observeViewModel() {
        authViewModel.loginResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is FirebaseResult.Loading -> {
                    Log.d(TAG, "Login Loading...")
                    // TODO: Show loading - ví dụ progress bar
                }
                is FirebaseResult.Success -> {
                    Log.d(TAG, "Login Success, handled by Activity")
                    // Login success - Activity observer sẽ navigate
                }
                is FirebaseResult.Error -> {
                    Log.e(TAG, "Login Failed", result.exception)
                    val errorMessage = result.exception.localizedMessage ?: getString(R.string.authentication_error, "Unknown error")
                    Toast.makeText(requireContext(), getString(R.string.login_failed, errorMessage), Toast.LENGTH_LONG).show()
                    // TODO: Hide loading
                }
            }
        }
        authViewModel.signUpResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is FirebaseResult.Loading -> {
                    Log.d(TAG, "Signup Loading...")
                    // TODO: Show loading
                }
                is FirebaseResult.Success -> {
                    Log.d(TAG, "Signup Success, returning to AuthenticationOptionsFragment")
                    Toast.makeText(requireContext(), getString(R.string.signup_successful_login_please), Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack() // Quay lại AuthenticationOptionsFragment sau signup thành công
                }
                is FirebaseResult.Error -> {
                    Log.e(TAG, "Signup Failed", result.exception)
                    val errorMessage = result.exception.localizedMessage ?: getString(R.string.authentication_error, "Unknown error")
                    Toast.makeText(requireContext(), getString(R.string.signup_failed, errorMessage), Toast.LENGTH_LONG).show()
                    // TODO: Hide loading
                }
            }
        }
    }


    private fun performLogin() {
        val email = binding.etEmail.text.toString()
        val password = binding.etPassword.text.toString()

        if (validateInput(email, password)) {
            authViewModel.loginWithEmailPassword(email, password)
        }
    }

    private fun performSignup() {
        val email = binding.etEmail.text.toString()
        val password = binding.etPassword.text.toString()

        if (validateInput(email, password)) {
            authViewModel.signUpWithEmailPassword(email, password)
        }
    }


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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}