package nl.part66l.logbook.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import nl.part66l.logbook.drive.DriveApiClient
import nl.part66l.logbook.drive.DriveApiClientImpl
import nl.part66l.logbook.drive.DriveAuthManager
import nl.part66l.logbook.drive.DriveAuthManagerImpl
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object OkHttpModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DriveModule {

    @Binds
    abstract fun bindDriveAuthManager(impl: DriveAuthManagerImpl): DriveAuthManager

    @Binds
    abstract fun bindDriveApiClient(impl: DriveApiClientImpl): DriveApiClient
}
