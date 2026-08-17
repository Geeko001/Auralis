package com.auralis.music.di

import android.content.Context
import com.google.gson.Gson
import com.auralis.music.data.SessionManager
import com.auralis.music.data.repository.RemoteAudioRepository
import com.auralis.music.data.repository.LocalAudioRepository
import com.auralis.music.data.repository.YouTubeRepository
import com.auralis.music.player.MusicPlayer
import com.auralis.music.di.ApplicationScope
import com.auralis.music.player.SpatialAudioProcessor
import com.auralis.music.core.domain.repository.LibraryRepository
import com.auralis.music.core.data.local.dao.LyricsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideSessionManager(
        @ApplicationContext context: Context
    ): SessionManager {
        return SessionManager(context)
    }
    
    @Provides
    @Singleton
    fun provideYouTubeRepository(
        sessionManager: SessionManager,
        streamingService: com.auralis.music.data.repository.youtube.streaming.YouTubeStreamingService,
        searchService: com.auralis.music.data.repository.youtube.search.YouTubeSearchService,
        accountService: com.auralis.music.data.repository.youtube.account.YouTubeAccountService,
        playlistService: com.auralis.music.data.repository.youtube.playlist.YouTubePlaylistService,
        browseService: com.auralis.music.data.repository.youtube.browse.YouTubeBrowseService,
        catalogService: com.auralis.music.data.repository.youtube.catalog.YouTubeCatalogService,
        libraryActionService: com.auralis.music.data.repository.youtube.library.YouTubeLibraryActionService,
        lyricsService: com.auralis.music.data.repository.youtube.lyrics.YouTubeLyricsService,
        networkMonitor: com.auralis.music.util.NetworkMonitor,
        @ApplicationScope externalScope: kotlinx.coroutines.CoroutineScope
    ): YouTubeRepository {
        return YouTubeRepository(
            sessionManager,
            streamingService,
            searchService,
            accountService,
            playlistService,
            browseService,
            catalogService,
            libraryActionService,
            lyricsService,
            networkMonitor,
            externalScope
        )
    }
    
    @Provides
    @Singleton
    fun provideLocalAudioRepository(
        @ApplicationContext context: Context,
        sessionManager: SessionManager
    ): LocalAudioRepository {
        return LocalAudioRepository(context, sessionManager)
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // Lightweight HTTP-level tracer for RemoteAudio + NewPipe calls. Logs
        // method, URL host/path, response code, and latency at INFO so it
        // survives release builds (Log.d is stripped by proguard).
        val tracer = okhttp3.Interceptor { chain ->
            val req = chain.request()
            val host = req.url.host
            val path = req.url.encodedPath
            // Include the query so we can correlate a failure with the exact search term
            // / song id that triggered it (the bare path is identical for every search).
            val query = req.url.encodedQuery?.let { "?$it" } ?: ""
            val started = System.currentTimeMillis()
            try {
                val resp = chain.proceed(req)
                val ms = System.currentTimeMillis() - started
                when {
                    // 429 is the saavn/sumit.co rate-limit signal behind the v2.5.1.0
                    // offline-fallback crash. Call it out explicitly with Retry-After so
                    // we can see how hard we're being throttled.
                    resp.code == 429 -> {
                        val retryAfter = resp.header("Retry-After") ?: "n/a"
                        android.util.Log.w("HttpTrace", "RATE_LIMITED 429 ${req.method} ${host}${path}${query} retryAfter=${retryAfter}s in ${ms}ms")
                    }
                    resp.code >= 400 -> android.util.Log.w("HttpTrace", "${req.method} ${host}${path}${query} -> ${resp.code} in ${ms}ms")
                    else -> android.util.Log.i("HttpTrace", "${req.method} ${host}${path}${query} -> ${resp.code} in ${ms}ms")
                }
                resp
            } catch (e: java.io.IOException) {
                val ms = System.currentTimeMillis() - started
                android.util.Log.e("HttpTrace", "${req.method} ${host}${path}${query} threw ${e.javaClass.simpleName} after ${ms}ms: ${e.message}")
                throw e
            }
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(5, 5, java.util.concurrent.TimeUnit.MINUTES))
            .protocols(listOf(okhttp3.Protocol.HTTP_3, okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
            .addInterceptor(tracer)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }

    /**
     * OkHttp client for HQ Audio traffic only. The route interceptor rewrites the host per
     * request so [com.auralis.music.data.repository.remote.HqAudioUrlProvider] can
     * divert away from a failing edge at runtime; keeping it off the shared client means
     * YouTube traffic never pays for it.
     */
    @Provides
    @Singleton
    @HqAudioClient
    fun provideHqAudioOkHttpClient(
        okHttpClient: OkHttpClient,
        @ApplicationContext context: Context
    ): OkHttpClient {
        com.auralis.music.data.repository.remote.HqAudioUrlProvider.init(context)
        return okHttpClient.newBuilder()
            .addInterceptor(com.auralis.music.data.repository.remote.HqAudioRouteInterceptor())
            .build()
    }

    @Provides
    @Singleton
    fun provideRemoteAudioApiService(
        @HqAudioClient okHttpClient: OkHttpClient,
        @ApplicationContext context: Context
    ): com.auralis.music.data.repository.remote.RemoteAudioApiService {
        val primaryBaseUrl = com.auralis.music.data.repository.remote.HqAudioUrlProvider.getBaseUrl(context)
        val primary = retrofit2.Retrofit.Builder()
            .baseUrl(primaryBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(com.auralis.music.data.repository.remote.RemoteAudioApiService::class.java)

        val fallback = retrofit2.Retrofit.Builder()
            .baseUrl(com.auralis.music.data.repository.remote.RemoteConstants.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(com.auralis.music.data.repository.remote.RemoteAudioApiService::class.java)

        return com.auralis.music.data.repository.remote.FallbackRemoteAudioApiService(primary, fallback)
    }

    @Provides
    @Singleton
    fun provideHqAudioPlaylistApiService(
        @HqAudioClient okHttpClient: OkHttpClient,
        @ApplicationContext context: Context
    ): com.auralis.music.data.repository.remote.HqAudioPlaylistApiService {
        val primaryBaseUrl = com.auralis.music.data.repository.remote.HqAudioUrlProvider.getBaseUrl(context)
        val primary = retrofit2.Retrofit.Builder()
            .baseUrl(primaryBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(com.auralis.music.data.repository.remote.HqAudioPlaylistApiService::class.java)

        val fallback = retrofit2.Retrofit.Builder()
            .baseUrl(com.auralis.music.data.repository.remote.RemoteConstants.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(com.auralis.music.data.repository.remote.HqAudioPlaylistApiService::class.java)

        return com.auralis.music.data.repository.remote.FallbackHqAudioPlaylistApiService(primary, fallback)
    }
    
    @Provides
    @Singleton
    fun provideRemoteAudioRepository(
        okHttpClient: OkHttpClient,
        gson: Gson,
        apiService: com.auralis.music.data.repository.remote.RemoteAudioApiService,
        playlistApiService: com.auralis.music.data.repository.remote.HqAudioPlaylistApiService,
        sessionManager: SessionManager
    ): RemoteAudioRepository {
        return RemoteAudioRepository(okHttpClient, gson, apiService, playlistApiService, sessionManager)
    }
    
    @Provides
    @Singleton
    fun provideMusicHapticsManager(
        @ApplicationContext context: Context,
        sessionManager: SessionManager
    ): com.auralis.music.util.MusicHapticsManager {
        return com.auralis.music.util.MusicHapticsManager(context, sessionManager)
    }
    
    @Provides
    @Singleton
    fun provideMusicPlayer(
        @ApplicationContext context: Context,
        youTubeRepository: YouTubeRepository,
        remoteAudioRepository: RemoteAudioRepository,
        sessionManager: SessionManager,
        sleepTimerManager: com.auralis.music.player.SleepTimerManager,
        listeningHistoryRepository: com.auralis.music.data.repository.ListeningHistoryRepository,
        cache: androidx.media3.datasource.cache.Cache,
        @PlayerDataSource dataSourceFactory: androidx.media3.datasource.DataSource.Factory,
        musicHapticsManager: com.auralis.music.util.MusicHapticsManager,
        ttsManager: com.auralis.music.util.TTSManager,
        spatialAudioProcessor: SpatialAudioProcessor,
        nativeSpatialAudio: com.auralis.music.player.NativeSpatialAudio,
        streamingService: com.auralis.music.data.repository.youtube.streaming.YouTubeStreamingService,
        loudnessAnalyzer: com.auralis.music.player.LoudnessAnalyzer,
    ): MusicPlayer {
        return MusicPlayer(
            context,
            youTubeRepository,
            remoteAudioRepository,
            sessionManager,
            sleepTimerManager,
            listeningHistoryRepository,
            cache,
            dataSourceFactory,
            musicHapticsManager,
            ttsManager,
            spatialAudioProcessor,
            nativeSpatialAudio,
            streamingService,
            loudnessAnalyzer,
        )
    }
    @Provides
    @Singleton
    fun provideLyricsRepository(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        youTubeRepository: YouTubeRepository,
        remoteAudioRepository: RemoteAudioRepository,
        betterLyricsProvider: com.auralis.music.providers.lyrics.BetterLyricsProvider,
        simpMusicLyricsProvider: com.auralis.music.simpmusic.SimpMusicLyricsProvider,
        kuGouLyricsProvider: com.auralis.music.kugou.KuGouLyricsProvider,
        lrcLibLyricsProvider: com.auralis.music.lrclib.LrcLibLyricsProvider,
        localLyricsProvider: com.auralis.music.providers.lyrics.LocalLyricsProvider,
        sessionManager: SessionManager,
        lyricsDao: LyricsDao
    ): com.auralis.music.data.repository.LyricsRepository {
        return com.auralis.music.data.repository.LyricsRepository(
            context,
            okHttpClient,
            youTubeRepository,
            remoteAudioRepository,
            betterLyricsProvider,
            simpMusicLyricsProvider,
            kuGouLyricsProvider,
            lrcLibLyricsProvider,
            localLyricsProvider,
            sessionManager,
            lyricsDao
        )
    }

    @Provides
    @Singleton
    fun provideListenTogetherClient(
        @ApplicationContext context: Context
    ): com.auralis.music.shareplay.ListenTogetherClient {
        return com.auralis.music.shareplay.ListenTogetherClient(context)
    }

    @Provides
    @Singleton
    fun provideListenTogetherManager(
        client: com.auralis.music.shareplay.ListenTogetherClient,
        youTubeRepository: YouTubeRepository,
        remoteAudioRepository: RemoteAudioRepository,
        sessionManager: SessionManager
    ): com.auralis.music.shareplay.ListenTogetherManager {
        val manager = com.auralis.music.shareplay.ListenTogetherManager(client, youTubeRepository, remoteAudioRepository, sessionManager)
        manager.initialize()
        return manager
    }

    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): androidx.work.WorkManager {
        return androidx.work.WorkManager.getInstance(context)
    }
}
