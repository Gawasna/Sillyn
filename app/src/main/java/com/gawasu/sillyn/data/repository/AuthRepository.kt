package com.gawasu.sillyn.data.repository

import javax.inject.Inject

class AuthRepository @Inject constructor(
    authRepositoryImpl: AuthRepositoryImpl
) : AuthRepositoryInterface by authRepositoryImpl