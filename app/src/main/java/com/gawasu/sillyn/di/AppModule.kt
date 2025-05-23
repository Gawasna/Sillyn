package com.gawasu.sillyn.di

import android.app.AlarmManager
import android.content.Context
import com.gawasu.sillyn.data.local.SharedPreferencesUserIdProvider
import com.gawasu.sillyn.data.local.UserIdProvider
import com.gawasu.sillyn.data.remote.auth.AuthService
import com.gawasu.sillyn.data.remote.auth.AuthServiceImpl
import com.gawasu.sillyn.data.remote.firestore.FirestoreService
import com.gawasu.sillyn.data.remote.firestore.FirestoreServiceImpl
import com.gawasu.sillyn.data.repository.AuthRepository
import com.gawasu.sillyn.data.repository.AuthRepositoryImpl
import com.gawasu.sillyn.data.repository.AuthRepositoryInterface
import com.gawasu.sillyn.data.repository.TaskRepository
import com.gawasu.sillyn.data.repository.TaskRepositoryImpl
import com.gawasu.sillyn.data.repository.TaskRepositoryInterface
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(authRepositoryImpl: AuthRepositoryImpl): AuthRepositoryInterface

    @Binds
    @Singleton
    abstract fun bindTaskRepository(taskRepositoryImpl: TaskRepositoryImpl): TaskRepositoryInterface

    @Binds
    @Singleton
    abstract fun bindAuthService(authServiceImpl: AuthServiceImpl): AuthService

    @Binds
    @Singleton
    abstract fun bindFirestoreService(firestoreServiceImpl: FirestoreServiceImpl): FirestoreService

    @Binds
    @Singleton
    abstract fun bindUserIdProvider(
        sharedPreferencesUserIdProvider: SharedPreferencesUserIdProvider
    ): UserIdProvider

    companion object {
        @Provides
        @Singleton
        fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

        @Provides
        @Singleton
        fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

        @Provides
        @Singleton
        fun provideAlarmManager(@dagger.hilt.android.qualifiers.ApplicationContext context: Context): AlarmManager {
            return context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        }
    }
}