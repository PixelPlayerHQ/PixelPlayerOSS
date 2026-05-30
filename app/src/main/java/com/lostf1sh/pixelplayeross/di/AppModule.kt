package com.lostf1sh.pixelplayeross.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.WorkManager
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.lostf1sh.pixelplayeross.BuildConfig
import com.lostf1sh.pixelplayeross.PixelPlayApplication
import com.lostf1sh.pixelplayeross.data.database.AlbumArtThemeDao
import com.lostf1sh.pixelplayeross.data.database.EngagementDao
import com.lostf1sh.pixelplayeross.data.database.FavoritesDao
import com.lostf1sh.pixelplayeross.data.database.LyricsDao
import com.lostf1sh.pixelplayeross.data.database.LocalPlaylistDao
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.database.PixelPlayDatabase
import com.lostf1sh.pixelplayeross.data.database.SearchHistoryDao
import com.lostf1sh.pixelplayeross.data.database.TransitionDao
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.preferences.PlaylistPreferencesRepository
import com.lostf1sh.pixelplayeross.data.preferences.dataStore
import com.lostf1sh.pixelplayeross.data.media.SongMetadataEditor
import com.lostf1sh.pixelplayeross.data.network.deezer.DeezerApiService
import com.lostf1sh.pixelplayeross.data.network.lyrics.LrcLibApiService
import com.lostf1sh.pixelplayeross.data.repository.ArtistImageRepository
import com.lostf1sh.pixelplayeross.data.repository.LyricsRepository
import com.lostf1sh.pixelplayeross.data.repository.LyricsRepositoryImpl
import com.lostf1sh.pixelplayeross.data.repository.MediaStoreSongRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepositoryImpl
import com.lostf1sh.pixelplayeross.data.repository.SongRepository
import com.lostf1sh.pixelplayeross.data.repository.TransitionRepository
import com.lostf1sh.pixelplayeross.data.repository.TransitionRepositoryImpl
import com.lostf1sh.pixelplayeross.data.repository.FolderTreeBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideApplication(@ApplicationContext app: Context): PixelPlayApplication {
        return app as PixelPlayApplication
    }

    @Singleton
    @Provides
    fun provideGson(): com.google.gson.Gson {
        return com.google.gson.Gson()
    }

    @OptIn(UnstableApi::class)
    @Singleton
    @Provides
    fun provideSessionToken(@ApplicationContext context: Context): androidx.media3.session.SessionToken {
        return androidx.media3.session.SessionToken(
            context,
            android.content.ComponentName(context, com.lostf1sh.pixelplayeross.data.service.MusicService::class.java)
        )
    }

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.dataStore

    @Singleton
    @Provides
    fun provideJson(): Json { // Proveer Json
        return Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    @Singleton
    @Provides
    @AppScope
    fun provideAppCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @Singleton
    @Provides
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Singleton
    @Provides
    fun providePixelPlayDatabase(@ApplicationContext context: Context): PixelPlayDatabase {
        val builder = Room.databaseBuilder(
            context.applicationContext,
            PixelPlayDatabase::class.java,
            "pixelplay_database"
        ).addMigrations(
            PixelPlayDatabase.MIGRATION_3_4,
            PixelPlayDatabase.MIGRATION_4_5,
            PixelPlayDatabase.MIGRATION_5_6,
            PixelPlayDatabase.MIGRATION_6_7,
            PixelPlayDatabase.MIGRATION_7_8,
            PixelPlayDatabase.MIGRATION_8_9,
            PixelPlayDatabase.MIGRATION_9_10,
            PixelPlayDatabase.MIGRATION_10_11,
            PixelPlayDatabase.MIGRATION_11_12,
            PixelPlayDatabase.MIGRATION_12_13,
            PixelPlayDatabase.MIGRATION_13_14,
            PixelPlayDatabase.MIGRATION_14_15,
            PixelPlayDatabase.MIGRATION_15_16,
            PixelPlayDatabase.MIGRATION_16_17,
            PixelPlayDatabase.MIGRATION_17_18,
            PixelPlayDatabase.MIGRATION_18_19,
            PixelPlayDatabase.MIGRATION_19_20,
            PixelPlayDatabase.MIGRATION_20_21,
            PixelPlayDatabase.MIGRATION_21_22,
            PixelPlayDatabase.MIGRATION_22_23,
            PixelPlayDatabase.MIGRATION_23_24,
            PixelPlayDatabase.MIGRATION_24_25,
            PixelPlayDatabase.MIGRATION_25_26,
            PixelPlayDatabase.MIGRATION_26_27,
            PixelPlayDatabase.MIGRATION_27_28,
            PixelPlayDatabase.MIGRATION_28_29,
            PixelPlayDatabase.MIGRATION_29_30,
            PixelPlayDatabase.MIGRATION_30_31,
            PixelPlayDatabase.MIGRATION_31_32,
            PixelPlayDatabase.MIGRATION_32_33,
            PixelPlayDatabase.MIGRATION_33_34,
            PixelPlayDatabase.MIGRATION_34_35,
            PixelPlayDatabase.MIGRATION_35_36,
            PixelPlayDatabase.MIGRATION_36_37,
            PixelPlayDatabase.MIGRATION_37_38,
            PixelPlayDatabase.MIGRATION_38_39,
            PixelPlayDatabase.MIGRATION_39_40,
            PixelPlayDatabase.MIGRATION_40_41,
            PixelPlayDatabase.MIGRATION_41_42,
            PixelPlayDatabase.MIGRATION_42_43
        )
            .addCallback(PixelPlayDatabase.createRuntimeArtifactsCallback())
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)

        // P2-4: Only allow destructive migration in debug builds.
        // In release, a migration bug will crash the app (revealing the problem)
        // rather than silently wiping user data (playlists, favorites, statistics).
        if (BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration(dropAllTables = true)
        }

        return builder.build()
    }

    @Singleton
    @Provides
    fun provideAlbumArtThemeDao(database: PixelPlayDatabase): AlbumArtThemeDao {
        return database.albumArtThemeDao()
    }

    @Singleton
    @Provides
    fun provideSearchHistoryDao(database: PixelPlayDatabase): SearchHistoryDao { // NUEVO MÉTODO
        return database.searchHistoryDao()
    }

    @Singleton
    @Provides
    fun provideMusicDao(database: PixelPlayDatabase): MusicDao { // Proveer MusicDao
        return database.musicDao()
    }

    @Singleton
    @Provides
    fun provideTransitionDao(database: PixelPlayDatabase): TransitionDao {
        return database.transitionDao()
    }

    @Singleton
    @Provides
    fun provideEngagementDao(database: PixelPlayDatabase): EngagementDao {
        return database.engagementDao()
    }

    @Singleton
    @Provides
    fun provideFavoritesDao(database: PixelPlayDatabase): FavoritesDao {
        return database.favoritesDao()
    }

    @Singleton
    @Provides
    fun provideLyricsDao(database: PixelPlayDatabase): LyricsDao {
        return database.lyricsDao()
    }

    @Singleton
    @Provides
    fun provideLocalPlaylistDao(database: PixelPlayDatabase): LocalPlaylistDao {
        return database.localPlaylistDao()
    }

    @Singleton
    @Provides
    fun provideNavidromeDao(database: PixelPlayDatabase): com.lostf1sh.pixelplayeross.data.database.NavidromeDao {
        return database.navidromeDao()
    }
    
    @Singleton
    @Provides
    fun provideJellyfinDao(database: PixelPlayDatabase): com.lostf1sh.pixelplayeross.data.database.JellyfinDao {
        return database.jellyfinDao()
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .okHttpClient(OkHttpClient.Builder().build())
            .dispatcher(Dispatchers.Default) // Use CPU-bound dispatcher for decoding
            .allowHardware(true) // Re-enable hardware bitmaps for better performance
            .memoryCache {
                MemoryCache.Builder(context)
                    // Hard 40 MB cap instead of 20%-of-heap. Rationale:
                    //  - On large-heap devices (Pixel 8 etc.) the percentage
                    //    expanded to ~80–100 MB, far beyond what an album-art
                    //    workload needs.
                    //  - allowHardware(true) keeps most decoded pixels in GPU
                    //    memory, so the MemoryCache mostly tracks Bitmap
                    //    references — 40 MB still buffers ~100+ album arts.
                    //  - Tighter cap = less GC pressure and less thermal
                    //    headroom spent on memory pressure during long sessions.
                    .maxSizeBytes(40 * 1024 * 1024)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100 MB disk cache
                    .build()
            }
            .respectCacheHeaders(false) // Ignore server cache headers, always cache
            .build()
    }

    @Provides
    @Singleton
    fun provideLyricsRepository(
        @ApplicationContext context: Context,
        lrcLibApiService: LrcLibApiService,
        lyricsDao: LyricsDao,
        okHttpClient: OkHttpClient
    ): LyricsRepository {
        return LyricsRepositoryImpl(
            context = context,
            lrcLibApiService = lrcLibApiService,
            lyricsDao = lyricsDao,
            okHttpClient = okHttpClient
        )
    }

    @Provides
    @Singleton
    fun provideSongRepository(
        @ApplicationContext context: Context,
        mediaStoreObserver: com.lostf1sh.pixelplayeross.data.observer.MediaStoreObserver,
        favoritesDao: FavoritesDao,
        userPreferencesRepository: UserPreferencesRepository,
        musicDao: MusicDao
    ): SongRepository {
        return MediaStoreSongRepository(
            context = context,
            mediaStoreObserver = mediaStoreObserver,
            favoritesDao = favoritesDao,
            userPreferencesRepository = userPreferencesRepository,
            musicDao = musicDao
        )
    }

    @Provides
    @Singleton
    fun provideFolderTreeBuilder(): FolderTreeBuilder {
        return FolderTreeBuilder()
    }

    @Provides
    @Singleton
    fun provideMusicRepository(
        @ApplicationContext context: Context,
        userPreferencesRepository: UserPreferencesRepository,
        playlistPreferencesRepository: PlaylistPreferencesRepository,
        searchHistoryDao: SearchHistoryDao,
        musicDao: MusicDao,
        lyricsRepository: LyricsRepository,
        songRepository: SongRepository,
        favoritesDao: FavoritesDao,
        artistImageRepository: ArtistImageRepository,
        folderTreeBuilder: FolderTreeBuilder
    ): MusicRepository {
        return MusicRepositoryImpl(
            context = context,
            userPreferencesRepository = userPreferencesRepository,
            playlistPreferencesRepository = playlistPreferencesRepository,
            searchHistoryDao = searchHistoryDao,
            musicDao = musicDao,
            lyricsRepository = lyricsRepository,
            songRepository = songRepository,
            favoritesDao = favoritesDao,
            artistImageRepository = artistImageRepository,
            folderTreeBuilder = folderTreeBuilder
        )

    }

    @Provides
    @Singleton
    fun provideTransitionRepository(
        transitionRepositoryImpl: TransitionRepositoryImpl
    ): TransitionRepository {
        return transitionRepositoryImpl
    }

    @Singleton
    @Provides
    fun provideSongMetadataEditor(
        @ApplicationContext context: Context,
        musicDao: MusicDao,
        userPreferencesRepository: UserPreferencesRepository
    ): SongMetadataEditor {
        return SongMetadataEditor(context, musicDao, userPreferencesRepository)
    }

    /**
     * Provee una instancia singleton de OkHttpClient con logging e interceptor de User-Agent.
     * Retry logic with backoff is handled in coroutine-based callers.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // HEADERS (not BODY) so we never print response bodies that may contain
            // cookies, tokens, or third-party API payloads. Headers are still useful
            // for debugging request paths and status codes.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            // Redact every header that can carry a credential or session token.
            redactHeader("Authorization")
            redactHeader("Proxy-Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
            redactHeader("X-Emby-Token")
            redactHeader("X-Emby-Authorization")
            redactHeader("X-MediaBrowser-Token")
        }
        
        // Connection pool with optimized connections for better performance
        val connectionPool = okhttp3.ConnectionPool(
            maxIdleConnections = 5,
            keepAliveDuration = 30,
            timeUnit = java.util.concurrent.TimeUnit.SECONDS
        )
        
        return OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Add User-Agent header (required by some APIs)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", "PixelPlayerOSS/1.0 (Android; Music Player)")
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * Provee una instancia de OkHttpClient con timeouts para búsquedas de lyrics.
     * Includes DNS resolver, modern TLS, connection pool, and connection retry.
     */
    @Provides
    @Singleton
    @FastOkHttpClient
    fun provideFastOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS)
        
        // Connection pool to reuse connections for better performance
        val connectionPool = okhttp3.ConnectionPool(
            maxIdleConnections = 5,
            keepAliveDuration = 30,
            timeUnit = java.util.concurrent.TimeUnit.SECONDS
        )
        
        // Use the platform resolver and fall back to manual JVM resolution if needed.
        val dns = okhttp3.Dns { hostname ->
            try {
                // First try system DNS
                okhttp3.Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                // Fallback to manual resolution if system DNS fails
                java.net.InetAddress.getAllByName(hostname).toList()
            }
        }

        return OkHttpClient.Builder()
            .dns(dns)
            .connectionPool(connectionPool)
            // Use HTTP/1.1 to avoid HTTP/2 stream issues with some servers
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            // Use modern TLS connection spec
            .connectionSpecs(listOf(
                okhttp3.ConnectionSpec.MODERN_TLS,
                okhttp3.ConnectionSpec.COMPATIBLE_TLS
            ))
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            // Enable built-in retry on connection failure
            .retryOnConnectionFailure(true)
            // Add headers
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithHeaders = originalRequest.newBuilder()
                    .header("User-Agent", "PixelPlayerOSS/1.0 (Android; Music Player)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(requestWithHeaders)
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * Provee una instancia singleton de Retrofit para la API de LRCLIB.
     */
    @Provides
    @Singleton
    fun provideRetrofit(@FastOkHttpClient okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Provee una instancia singleton del servicio de la API de LRCLIB.
     */
    @Provides
    @Singleton
    fun provideLrcLibApiService(retrofit: Retrofit): LrcLibApiService {
        return retrofit.create(LrcLibApiService::class.java)
    }

    /**
     * Provee una instancia de Retrofit para la API de Deezer.
     */
    @Provides
    @Singleton
    @DeezerRetrofit
    fun provideDeezerRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.deezer.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Provee el servicio de la API de Deezer.
     */
    @Provides
    @Singleton
    fun provideDeezerApiService(@DeezerRetrofit retrofit: Retrofit): DeezerApiService {
        return retrofit.create(DeezerApiService::class.java)
    }

    /**
     * Provee el repositorio de imágenes de artistas.
     */
    @Provides
    @Singleton
    fun provideArtistImageRepository(
        deezerApiService: DeezerApiService,
        musicDao: MusicDao
    ): ArtistImageRepository {
        return ArtistImageRepository(deezerApiService, musicDao)
    }
}
