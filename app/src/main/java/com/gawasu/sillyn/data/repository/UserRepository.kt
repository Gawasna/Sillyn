package com.gawasu.sillyn.data.repository

import com.gawasu.sillyn.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getCurrentUser(): Flow<User?>
    fun signInWithEmailAndPassword(email: String, password: String): Flow<Result<User>>
    fun signUpWithEmailAndPassword(email: String, password: String): Flow<Result<User>>
    fun signInWithGoogle(): Flow<Result<User>>
    fun signOut(): Flow<Result<Boolean>>
    // TODO: Các methods khác liên quan đến User (profile, update info, etc.)
}