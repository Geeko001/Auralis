package com.auralis.music.di

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.work.WorkManager
import com.google.gson.Gson
import com.auralis.music.core.data.local.AppDatabase
import com.auralis.music.core.data.local.dao.DislikedItemDao
import com.auralis.music.core.data.local.dao.LibraryDao
import com.auralis.music.core.data.local.dao.ListeningHistoryDao
import com.auralis.music.core.data.local.dao.SongGenreDao
import com.auralis.music.core.domain.repository.LibraryRepository
import com.auralis.music.data.SessionManager
import com.auralis.music.data.repository.DownloadRepository
import com.auralis.music.data.repository.RemoteAudioRepository
import com.auralis.music.data.repository.ListeningHistoryRepository
import com.auralis.music.data.repository.LocalAudioRepository
import com.auralis.music.data.repository.LyricsRepository
import com.auralis.music.data.repository.YouTubeRepository
import com.auralis.music.data.repository.youtube.internal.YouTubeApiClient
import com.auralis.music.data.repository.youtube.internal.YouTubeJsonParser
import com.auralis.music.data.repository.youtube.search.YouTubeSearchService
import com.auralis.music.data.repository.youtube.streaming.YouTubeStreamingService
import com.auralis.music.ai.AIEqualizerService
import com.auralis.music.data.BackupManager
import com.auralis.music.lastfm.LastFmClient
import com.auralis.music.lastfm.LastFmConfig
import com.auralis.music.lastfm.LastFmRepository
import com.auralis.music.data.repository.SponsorBlockRepository
import com.auralis.music.discord.DiscordManager
import com.auralis.music.player.AudioARManager
import com.auralis.music.player.MusicPlayer
import com.auralis.music.player.SleepTimerManager
import com.auralis.music.player.SpatialAudioProcessor
import com.auralis.music.recommendation.RecommendationEngine
import com.auralis.music.recommendation.SmartQueueManager
import com.auralis.music.recommendation.WrappedGenerator
import com.auralis.music.updater.UpdateDownloader
import com.auralis.music.shareplay.ListenTogetherClient
import com.auralis.music.shareplay.ListenTogetherManager
import com.auralis.music.updater.UpdateChecker
import com.auralis.music.util.MusicHapticsManager
import com.auralis.music.util.NetworkMonitor
import com.auralis.music.util.PlaylistImportHelper
import com.auralis.music.util.RingtoneHelper
import com.auralis.music.util.SpotifyImportHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient

/**
 * Hilt -> Koin bridge for the parallel-DI period of phase 1.
 *
 * Why this exists:
 * Hilt and Koin would otherwise each construct their own copy of every
 * singleton, and shared OS resources (SimpleCache file lock, Room DB lock,
 * ExoPlayer instance, MediaSession, audio focus owner) crash or misbehave on
 * the second construction. We saw `IllegalStateException: Another SimpleCache
 * instance uses the folder` during chunk 1c.1.
 *
 * The bridge has Koin's `single { ... }` blocks delegate to Hilt's already-
 * constructed instances via this @EntryPoint. Result: single source of truth
 * for every shared object during the migration.
 *
 * Lifecycle:
 * - Added: chunk 1c (now). Required for any Koin consumer that resolves a
 *   shared singleton.
 * - Removed: chunk 1d, when Hilt itself is removed. At that point Koin's
 *   `single { ... }` blocks reclaim direct construction.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HiltKoinBridgeEntryPoint {
    // app/di — AppModule
    fun sessionManager(): SessionManager
    fun youTubeRepository(): YouTubeRepository
    fun localAudioRepository(): LocalAudioRepository
    fun okHttpClient(): OkHttpClient
    fun gson(): Gson
    fun remoteAudioRepository(): RemoteAudioRepository
    fun musicHapticsManager(): MusicHapticsManager
    fun musicPlayer(): MusicPlayer
    fun lyricsRepository(): LyricsRepository
    fun listenTogetherClient(): ListenTogetherClient
    fun listenTogetherManager(): ListenTogetherManager
    fun workManager(): WorkManager

    // app/di — CacheModule
    fun cache(): Cache

    @PlayerDataSource
    fun playerDataSourceFactory(): DataSource.Factory

    @DownloadDataSource
    fun downloadDataSourceFactory(): DataSource.Factory

    // app — transitive @Inject constructor classes that 1c.1 VMs reach
    fun youTubeJsonParser(): YouTubeJsonParser
    fun youTubeApiClient(): YouTubeApiClient
    fun youTubeStreamingService(): YouTubeStreamingService
    fun youTubeSearchService(): YouTubeSearchService
    fun networkMonitor(): NetworkMonitor
    fun listeningHistoryRepository(): ListeningHistoryRepository
    fun ringtoneHelper(): RingtoneHelper
    fun downloadRepository(): DownloadRepository

    // core/data
    fun appDatabase(): AppDatabase
    fun libraryDao(): LibraryDao
    fun listeningHistoryDao(): ListeningHistoryDao
    fun dislikedItemDao(): DislikedItemDao
    fun songGenreDao(): SongGenreDao
    fun libraryRepository(): LibraryRepository

    // scrobbler
    fun lastFmConfig(): LastFmConfig
    fun lastFmClient(): LastFmClient

    // updater
    fun updateChecker(): UpdateChecker

    // chunk 1c.3 — additional transitive @Inject constructor classes
    fun spotifyImportHelper(): SpotifyImportHelper
    fun playlistImportHelper(): PlaylistImportHelper
    fun recommendationEngine(): RecommendationEngine
    fun lastFmRepository(): LastFmRepository
    fun audioARManager(): AudioARManager
    fun aiEqualizerService(): AIEqualizerService
    fun wrappedGenerator(): WrappedGenerator
    fun backupManager(): BackupManager

    // chunk 1c.4 — PlayerViewModel + UpdateViewModel transitive @Inject classes
    fun sleepTimerManager(): SleepTimerManager
    fun smartQueueManager(): SmartQueueManager
    fun sponsorBlockRepository(): SponsorBlockRepository
    fun discordManager(): DiscordManager
    fun spatialAudioProcessor(): SpatialAudioProcessor
    fun updateDownloader(): UpdateDownloader
    fun loudnessAnalyzer(): com.auralis.music.player.LoudnessAnalyzer
}

/** One-call accessor used by Koin module blocks. Resolved against the application Context. */
internal fun bridge(context: Context): HiltKoinBridgeEntryPoint =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        HiltKoinBridgeEntryPoint::class.java,
    )
