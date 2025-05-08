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
import androidx.fragment.app.viewModels
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.FragmentAuthenticationOptionsBinding
import com.gawasu.sillyn.ui.auth.EmailLoginFragment
import com.gawasu.sillyn.ui.viewmodel.AuthViewModel
import com.gawasu.sillyn.utils.FirebaseResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import dagger.hilt.android.AndroidEntryPoint
import androidx.fragment.app.activityViewModels
@AndroidEntryPoint
class AuthenticationOptionsFragment : Fragment() {
    private var _binding: FragmentAuthenticationOptionsBinding? = null
    private val binding get() = _binding!!
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
        // Removed observeViewModel() from Fragment
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

// Removed observeViewModel() from Fragment - Activity will handle navigation based on loginResult
// Fragment just triggers the sign-in

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }


    private fun handleGoogleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account: GoogleSignInAccount = completedTask.getResult(ApiException::class.java)
            account?.idToken?.let { idToken ->
                authViewModel.signInWithGoogle(idToken) // Fragment triggers Google Sign-in in ViewModel
            } ?: run {
                Toast.makeText(requireContext(), "Google Sign-in failed: Account ID Token is null", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Google Sign-in failed: Account ID Token is null")
            }
        } catch (e: ApiException) {
            Toast.makeText(requireContext(), "Google Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Google Sign-in failed", e)
        }
    }


    private fun showEmailLoginFragment(isSignup: Boolean = false) {
        val fragment = EmailLoginFragment.newInstance(isSignup)
        replaceFragment(fragment)
    }


    private fun showForgotPasswordFragment() {
        Toast.makeText(requireContext(), "Forgot Password Fragment is not implemented yet", Toast.LENGTH_SHORT).show()
    }


    private fun replaceFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container_authentication, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}