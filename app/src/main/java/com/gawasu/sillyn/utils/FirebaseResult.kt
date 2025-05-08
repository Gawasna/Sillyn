package com.gawasu.sillyn.utils

sealed class FirebaseResult<out T> {
    data class Success<out T>(val data: T) : FirebaseResult<T>()
    data class Error(val exception: Exception) : FirebaseResult<Nothing>()
    object Loading : FirebaseResult<Nothing>()

    companion object {
        // Factory method specifically for the Success<Void> case
        // This allows creating a Success result that signifies 'no data'
        // By casting null as Void, we satisfy the type system for this specific use case
        fun voidSuccess(): FirebaseResult<Void> = Success(null as Void) // <--- Thêm phương thức này
    }
}