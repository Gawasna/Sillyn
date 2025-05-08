package com.gawasu.sillyn.ui.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.gawasu.sillyn.R
import com.gawasu.sillyn.ui.activity.AuthenticationActivity

class FinishOnboardingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_finish_onboarding, container, false)

        val goToAuthButton = view.findViewById<Button>(R.id.buttonGoToAuth)
        goToAuthButton.setOnClickListener {
            // Mark onboarding as complete
            //markOnboardingComplete()

            // Navigate to the Authentication Activity
            val intent = Intent(requireActivity(), AuthenticationActivity::class.java)
            // Add flags to clear the back stack so the user can't go back to onboarding
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            // Finish the current activity (MainActivity)
            requireActivity().finish()
        }

        return view
    }

//    private fun markOnboardingComplete() {
//        // Save the state in SharedPreferences
//        val prefs = requireActivity().getSharedPreferences(AppPreferences.PREF_NAME, Context.MODE_PRIVATE)
//        with(prefs.edit()) {
//            putBoolean(AppPreferences.KEY_ONBOARDING_COMPLETE, true)
//            apply()
//        }
//    }
}