package com.auralis.music.lastfm.di

import com.auralis.music.lastfm.LastFmClient
import com.auralis.music.lastfm.LastFmConfig
import com.auralis.music.lastfm.LastFmConfigImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LastFmModule {

    @Binds
    @Singleton
    abstract fun bindLastFmConfig(impl: LastFmConfigImpl): LastFmConfig

    companion object {
        @Provides
        @Singleton
        fun provideLastFmClient(config: LastFmConfig): LastFmClient {
            return LastFmClient(config)
        }
    }
}
