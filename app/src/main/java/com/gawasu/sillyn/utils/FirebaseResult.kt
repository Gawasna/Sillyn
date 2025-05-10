package com.gawasu.sillyn.utils

// Chỉ sửa lại phương thức voidSuccess() để sử dụng Unit thay vì Void
sealed class FirebaseResult<out T> {
    data class Success<out T>(val data: T) : FirebaseResult<T>()
    data class Error(val exception: Exception) : FirebaseResult<Nothing>()
    object Loading : FirebaseResult<Nothing>()

    companion object {
        fun voidSuccess(): FirebaseResult<Unit> = Success(Unit)
    }
}