package com.gawasu.sillyn.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface UserIdProvider {
    fun setUserId(userId: String?)
    fun getUserId(): String?
}

@Singleton // Đảm bảo chỉ có 1 instance
class SharedPreferencesUserIdProvider @Inject constructor(
    @ApplicationContext private val context: Context // Sử dụng ApplicationContext để tránh memory leaks
) : UserIdProvider {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    }

    private val USER_ID_KEY = "current_user_id"

    override fun setUserId(userId: String?) {
        prefs.edit {
            if (userId == null) {
                remove(USER_ID_KEY)
            } else {
                putString(USER_ID_KEY, userId)
            }
            apply()
        }
    }

    override fun getUserId(): String? {
        return prefs.getString(USER_ID_KEY, null)
    }
}