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
import androidx.lifecycle.ViewModelProvider
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.FragmentAuthenticationOptionsBinding
import com.gawasu.sillyn.ui.viewmodel.AuthViewModel
import com.gawasu.sillyn.ui.viewmodel.AuthViewModelFactory
import com.gawasu.sillyn.data.repository.AuthRepository
import com.gawasu.sillyn.data.firebase.FirestoreAuthService
import com.gawasu.sillyn.utils.FirebaseResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task

class AuthenticationOptionsFragment : Fragment() {

    private var _binding: FragmentAuthenticationOptionsBinding? = null
    private val binding get() = _binding!!
    private lateinit var authViewModel: AuthViewModel
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

        // Initialize ViewModel
        val authRepository = AuthRepository(FirestoreAuthService())
        val factory = AuthViewModelFactory(authRepository)
        authViewModel = ViewModelProvider(requireActivity(), factory)[AuthViewModel::class.java]

        setupGoogleSignIn()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupGoogleSignIn() {
        // Configure Google Sign In options to request the ID token
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        // Initialize ActivityResultLauncher for Google Sign-in
        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val intent = result.data
                intent?.let {
                    val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(it)
                    handleGoogleSignInResult(task)
                }
            } else {
                Toast.makeText(requireContext(), "Google Sign-in Cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun setupClickListeners() {
        binding.btnLoginEmail.setOnClickListener {
            showEmailLoginFragment()
        }
        binding.btnLoginGoogle.setOnClickListener {
            signInWithGoogle()
        }
        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordFragment()
        }
        binding.tvSignUp.setOnClickListener {
            showEmailLoginFragment(true) // Signup mode
        }
    }

    private fun observeViewModel() {
        authViewModel.loginResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is FirebaseResult.Loading -> {
                    // TODO: Show loading indicator
                    Log.d(TAG, "Loading...")
                }
                is FirebaseResult.Success -> {
                    // Login successful
                    Log.d(TAG, "Login Success: ${result.data}")
                    // Navigation to MainActivity is handled in AuthenticationActivity
                }
                is FirebaseResult.Error -> {
                    // Login failed
                    val errorMessage = result.exception.localizedMessage ?: getString(R.string.authentication_error, "Unknown error")
                    Toast.makeText(requireContext(), getString(R.string.login_failed, errorMessage), Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Login Error: $errorMessage", result.exception)
                    // TODO: Hide loading indicator
                }
            }
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
                // Send ID Token to Firebase via ViewModel
                authViewModel.signInWithGoogle(idToken)
            } ?: run {
                Toast.makeText(requireContext(), "Google Sign-in failed: Account ID Token is null", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Google Sign-in failed: Account ID Token is null")
            }
        } catch (e: ApiException) {
            // The ApiException status code indicates the Google Sign-in service error.
            Toast.makeText(requireContext(), "Google Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Google Sign-in failed", e)
        }
    }


    private fun showEmailLoginFragment(isSignup: Boolean = false) {
        val fragment = EmailLoginFragment.newInstance(isSignup)
        replaceFragment(fragment)
    }


    private fun showForgotPasswordFragment() {
        replaceFragment(ForgotPasswordFragment())
    }


    private fun replaceFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}