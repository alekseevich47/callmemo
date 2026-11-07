package com.alekseevich47.callmemo.di

import android.content.Context
import androidx.room.Room
import com.alekseevich47.callmemo.data.local.CallMemoDatabase
import com.alekseevich47.callmemo.data.secure.SecureSettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CallMemoDatabase =
        Room.databaseBuilder(context, CallMemoDatabase::class.java, "callmemo.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideSecureSettings(@ApplicationContext context: Context): SecureSettingsStore =
        SecureSettingsStore(context)
}
