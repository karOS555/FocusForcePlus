package com.focusforceplus.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Application-lifetime coroutine scope for fire-and-forget work that must outlive
 * short-lived components (e.g. a service writing its history row while stopping).
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
