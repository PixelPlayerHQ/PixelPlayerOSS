package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.backup.BackupManager
import com.lostf1sh.pixelplayeross.data.backup.model.BackupSection
import com.lostf1sh.pixelplayeross.data.backup.model.BackupOperationType
import com.lostf1sh.pixelplayeross.data.backup.model.BackupTransferProgressUpdate
import com.lostf1sh.pixelplayeross.data.backup.model.BackupHistoryEntry
import com.lostf1sh.pixelplayeross.data.backup.model.RestorePlan
import com.lostf1sh.pixelplayeross.data.backup.model.RestoreResult
import com.lostf1sh.pixelplayeross.data.backup.model.ValidationError
import com.lostf1sh.pixelplayeross.data.preferences.AppThemeMode
import com.lostf1sh.pixelplayeross.data.preferences.CarouselStyle
import com.lostf1sh.pixelplayeross.data.preferences.LibraryNavigationMode
import com.lostf1sh.pixelplayeross.data.preferences.ThemePreference
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.preferences.AlbumArtQuality
import com.lostf1sh.pixelplayeross.data.preferences.AlbumArtColorAccuracy
import com.lostf1sh.pixelplayeross.data.preferences.AlbumArtPaletteStyle
import com.lostf1sh.pixelplayeross.data.preferences.CollagePattern
import com.lostf1sh.pixelplayeross.data.preferences.FullPlayerLoadingTweaks
import com.lostf1sh.pixelplayeross.data.preferences.ThemePreferencesRepository
import com.lostf1sh.pixelplayeross.data.repository.LyricsRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import com.lostf1sh.pixelplayeross.data.model.LyricsSourcePreference
import com.lostf1sh.pixelplayeross.data.worker.SyncManager
import com.lostf1sh.pixelplayeross.data.worker.SyncProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.preferences.NavBarStyle
import com.lostf1sh.pixelplayeross.data.preferences.LaunchTab
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.service.player.HiFiCapabilityChecker
import java.io.File

data class SettingsUiState(
    val isLoadingDirectories: Boolean = false,
    val appThemeMode: String = AppThemeMode.FOLLOW_SYSTEM,
    val playerThemePreference: String = ThemePreference.ALBUM_ART,
    val albumArtPaletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.default,
    val albumArtColorAccuracy: Int = AlbumArtColorAccuracy.DEFAULT,
    val mockGenresEnabled: Boolean = false,
    val navBarCornerRadius: Int = 32,
    val navBarStyle: String = NavBarStyle.DEFAULT,
    val navBarCompactMode: Boolean = false,
    val carouselStyle: String = CarouselStyle.NO_PEEK,
    val libraryNavigationMode: String = LibraryNavigationMode.TAB_ROW,
    val launchTab: String = LaunchTab.HOME,
    val keepPlayingInBackground: Boolean = true,
    val resumeOnHeadsetReconnect: Boolean = false,
    val showQueueHistory: Boolean = true,
    val isCrossfadeEnabled: Boolean = false,
    val hiFiModeEnabled: Boolean = false,
    val hiFiModeDeviceSupported: Boolean = true,
    val crossfadeDuration: Int = 2000,
    val persistentShuffleEnabled: Boolean = false,
    val folderBackGestureNavigation: Boolean = true,
    val lyricsSourcePreference: LyricsSourcePreference = LyricsSourcePreference.EMBEDDED_FIRST,
    val autoScanLrcFiles: Boolean = false,
    val externalLyricsEnabled: Boolean = false,
    val externalArtistImagesEnabled: Boolean = false,
    val blockedDirectories: Set<String> = emptySet(),
    val appRebrandDialogShown: Boolean = false,
    val beta05CleanInstallDisclaimerDismissed: Boolean? = null,
    val fullPlayerLoadingTweaks: FullPlayerLoadingTweaks = FullPlayerLoadingTweaks(),
    val showPlayerFileInfo: Boolean = true,
    // Developer Options
    val albumArtQuality: AlbumArtQuality = AlbumArtQuality.MEDIUM,
    val albumArtCacheLimitMb: Int = 200,
    val tapBackgroundClosesPlayer: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val immersiveLyricsEnabled: Boolean = false,
    val immersiveLyricsTimeout: Long = 4000L,
    val useAnimatedLyrics: Boolean = false,
    val animatedLyricsBlurEnabled: Boolean = true,
    val animatedLyricsBlurStrength: Float = 2.5f,
    val backupInfoDismissed: Boolean = false,
    val isDataTransferInProgress: Boolean = false,
    val restorePlan: RestorePlan? = null,
    val backupHistory: List<BackupHistoryEntry> = emptyList(),
    val backupValidationErrors: List<ValidationError> = emptyList(),
    val isInspectingBackup: Boolean = false,
    val collagePattern: CollagePattern = CollagePattern.default,
    val collageAutoRotate: Boolean = false,
    val minSongDuration: Int = 10000,
    val minTracksPerAlbum: Int = 1,
    val replayGainEnabled: Boolean = false,
    val replayGainUseAlbumGain: Boolean = false
)

data class FailedSongInfo(
    val id: String,
    val title: String,
    val artist: String
)

data class LyricsRefreshProgress(
    val totalSongs: Int = 0,
    val currentCount: Int = 0,
    val savedCount: Int = 0,
    val notFoundCount: Int = 0,
    val skippedCount: Int = 0,
    val isComplete: Boolean = false,
    val failedSongs: List<FailedSongInfo> = emptyList()
) {
    val hasProgress: Boolean get() = totalSongs > 0
    val progress: Float get() = if (totalSongs > 0) currentCount.toFloat() / totalSongs else 0f
    val hasFailedSongs: Boolean get() = failedSongs.isNotEmpty()
}

// Helper classes for consolidated combine() collectors to reduce coroutine overhead
private sealed interface SettingsUiUpdate {
    data class Group1(
        val appRebrandDialogShown: Boolean,
        val appThemeMode: String,
        val playerThemePreference: String,
        val albumArtPaletteStyle: AlbumArtPaletteStyle,
        val albumArtColorAccuracy: Int,
        val mockGenresEnabled: Boolean,
        val navBarCornerRadius: Int,
        val navBarStyle: String,
        val navBarCompactMode: Boolean,
        val libraryNavigationMode: String,
        val carouselStyle: String,
        val launchTab: String,
        val showPlayerFileInfo: Boolean
    ) : SettingsUiUpdate
    
    data class Group2(
        val keepPlayingInBackground: Boolean,
        val resumeOnHeadsetReconnect: Boolean,
        val showQueueHistory: Boolean,
        val isCrossfadeEnabled: Boolean,
        val hiFiModeEnabled: Boolean,
        val crossfadeDuration: Int,
        val persistentShuffleEnabled: Boolean,
        val folderBackGestureNavigation: Boolean,
        val lyricsSourcePreference: LyricsSourcePreference,
        val autoScanLrcFiles: Boolean,
        val blockedDirectories: Set<String>,
        val hapticsEnabled: Boolean,
        val immersiveLyricsEnabled: Boolean,
        val immersiveLyricsTimeout: Long,
        val animatedLyricsBlurEnabled: Boolean,
        val animatedLyricsBlurStrength: Float
    ) : SettingsUiUpdate
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val colorSchemeProcessor: ColorSchemeProcessor,
    private val syncManager: SyncManager,
    private val lyricsRepository: LyricsRepository,
    private val musicRepository: MusicRepository,
    private val backupManager: BackupManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val fileExplorerStateHolder = FileExplorerStateHolder(userPreferencesRepository, viewModelScope, context)

    val currentPath = fileExplorerStateHolder.currentPath
    val currentDirectoryChildren = fileExplorerStateHolder.currentDirectoryChildren
    val blockedDirectories = fileExplorerStateHolder.blockedDirectories
    val availableStorages = fileExplorerStateHolder.availableStorages
    val selectedStorageIndex = fileExplorerStateHolder.selectedStorageIndex
    val isLoadingDirectories = fileExplorerStateHolder.isLoading
    val isExplorerPriming = fileExplorerStateHolder.isPrimingExplorer
    val isExplorerReady = fileExplorerStateHolder.isExplorerReady
    val isCurrentDirectoryResolved = fileExplorerStateHolder.isCurrentDirectoryResolved
    private var hasPendingDirectoryRuleChanges = false
    private var latestDirectoryRuleUpdateJob: Job? = null

    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val syncProgress: StateFlow<SyncProgress> = syncManager.syncProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SyncProgress()
        )

    private val _dataTransferEvents = MutableSharedFlow<String>()
    val dataTransferEvents: SharedFlow<String> = _dataTransferEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            backupManager.getBackupHistory().collect { history ->
                _uiState.update { it.copy(backupHistory = history) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.collagePatternFlow.collect { pattern ->
                _uiState.update { it.copy(collagePattern = pattern) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.collageAutoRotateFlow.collect { autoRotate ->
                _uiState.update { it.copy(collageAutoRotate = autoRotate) }
            }
        }
    }

    private val _dataTransferProgress = MutableStateFlow<BackupTransferProgressUpdate?>(null)
    val dataTransferProgress: StateFlow<BackupTransferProgressUpdate?> = _dataTransferProgress.asStateFlow()

    init {
        // One-time device capability check — result is cached inside HiFiCapabilityChecker
        _uiState.update {
            it.copy(
                hiFiModeDeviceSupported = HiFiCapabilityChecker.isSupported()
            )
        }

        // Consolidated collectors using combine() to reduce coroutine overhead
        // Instead of 20 separate coroutines, we use 2 combined flows
        
        // Group 1: Core UI settings (theme, navigation, appearance)
        viewModelScope.launch {
            combine<Any?, SettingsUiUpdate.Group1>(
                userPreferencesRepository.appRebrandDialogShownFlow,
                themePreferencesRepository.appThemeModeFlow,
                themePreferencesRepository.playerThemePreferenceFlow,
                themePreferencesRepository.albumArtPaletteStyleFlow,
                themePreferencesRepository.albumArtColorAccuracyFlow,
                userPreferencesRepository.mockGenresEnabledFlow,
                userPreferencesRepository.navBarCornerRadiusFlow,
                userPreferencesRepository.navBarStyleFlow,
                userPreferencesRepository.navBarCompactModeFlow,
                userPreferencesRepository.libraryNavigationModeFlow,
                userPreferencesRepository.carouselStyleFlow,
                userPreferencesRepository.launchTabFlow,
                userPreferencesRepository.showPlayerFileInfoFlow
            ) { values ->
                SettingsUiUpdate.Group1(
                    appRebrandDialogShown = values[0] as Boolean,
                    appThemeMode = values[1] as String,
                    playerThemePreference = values[2] as String,
                    albumArtPaletteStyle = values[3] as AlbumArtPaletteStyle,
                    albumArtColorAccuracy = values[4] as Int,
                    mockGenresEnabled = values[5] as Boolean,
                    navBarCornerRadius = values[6] as Int,
                    navBarStyle = values[7] as String,
                    navBarCompactMode = values[8] as Boolean,
                    libraryNavigationMode = values[9] as String,
                    carouselStyle = values[10] as String,
                    launchTab = values[11] as String,
                    showPlayerFileInfo = values[12] as Boolean
                )
            }.collect { update ->
                _uiState.update { state ->
                    state.copy(
                        appRebrandDialogShown = update.appRebrandDialogShown,
                        appThemeMode = update.appThemeMode,
                        playerThemePreference = update.playerThemePreference,
                        albumArtPaletteStyle = update.albumArtPaletteStyle,
                        albumArtColorAccuracy = update.albumArtColorAccuracy,
                        mockGenresEnabled = update.mockGenresEnabled,
                        navBarCornerRadius = update.navBarCornerRadius,
                        navBarStyle = update.navBarStyle,
                        navBarCompactMode = update.navBarCompactMode,
                        libraryNavigationMode = update.libraryNavigationMode,
                        carouselStyle = update.carouselStyle,
                        launchTab = update.launchTab,
                        showPlayerFileInfo = update.showPlayerFileInfo
                    )
                }
            }
        }
        
        // Group 2: Playback and system settings
        viewModelScope.launch {
            combine<Any?, SettingsUiUpdate.Group2>(
                userPreferencesRepository.keepPlayingInBackgroundFlow,
                userPreferencesRepository.resumeOnHeadsetReconnectFlow,
                userPreferencesRepository.showQueueHistoryFlow,
                userPreferencesRepository.isCrossfadeEnabledFlow,
                userPreferencesRepository.hiFiModeEnabledFlow,
                userPreferencesRepository.crossfadeDurationFlow,
                userPreferencesRepository.persistentShuffleEnabledFlow,
                userPreferencesRepository.folderBackGestureNavigationFlow,
                userPreferencesRepository.lyricsSourcePreferenceFlow,
                userPreferencesRepository.autoScanLrcFilesFlow,
                userPreferencesRepository.blockedDirectoriesFlow,
                userPreferencesRepository.hapticsEnabledFlow,
                userPreferencesRepository.immersiveLyricsEnabledFlow,
                userPreferencesRepository.immersiveLyricsTimeoutFlow,
                userPreferencesRepository.animatedLyricsBlurEnabledFlow,
                userPreferencesRepository.animatedLyricsBlurStrengthFlow
            ) { values ->
                SettingsUiUpdate.Group2(
                    keepPlayingInBackground = values[0] as Boolean,
                    resumeOnHeadsetReconnect = values[1] as Boolean,
                    showQueueHistory = values[2] as Boolean,
                    isCrossfadeEnabled = values[3] as Boolean,
                    hiFiModeEnabled = values[4] as Boolean,
                    crossfadeDuration = values[5] as Int,
                    persistentShuffleEnabled = values[6] as Boolean,
                    folderBackGestureNavigation = values[7] as Boolean,
                    lyricsSourcePreference = values[8] as LyricsSourcePreference,
                    autoScanLrcFiles = values[9] as Boolean,
                    blockedDirectories = @Suppress("UNCHECKED_CAST") (values[10] as Set<String>),
                    hapticsEnabled = values[11] as Boolean,
                    immersiveLyricsEnabled = values[12] as Boolean,
                    immersiveLyricsTimeout = values[13] as Long,
                    animatedLyricsBlurEnabled = values[14] as Boolean,
                    animatedLyricsBlurStrength = values[15] as Float
                )
            }.collect { update ->
                _uiState.update { state ->
                    state.copy(
                        keepPlayingInBackground = update.keepPlayingInBackground,
                        resumeOnHeadsetReconnect = update.resumeOnHeadsetReconnect,
                        showQueueHistory = update.showQueueHistory,
                        isCrossfadeEnabled = update.isCrossfadeEnabled,
                        hiFiModeEnabled = update.hiFiModeEnabled,
                        crossfadeDuration = update.crossfadeDuration,
                        persistentShuffleEnabled = update.persistentShuffleEnabled,
                        folderBackGestureNavigation = update.folderBackGestureNavigation,
                        lyricsSourcePreference = update.lyricsSourcePreference,
                        autoScanLrcFiles = update.autoScanLrcFiles,
                        blockedDirectories = update.blockedDirectories,
                        hapticsEnabled = update.hapticsEnabled,
                        immersiveLyricsEnabled = update.immersiveLyricsEnabled,
                        immersiveLyricsTimeout = update.immersiveLyricsTimeout,
                        animatedLyricsBlurEnabled = update.animatedLyricsBlurEnabled,
                        animatedLyricsBlurStrength = update.animatedLyricsBlurStrength
                    )
                }
            }
        }
        
        // Group 3: Remaining individual collectors (loading state, tweaks)
        viewModelScope.launch {
            userPreferencesRepository.fullPlayerLoadingTweaksFlow.collect { tweaks ->
                _uiState.update { it.copy(fullPlayerLoadingTweaks = tweaks) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.useAnimatedLyricsFlow.collect { enabled ->
                _uiState.update { it.copy(useAnimatedLyrics = enabled) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.externalLyricsEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(externalLyricsEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.externalArtistImagesEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(externalArtistImagesEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.backupInfoDismissedFlow.collect { dismissed ->
                _uiState.update { it.copy(backupInfoDismissed = dismissed) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.beta05CleanInstallDisclaimerDismissedFlow.collect { dismissed ->
                _uiState.update { it.copy(beta05CleanInstallDisclaimerDismissed = dismissed) }
            }
        }

        viewModelScope.launch {
            fileExplorerStateHolder.isLoading.collect { loading ->
                _uiState.update { it.copy(isLoadingDirectories = loading) }
            }
        }

        // Beta Features Collectors
        viewModelScope.launch {
            userPreferencesRepository.albumArtQualityFlow.collect { quality ->
                _uiState.update { it.copy(albumArtQuality = quality) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.albumArtCacheLimitMbFlow.collect { limitMb ->
                _uiState.update { it.copy(albumArtCacheLimitMb = limitMb) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.tapBackgroundClosesPlayerFlow.collect { enabled ->
                _uiState.update { it.copy(tapBackgroundClosesPlayer = enabled) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.minSongDurationFlow.collect { duration ->
                _uiState.update { it.copy(minSongDuration = duration) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.minTracksPerAlbumFlow.collect { minTracks ->
                _uiState.update { it.copy(minTracksPerAlbum = minTracks) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.replayGainEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(replayGainEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.replayGainUseAlbumGainFlow.collect { useAlbum ->
                _uiState.update { it.copy(replayGainUseAlbumGain = useAlbum) }
            }
        }
    }

    fun setAppRebrandDialogShown(wasShown: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAppRebrandDialogShown(wasShown)
        }
    }

    fun setBeta05CleanInstallDisclaimerDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBeta05CleanInstallDisclaimerDismissed(dismissed)
        }
    }

    fun toggleDirectoryAllowed(file: File) {
        hasPendingDirectoryRuleChanges = true
        latestDirectoryRuleUpdateJob = viewModelScope.launch {
            fileExplorerStateHolder.toggleDirectoryAllowed(file)
        }
    }

    fun applyPendingDirectoryRuleChanges() {
        if (!hasPendingDirectoryRuleChanges) return
        hasPendingDirectoryRuleChanges = false
        viewModelScope.launch {
            latestDirectoryRuleUpdateJob?.join()
            syncManager.forceRefresh()
        }
    }

    fun loadDirectory(file: File) {
        fileExplorerStateHolder.loadDirectory(file)
    }

    fun primeExplorer() {
        fileExplorerStateHolder.primeExplorerRoot()
    }

    fun openExplorer() {
        fileExplorerStateHolder.openExplorerRoot()
    }

    fun navigateUp() {
        fileExplorerStateHolder.navigateUp()
    }

    fun refreshExplorer() {
        fileExplorerStateHolder.refreshCurrentDirectory()
    }

    fun selectStorage(index: Int) {
        fileExplorerStateHolder.selectStorage(index)
    }

    fun refreshAvailableStorages() {
        fileExplorerStateHolder.refreshAvailableStorages()
    }

    fun isAtRoot(): Boolean = fileExplorerStateHolder.isAtRoot()

    fun explorerRoot(): File = fileExplorerStateHolder.rootDirectory()

    // Method to save the player theme preference
    fun setPlayerThemePreference(preference: String) {
        viewModelScope.launch {
            themePreferencesRepository.setPlayerThemePreference(preference)
        }
    }

    fun setAlbumArtPaletteStyle(style: AlbumArtPaletteStyle) {
        viewModelScope.launch {
            themePreferencesRepository.setAlbumArtPaletteStyle(style)
        }
    }

    fun setAlbumArtPaletteSettings(
        style: AlbumArtPaletteStyle,
        accuracyLevel: Int
    ) {
        viewModelScope.launch {
            themePreferencesRepository.setAlbumArtPaletteSettings(style, accuracyLevel)
        }
    }

    suspend fun getAlbumArtPalettePreview(
        uriString: String,
        style: AlbumArtPaletteStyle,
        accuracyLevel: Int
    ): ColorSchemePair? {
        return colorSchemeProcessor.getPreviewColorScheme(
            albumArtUri = uriString,
            paletteStyle = style,
            colorAccuracyLevel = accuracyLevel
        )
    }

    fun setCollagePattern(pattern: CollagePattern) {
        viewModelScope.launch {
            userPreferencesRepository.setCollagePattern(pattern)
        }
    }

    fun setCollageAutoRotate(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setCollageAutoRotate(enabled)
        }
    }

    fun setAppThemeMode(mode: String) {
        viewModelScope.launch {
            themePreferencesRepository.setAppThemeMode(mode)
        }
    }

    fun setNavBarStyle(style: String) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarStyle(style)
        }
    }

    fun setNavBarCompactMode(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarCompactMode(enabled)
        }
    }

    fun setLibraryNavigationMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setLibraryNavigationMode(mode)
        }
    }

    fun setCarouselStyle(style: String) {
        viewModelScope.launch {
            userPreferencesRepository.setCarouselStyle(style)
        }
    }

    fun setShowPlayerFileInfo(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setShowPlayerFileInfo(show)
        }
    }

    fun setLaunchTab(tab: String) {
        viewModelScope.launch {
            userPreferencesRepository.setLaunchTab(tab)
        }
    }

    fun setKeepPlayingInBackground(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setKeepPlayingInBackground(enabled)
        }
    }

    fun setResumeOnHeadsetReconnect(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setResumeOnHeadsetReconnect(enabled)
        }
    }

    fun setHiFiModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHiFiModeEnabled(enabled)
        }
    }

    fun setShowQueueHistory(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setShowQueueHistory(show)
        }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setCrossfadeEnabled(enabled)
        }
    }

    fun setCrossfadeDuration(duration: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setCrossfadeDuration(duration)
        }
    }

    val playbackSpeed: StateFlow<Float> = userPreferencesRepository.playbackSpeedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            userPreferencesRepository.setPlaybackSpeed(speed)
        }
    }

    fun setPersistentShuffleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setPersistentShuffleEnabled(enabled)
        }
    }

    fun setFolderBackGestureNavigation(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFolderBackGestureNavigation(enabled)
        }
    }

    fun setLyricsSourcePreference(preference: LyricsSourcePreference) {
        viewModelScope.launch {
            userPreferencesRepository.setLyricsSourcePreference(preference)
        }
    }

    fun setAutoScanLrcFiles(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoScanLrcFiles(enabled)
        }
    }

    fun setExternalLyricsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setExternalLyricsEnabled(enabled)
        }
    }

    fun setExternalArtistImagesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setExternalArtistImagesEnabled(enabled)
        }
    }

    fun setDelayAllFullPlayerContent(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayAllFullPlayerContent(enabled)
        }
    }

    fun setDelayAlbumCarousel(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayAlbumCarousel(enabled)
        }
    }

    fun setDelaySongMetadata(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelaySongMetadata(enabled)
        }
    }

    fun setDelayProgressBar(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayProgressBar(enabled)
        }
    }

    fun setDelayControls(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayControls(enabled)
        }
    }

    fun setFullPlayerPlaceholders(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerPlaceholders(enabled)
            if (!enabled) {
                userPreferencesRepository.setTransparentPlaceholders(false)
            }
        }
    }

    fun setTransparentPlaceholders(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setTransparentPlaceholders(enabled)
        }
    }

    fun setFullPlayerPlaceholdersOnClose(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerPlaceholdersOnClose(enabled)
        }
    }

    fun setFullPlayerSwitchOnDragRelease(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerSwitchOnDragRelease(enabled)
        }
    }

    fun setFullPlayerAppearThreshold(thresholdPercent: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerAppearThreshold(thresholdPercent)
        }
    }

    fun setFullPlayerCloseThreshold(thresholdPercent: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerCloseThreshold(thresholdPercent)
        }
    }

    fun setUseAnimatedLyrics(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setUseAnimatedLyrics(enabled)
        }
    }

    fun setAnimatedLyricsBlurEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAnimatedLyricsBlurEnabled(enabled)
        }
    }

    fun setAnimatedLyricsBlurStrength(strength: Float) {
        viewModelScope.launch {
            userPreferencesRepository.setAnimatedLyricsBlurStrength(strength)
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            syncManager.forceRefresh()
        }
    }




    /**
     * Performs a full library rescan - rescans all files from scratch.
     * Use when songs are missing or metadata is incorrect.
     */
    fun fullSyncLibrary() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            syncManager.fullSync()
        }
    }

    fun setMinSongDuration(durationMs: Int) {
        viewModelScope.launch {
            if (durationMs == _uiState.value.minSongDuration) return@launch
            userPreferencesRepository.setMinSongDuration(durationMs)
            // Trigger a library rescan so the change takes effect in the database
            syncManager.fullSync(deepScan = false)
        }
    }

    fun setMinTracksPerAlbum(minTracks: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setMinTracksPerAlbum(minTracks)
        }
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setReplayGainEnabled(enabled)
        }
    }

    fun setReplayGainUseAlbumGain(useAlbumGain: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setReplayGainUseAlbumGain(useAlbumGain)
        }
    }

    fun setImmersiveLyricsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setImmersiveLyricsEnabled(enabled)
        }
    }

    fun setImmersiveLyricsTimeout(timeout: Long) {
        viewModelScope.launch {
            userPreferencesRepository.setImmersiveLyricsTimeout(timeout)
        }
    }

    /**
     * Rebuilds local MediaStore-backed songs from scratch while preserving cloud sources.
     * Local imported lyrics, favorites, and user metadata edits are removed for rebuilt songs.
     * Use when local library data is corrupted or as a last resort.
     */
    fun rebuildDatabase() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            syncManager.rebuildDatabase()
        }
    }

    fun setNavBarCornerRadius(radius: Int) {
        viewModelScope.launch { userPreferencesRepository.setNavBarCornerRadius(radius) }
    }
    /**
     * Triggers a test crash to verify the crash handler is working correctly.
     * This should only be used for testing in Developer Options.
     */
    fun triggerTestCrash() {
        throw RuntimeException(context.getString(R.string.dev_test_crash_message))
    }

    fun resetSetupFlow() {
        viewModelScope.launch {
            userPreferencesRepository.setInitialSetupDone(false)
        }
    }

    // ===== Developer Options =====

    val albumArtQuality: StateFlow<AlbumArtQuality> = userPreferencesRepository.albumArtQualityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlbumArtQuality.MEDIUM)

    val useSmoothCorners: StateFlow<Boolean> = userPreferencesRepository.useSmoothCornersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val tapBackgroundClosesPlayer: StateFlow<Boolean> = userPreferencesRepository.tapBackgroundClosesPlayerFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAlbumArtQuality(quality: AlbumArtQuality) {
        viewModelScope.launch {
            userPreferencesRepository.setAlbumArtQuality(quality)
        }
    }

    fun setAlbumArtCacheLimitMb(limitMb: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setAlbumArtCacheLimitMb(limitMb)
            com.lostf1sh.pixelplayeross.utils.AlbumArtCacheManager.configuredCacheLimitMb = limitMb.toLong()
        }
    }

    fun setUseSmoothCorners(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setUseSmoothCorners(enabled)
        }
    }

    fun setTapBackgroundClosesPlayer(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setTapBackgroundClosesPlayer(enabled)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHapticsEnabled(enabled)
        }
    }

    fun setBackupInfoDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBackupInfoDismissed(dismissed)
        }
    }

    fun exportAppData(uri: Uri, sections: Set<BackupSection>) {
        if (sections.isEmpty() || _uiState.value.isDataTransferInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDataTransferInProgress = true) }
            _dataTransferProgress.value = BackupTransferProgressUpdate(
                operation = BackupOperationType.EXPORT,
                step = 0,
                totalSteps = 1,
                title = context.getString(R.string.backup_progress_preparing_backup),
                detail = context.getString(R.string.backup_progress_starting_backup_task),
            )
            val result = backupManager.export(uri, sections) { progress ->
                _dataTransferProgress.value = progress
            }
            result.fold(
                onSuccess = { _dataTransferEvents.emit(context.getString(R.string.data_exported_successfully)) },
                onFailure = {
                    _dataTransferEvents.emit(
                        context.getString(
                            R.string.export_failed_format,
                            it.localizedMessage ?: context.getString(R.string.error_unknown),
                        ),
                    )
                },
            )
            delay(300)
            _uiState.update { it.copy(isDataTransferInProgress = false) }
            _dataTransferProgress.value = null
        }
    }

    fun inspectBackupFile(uri: Uri) {
        if (_uiState.value.isInspectingBackup) return
        viewModelScope.launch {
            _uiState.update { it.copy(isInspectingBackup = true, backupValidationErrors = emptyList(), restorePlan = null) }
            val result = backupManager.inspectBackup(uri)
            result.fold(
                onSuccess = { plan ->
                    _uiState.update { it.copy(restorePlan = plan, isInspectingBackup = false) }
                },
                onFailure = { error ->
                    _dataTransferEvents.emit(
                        context.getString(
                            R.string.backup_invalid_format,
                            error.localizedMessage ?: context.getString(R.string.error_unknown),
                        ),
                    )
                    _uiState.update { it.copy(isInspectingBackup = false) }
                }
            )
        }
    }

    fun updateRestorePlanSelection(selectedModules: Set<BackupSection>) {
        _uiState.update { state ->
            state.restorePlan?.let { plan ->
                state.copy(restorePlan = plan.copy(selectedModules = selectedModules))
            } ?: state
        }
    }

    fun restoreFromPlan(uri: Uri) {
        val plan = _uiState.value.restorePlan ?: return
        if (plan.selectedModules.isEmpty() || _uiState.value.isDataTransferInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDataTransferInProgress = true) }
            _dataTransferProgress.value = BackupTransferProgressUpdate(
                operation = BackupOperationType.IMPORT,
                step = 0,
                totalSteps = 1,
                title = context.getString(R.string.backup_progress_preparing_restore),
                detail = context.getString(R.string.backup_progress_starting_task),
            )
            val result = backupManager.restore(uri, plan) { progress ->
                _dataTransferProgress.value = progress
            }
            when (result) {
                is RestoreResult.Success -> {
                    _dataTransferEvents.emit(context.getString(R.string.data_restored_successfully))
                    syncManager.sync()
                }
                is RestoreResult.PartialFailure -> {
                    val failedNames = result.failed.entries.joinToString { "${it.key.label}: ${it.value}" }
                    _dataTransferEvents.emit(
                        context.getString(R.string.restore_partial_unresolved_format, failedNames),
                    )
                    // Only sync when data was actually restored. Previously this also synced when
                    // !rolledBack was true, which conflated "something succeeded" with "rollback did
                    // not happen" and ran a full sync over a partially-written, non-rolled-back DB.
                    if (result.succeeded.isNotEmpty()) {
                        syncManager.sync()
                    }
                }
                is RestoreResult.TotalFailure -> {
                    _dataTransferEvents.emit(context.getString(R.string.restore_failed_format, result.error))
                }
            }
            delay(300)
            _uiState.update { it.copy(isDataTransferInProgress = false, restorePlan = null) }
            _dataTransferProgress.value = null
        }
    }

    fun clearRestorePlan() {
        _uiState.update { it.copy(restorePlan = null, backupValidationErrors = emptyList()) }
    }

    fun removeBackupHistoryEntry(entry: BackupHistoryEntry) {
        viewModelScope.launch {
            backupManager.removeBackupHistoryEntry(entry.uri)
        }
    }

}
