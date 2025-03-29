package com.gawasu.sillyn.ui.activity

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.gawasu.sillyn.databinding.ActivityForgotPasswordBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.btnResetPassword.setOnClickListener { resetPassword() }
        binding.tvBackToLogin.setOnClickListener { finish() }
    }

    private fun resetPassword() {
        val email = binding.etEmail.text.toString()

        // Validate email
        if (TextUtils.isEmpty(email)) {
            binding.etEmail.error = "Email không được để trống"
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Email không hợp lệ"
            return
        }

        // Show loading
        binding.progressBar.visibility = View.VISIBLE
        binding.btnResetPassword.isEnabled = false

        // Send password reset email
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                binding.progressBar.visibility = View.GONE
                binding.btnResetPassword.isEnabled = true

                if (task.isSuccessful) {
                    Log.d(TAG, "Password reset email sent to $email")
                    Snackbar.make(binding.root,
                        "Email đặt lại mật khẩu đã được gửi. Vui lòng kiểm tra hộp thư.",
                        Snackbar.LENGTH_LONG).show()

                    // Clear input and show success message
                    binding.etEmail.text?.clear()
                    binding.tvInstructions.text = "Email đặt lại mật khẩu đã được gửi đến $email. " +
                            "Vui lòng kiểm tra hộp thư và làm theo hướng dẫn."
                } else {
                    Log.w(TAG, "Failed to send reset email", task.exception)
                    Snackbar.make(binding.root,
                        "Không thể gửi email đặt lại mật khẩu: ${task.exception?.message}",
                        Snackbar.LENGTH_LONG).show()
                }
            }
    }

    companion object {
        private const val TAG = "ForgotPasswordActivity"
    }
}