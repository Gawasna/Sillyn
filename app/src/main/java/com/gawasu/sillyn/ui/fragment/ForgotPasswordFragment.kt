package com.gawasu.sillyn.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.gawasu.sillyn.R
import com.gawasu.sillyn.data.firebase.FirestoreAuthService
import com.gawasu.sillyn.data.repository.AuthRepository
import com.gawasu.sillyn.databinding.FragmentForgotPasswordBinding
import com.gawasu.sillyn.ui.viewmodel.AuthViewModel
import com.gawasu.sillyn.ui.viewmodel.AuthViewModelFactory
import com.gawasu.sillyn.utils.FirebaseResult

class ForgotPasswordFragment : Fragment() {

    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!
    private lateinit var authViewModel: AuthViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel
        val authRepository = AuthRepository(FirestoreAuthService())
        val factory = AuthViewModelFactory(authRepository)
        authViewModel = ViewModelProvider(requireActivity(), factory)[AuthViewModel::class.java]

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnResetPassword.setOnClickListener {
            val email = binding.etEmailReset.text.toString()
            if (validateEmail(email)) {
                authViewModel.sendPasswordResetEmail(email)
            }
        }
        binding.tvBackToLoginFromReset.setOnClickListener {
            parentFragmentManager.popBackStack() // Go back to Login screen
        }
    }

    private fun observeViewModel() {
        authViewModel.resetPasswordResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is FirebaseResult.Loading -> {
                    // TODO: Show loading
                }
                is FirebaseResult.Success -> {
                    // Reset password email sent successfully
                    Toast.makeText(requireContext(), getString(R.string.reset_password_email_sent, binding.etEmailReset.text.toString()), Toast.LENGTH_LONG).show()
                    parentFragmentManager.popBackStack() // Go back to Login screen
                }
                is FirebaseResult.Error -> {
                    // Reset password email failed to send
                    val errorMessage = result.exception.localizedMessage ?: getString(R.string.authentication_error, "Unknown error")
                    Toast.makeText(requireContext(), getString(R.string.reset_password_failed, errorMessage), Toast.LENGTH_LONG).show()
                    // TODO: Hide loading
                }
            }
        }
    }

    private fun validateEmail(email: String): Boolean {
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmailReset.error = getString(R.string.invalid_email)
            return false
        } else {
            binding.tilEmailReset.error = null
            return true
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}