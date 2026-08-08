package com.lostf1sh.pixelplayeross.presentation.settings.search

import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.model.SettingsCategory
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen

/**
 * Flat index of every setting reachable from the settings tree, used to power settings search.
 *
 * Each entry mirrors a row rendered by `SettingsCategoryScreen`; `itemKey` is the contract
 * between the two — search navigates with it, and the screen pulses the row tagged with the
 * same key via `Modifier.settingHighlight`. Adding a row to a category screen means adding it
 * here too, otherwise it stays unsearchable.
 *
 * `keywordsStatic` carries the words users actually type but that do not appear in the label
 * (synonyms, the old name of a feature, the hardware it affects).
 */
object SettingsRegistry {

    private val libraryRoute = Screen.SettingsCategory.createRoute(SettingsCategory.LIBRARY.id)
    private val appearanceRoute = Screen.SettingsCategory.createRoute(SettingsCategory.APPEARANCE.id)
    private val playbackRoute = Screen.SettingsCategory.createRoute(SettingsCategory.PLAYBACK.id)
    private val behaviorRoute = Screen.SettingsCategory.createRoute(SettingsCategory.BEHAVIOR.id)
    private val backupRoute = Screen.SettingsCategory.createRoute(SettingsCategory.BACKUP_RESTORE.id)
    private val developerRoute = Screen.SettingsCategory.createRoute(SettingsCategory.DEVELOPER.id)

    val allSettings: List<SettingSpec> by lazy {
        buildList {
            addAll(librarySettings)
            addAll(appearanceSettings)
            addAll(playbackSettings)
            addAll(behaviorSettings)
            addAll(backupSettings)
            addAll(developerSettings)
            addAll(standaloneSettings)
        }
    }

    private val librarySettings = listOf(
        SettingSpec(
            id = "library_excluded_directories",
            itemKey = "item_library_excluded_directories",
            titleRes = R.string.setcat_excluded_directories_title,
            subtitleRes = R.string.setcat_excluded_directories_subtitle,
            category = SettingsCategory.LIBRARY,
            subscreenRoute = libraryRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("folders", "exclude", "ignore", "blocked", "hidden", "scan")
        ),
        SettingSpec(
            id = "library_artists",
            itemKey = "item_library_artists",
            titleRes = R.string.setcat_artists_title,
            subtitleRes = R.string.setcat_artists_subtitle,
            category = SettingsCategory.LIBRARY,
            subscreenRoute = libraryRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("artist", "separator", "delimiter", "split", "featuring")
        ),
        SettingSpec(
            id = "library_min_duration",
            itemKey = "item_library_min_duration",
            titleRes = R.string.setcat_min_song_duration,
            category = SettingsCategory.LIBRARY,
            subscreenRoute = libraryRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("duration", "short", "filter", "seconds", "length", "skip clips")
        ),
        SettingSpec(
            id = "library_min_tracks",
            itemKey = "item_library_min_tracks",
            titleRes = R.string.setcat_min_tracks_per_album,
            category = SettingsCategory.LIBRARY,
            subscreenRoute = libraryRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("album", "tracks", "minimum", "singles", "filter")
        ),
        SettingSpec(
            id = "library_art_cache_limit",
            itemKey = "item_library_art_cache_limit",
            titleRes = R.string.setcat_album_art_cache_limit,
            category = SettingsCategory.LIBRARY,
            subscreenRoute = libraryRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("cache", "storage", "artwork", "cover", "megabytes", "space")
        ),
        SettingSpec(
            id = "library_refresh",
            itemKey = "item_library_refresh",
            titleRes = R.string.setcat_sync_scanning,
            subtitleRes = R.string.setcat_sync_full_rescan_label,
            category = SettingsCategory.LIBRARY,
            subscreenRoute = libraryRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("rescan", "refresh", "sync", "rebuild", "reindex", "missing songs")
        ),
        SettingSpec(
            id = "library_auto_scan_lrc",
            itemKey = "item_library_auto_scan_lrc",
            titleRes = R.string.setcat_auto_scan_lrc_title,
            subtitleRes = R.string.setcat_auto_scan_lrc_subtitle,
            category = SettingsCategory.LIBRARY,
            subscreenRoute = libraryRoute,
            type = SettingType.SWITCH,
            keywordsStatic = listOf("lrc", "lyrics", "scan", "local lyrics"),
            getValue = { it.autoScanLrcFiles },
            onToggle = { vm, value -> vm.setAutoScanLrcFiles(value) }
        ),
        SettingSpec(
            id = "library_find_duplicates",
            itemKey = "item_library_find_duplicates",
            titleRes = R.string.setcat_find_duplicates_title,
            subtitleRes = R.string.setcat_find_duplicates_subtitle,
            category = SettingsCategory.LIBRARY,
            subscreenRoute = libraryRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("duplicate", "copies", "same song", "cleanup")
        ),
        SettingSpec(
            id = "library_external_lyrics",
            itemKey = "item_library_external_lyrics",
            titleRes = R.string.setcat_external_lyrics_title,
            subtitleRes = R.string.setcat_external_lyrics_subtitle,
            category = SettingsCategory.LIBRARY,
            subscreenRoute = libraryRoute,
            type = SettingType.SWITCH,
            keywordsStatic = listOf("lrclib", "online lyrics", "internet", "network", "fetch"),
            getValue = { it.externalLyricsEnabled },
            onToggle = { vm, value -> vm.setExternalLyricsEnabled(value) }
        ),
        SettingSpec(
            id = "library_external_artist_images",
            itemKey = "item_library_external_artist_images",
            titleRes = R.string.setcat_external_artist_images_title,
            subtitleRes = R.string.setcat_external_artist_images_subtitle,
            category = SettingsCategory.LIBRARY,
            subscreenRoute = libraryRoute,
            type = SettingType.SWITCH,
            keywordsStatic = listOf("deezer", "artist image", "photo", "online", "artwork"),
            getValue = { it.externalArtistImagesEnabled },
            onToggle = { vm, value -> vm.setExternalArtistImagesEnabled(value) }
        ),
        SettingSpec(
            id = "library_lyrics_source_priority",
            itemKey = "item_library_lyrics_source_priority",
            titleRes = R.string.setcat_lyrics_source_priority_label,
            subtitleRes = R.string.setcat_lyrics_source_priority_desc,
            category = SettingsCategory.LIBRARY,
            subscreenRoute = libraryRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("lyrics", "priority", "embedded", "order", "source")
        ),
        SettingSpec(
            id = "library_reset_imported_lyrics",
            itemKey = "item_library_reset_imported_lyrics",
            titleRes = R.string.setcat_reset_imported_lyrics_title,
            subtitleRes = R.string.setcat_reset_imported_lyrics_subtitle,
            category = SettingsCategory.LIBRARY,
            subscreenRoute = libraryRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("lyrics", "reset", "clear", "delete", "imported")
        )
    )

    private val appearanceSettings = listOf(
        SettingSpec(
            id = "appearance_app_theme",
            itemKey = "item_appearance_app_theme",
            titleRes = R.string.setcat_app_theme_label,
            subtitleRes = R.string.setcat_app_theme_desc,
            category = SettingsCategory.APPEARANCE,
            subscreenRoute = appearanceRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("dark mode", "light", "night", "theme", "system")
        ),
        SettingSpec(
            id = "appearance_smooth_corners",
            itemKey = "item_appearance_smooth_corners",
            titleRes = R.string.setcat_smooth_corners_title,
            subtitleRes = R.string.setcat_smooth_corners_subtitle,
            category = SettingsCategory.APPEARANCE,
            subscreenRoute = appearanceRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("squircle", "rounded", "corners", "shape")
        ),
        SettingSpec(
            id = "appearance_player_theme",
            itemKey = "item_appearance_player_theme",
            titleRes = R.string.setcat_player_theme_label,
            subtitleRes = R.string.setcat_player_theme_desc,
            category = SettingsCategory.APPEARANCE,
            subscreenRoute = appearanceRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("player", "album art", "dynamic color", "monet", "colours")
        ),
        SettingSpec(
            id = "appearance_show_player_file_info",
            itemKey = "item_appearance_show_player_file_info",
            titleRes = R.string.setcat_show_player_file_info_title,
            subtitleRes = R.string.setcat_show_player_file_info_subtitle,
            category = SettingsCategory.APPEARANCE,
            subscreenRoute = appearanceRoute,
            type = SettingType.SWITCH,
            keywordsStatic = listOf("bitrate", "format", "codec", "file info", "flac", "quality"),
            getValue = { it.showPlayerFileInfo },
            onToggle = { vm, value -> vm.setShowPlayerFileInfo(value) }
        ),
        SettingSpec(
            id = "appearance_album_art_palette",
            itemKey = "item_appearance_album_art_palette",
            titleRes = R.string.setcat_album_art_palette_title,
            category = SettingsCategory.APPEARANCE,
            subscreenRoute = Screen.PaletteStyle.route,
            type = SettingType.NAVIGABLE_CARD,

            supportsHighlight = false,
            keywordsStatic = listOf("palette", "colors", "album art", "extraction", "vibrant")
        ),
        SettingSpec(
            id = "appearance_carousel_style",
            itemKey = "item_appearance_carousel_style",
            titleRes = R.string.setcat_carousel_style_label,
            subtitleRes = R.string.setcat_carousel_style_desc,
            category = SettingsCategory.APPEARANCE,
            subscreenRoute = appearanceRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("carousel", "peek", "swipe", "player art")
        ),
        SettingSpec(
            id = "appearance_collage_pattern",
            itemKey = "item_appearance_collage_pattern",
            titleRes = R.string.setcat_collage_pattern_label,
            subtitleRes = R.string.setcat_collage_pattern_desc,
            category = SettingsCategory.APPEARANCE,
            subscreenRoute = appearanceRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("collage", "home", "grid", "mosaic", "pattern")
        ),
        SettingSpec(
            id = "appearance_collage_auto_rotate",
            itemKey = "item_appearance_collage_auto_rotate",
            titleRes = R.string.setcat_auto_rotate_patterns_title,
            subtitleRes = R.string.setcat_auto_rotate_patterns_subtitle,
            category = SettingsCategory.APPEARANCE,
            subscreenRoute = appearanceRoute,
            type = SettingType.SWITCH,
            keywordsStatic = listOf("collage", "rotate", "shuffle", "home", "pattern"),
            getValue = { it.collageAutoRotate },
            onToggle = { vm, value -> vm.setCollageAutoRotate(value) }
        ),
        SettingSpec(
            id = "appearance_navbar_style",
            itemKey = "item_appearance_navbar_style",
            titleRes = R.string.setcat_navbar_style_label,
            subtitleRes = R.string.setcat_navbar_style_desc,
            category = SettingsCategory.APPEARANCE,
            subscreenRoute = appearanceRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("navigation bar", "navbar", "bottom bar", "full width")
        ),
        SettingSpec(
            id = "appearance_navbar_compact",
            itemKey = "item_appearance_navbar_compact",
            titleRes = R.string.setcat_compact_mode_title,
            subtitleRes = R.string.setcat_compact_mode_subtitle,
            category = SettingsCategory.APPEARANCE,
            subscreenRoute = appearanceRoute,
            type = SettingType.SWITCH,
            keywordsStatic = listOf("navbar", "compact", "small", "labels", "bottom bar"),
            getValue = { it.navBarCompactMode },
            onToggle = { vm, value -> vm.setNavBarCompactMode(value) }
        ),
        SettingSpec(
            id = "appearance_navbar_corner",
            itemKey = "item_appearance_navbar_corner",
            titleRes = R.string.setcat_navbar_corner_title,
            subtitleRes = R.string.setcat_navbar_corner_subtitle,
            category = SettingsCategory.APPEARANCE,
            subscreenRoute = Screen.NavBarCrRad.route,
            type = SettingType.NAVIGABLE_CARD,

            supportsHighlight = false,
            keywordsStatic = listOf("navbar", "corner", "radius", "rounded")
        ),
        SettingSpec(
            id = "appearance_immersive_lyrics",
            itemKey = "item_appearance_immersive_lyrics",
            titleRes = R.string.setcat_immersive_lyrics_title,
            subtitleRes = R.string.setcat_immersive_lyrics_subtitle,
            category = SettingsCategory.APPEARANCE,
            subscreenRoute = appearanceRoute,
            type = SettingType.SWITCH,
            keywordsStatic = listOf("lyrics", "immersive", "fullscreen", "hide controls"),
            getValue = { it.immersiveLyricsEnabled },
            onToggle = { vm, value -> vm.setImmersiveLyricsEnabled(value) }
        ),
        SettingSpec(
            id = "appearance_default_tab",
            itemKey = "item_appearance_default_tab",
            titleRes = R.string.setcat_default_tab_label,
            subtitleRes = R.string.setcat_default_tab_desc,
            category = SettingsCategory.APPEARANCE,
            subscreenRoute = appearanceRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("startup", "launch", "start screen", "default tab", "home")
        ),
        SettingSpec(
            id = "appearance_library_navigation",
            itemKey = "item_appearance_library_navigation",
            titleRes = R.string.setcat_library_navigation_label,
            subtitleRes = R.string.setcat_library_navigation_desc,
            category = SettingsCategory.APPEARANCE,
            subscreenRoute = appearanceRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("library", "tabs", "pill", "navigation")
        )
    )

    private val playbackSettings = listOf(
        SettingSpec(
            id = "playback_keep_playing_bg",
            itemKey = "item_playback_keep_playing_bg",
            titleRes = R.string.setcat_keep_playing_label,
            subtitleRes = R.string.setcat_keep_playing_desc,
            category = SettingsCategory.PLAYBACK,
            subscreenRoute = playbackRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("background", "keep playing", "swipe away", "stop", "kill")
        ),
        SettingSpec(
            id = "playback_replaygain",
            itemKey = "item_playback_replaygain",
            titleRes = R.string.setcat_replaygain_enable_title,
            subtitleRes = R.string.setcat_replaygain_enable_subtitle,
            category = SettingsCategory.PLAYBACK,
            subscreenRoute = playbackRoute,
            type = SettingType.SWITCH,
            keywordsStatic = listOf("replaygain", "volume", "normalization", "loudness", "gain"),
            getValue = { it.replayGainEnabled },
            onToggle = { vm, value -> vm.setReplayGainEnabled(value) }
        ),
        SettingSpec(
            id = "playback_gain_mode",
            itemKey = "item_playback_gain_mode",
            titleRes = R.string.setcat_gain_mode_label,
            subtitleRes = R.string.setcat_gain_mode_desc,
            category = SettingsCategory.PLAYBACK,
            subscreenRoute = playbackRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("replaygain", "track gain", "album gain", "volume")
        ),
        SettingSpec(
            id = "playback_headphones_resume",
            itemKey = "item_playback_headphones_resume",
            titleRes = R.string.setcat_headphones_resume_title,
            subtitleRes = R.string.setcat_headphones_resume_subtitle,
            category = SettingsCategory.PLAYBACK,
            subscreenRoute = playbackRoute,
            type = SettingType.SWITCH,
            keywordsStatic = listOf("headphones", "bluetooth", "earbuds", "resume", "reconnect"),
            getValue = { it.resumeOnHeadsetReconnect },
            onToggle = { vm, value -> vm.setResumeOnHeadsetReconnect(value) }
        ),
        SettingSpec(
            id = "playback_crossfade",
            itemKey = "item_playback_crossfade",
            titleRes = R.string.setcat_crossfade_label,
            subtitleRes = R.string.setcat_crossfade_desc,
            category = SettingsCategory.PLAYBACK,
            subscreenRoute = playbackRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("crossfade", "fade", "transition", "gapless", "blend")
        ),
        SettingSpec(
            id = "playback_crossfade_duration",
            itemKey = "item_playback_crossfade_duration",
            titleRes = R.string.setcat_crossfade_duration,
            category = SettingsCategory.PLAYBACK,
            subscreenRoute = playbackRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("crossfade", "duration", "seconds", "fade length")
        ),
        SettingSpec(
            id = "playback_speed",
            itemKey = "item_playback_speed",
            titleRes = R.string.setcat_playback_speed,
            category = SettingsCategory.PLAYBACK,
            subscreenRoute = playbackRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("speed", "tempo", "faster", "slower", "rate", "pitch")
        ),
        SettingSpec(
            id = "playback_hifi_mode",
            itemKey = "item_playback_hifi_mode",
            titleRes = R.string.setcat_hifi_mode_title,
            subtitleRes = R.string.setcat_hifi_mode_subtitle_supported,
            category = SettingsCategory.PLAYBACK,
            subscreenRoute = playbackRoute,
            type = SettingType.SWITCH,
            keywordsStatic = listOf("hi-fi", "hifi", "hi res", "bit perfect", "offload", "quality"),
            getValue = { it.hiFiModeEnabled },
            onToggle = { vm, value -> vm.setHiFiModeEnabled(value) }
        ),
        SettingSpec(
            id = "playback_persistent_shuffle",
            itemKey = "item_playback_persistent_shuffle",
            titleRes = R.string.setcat_persistent_shuffle_title,
            subtitleRes = R.string.setcat_persistent_shuffle_subtitle,
            category = SettingsCategory.PLAYBACK,
            subscreenRoute = playbackRoute,
            type = SettingType.SWITCH,
            keywordsStatic = listOf("shuffle", "random", "remember", "persistent"),
            getValue = { it.persistentShuffleEnabled },
            onToggle = { vm, value -> vm.setPersistentShuffleEnabled(value) }
        ),
        SettingSpec(
            id = "playback_show_queue_history",
            itemKey = "item_playback_show_queue_history",
            titleRes = R.string.setcat_show_queue_history_title,
            subtitleRes = R.string.setcat_show_queue_history_subtitle,
            category = SettingsCategory.PLAYBACK,
            subscreenRoute = playbackRoute,
            type = SettingType.SWITCH,
            keywordsStatic = listOf("queue", "history", "played", "previous"),
            getValue = { it.showQueueHistory },
            onToggle = { vm, value -> vm.setShowQueueHistory(value) }
        )
    )

    private val behaviorSettings = listOf(
        SettingSpec(
            id = "behavior_folder_back_gesture",
            itemKey = "item_behavior_folder_back_gesture",
            titleRes = R.string.setcat_folder_back_gesture_title,
            subtitleRes = R.string.setcat_folder_back_gesture_subtitle,
            category = SettingsCategory.BEHAVIOR,
            subscreenRoute = behaviorRoute,
            type = SettingType.SWITCH,
            keywordsStatic = listOf("back", "gesture", "folders", "navigation", "up"),
            getValue = { it.folderBackGestureNavigation },
            onToggle = { vm, value -> vm.setFolderBackGestureNavigation(value) }
        ),
        SettingSpec(
            id = "behavior_tap_bg_closes",
            itemKey = "item_behavior_tap_bg_closes",
            titleRes = R.string.setcat_tap_bg_closes_title,
            subtitleRes = R.string.setcat_tap_bg_closes_subtitle,
            category = SettingsCategory.BEHAVIOR,
            subscreenRoute = behaviorRoute,
            type = SettingType.SWITCH,
            keywordsStatic = listOf("tap", "dismiss", "close player", "background", "gesture"),
            getValue = { it.tapBackgroundClosesPlayer },
            onToggle = { vm, value -> vm.setTapBackgroundClosesPlayer(value) }
        ),
        SettingSpec(
            id = "behavior_haptics",
            itemKey = "item_behavior_haptics",
            titleRes = R.string.setcat_haptic_feedback_title,
            subtitleRes = R.string.setcat_haptic_feedback_subtitle,
            category = SettingsCategory.BEHAVIOR,
            subscreenRoute = behaviorRoute,
            type = SettingType.SWITCH,
            keywordsStatic = listOf("haptics", "vibration", "vibrate", "feedback", "buzz"),
            getValue = { it.hapticsEnabled },
            onToggle = { vm, value -> vm.setHapticsEnabled(value) }
        )
    )

    private val backupSettings = listOf(
        SettingSpec(
            id = "backup_export",
            itemKey = "item_backup_export",
            titleRes = R.string.setcat_export_backup_title,
            category = SettingsCategory.BACKUP_RESTORE,
            subscreenRoute = backupRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("backup", "export", "save", "pxpl", "playlists", "transfer")
        ),
        SettingSpec(
            id = "backup_import",
            itemKey = "item_backup_import",
            titleRes = R.string.setcat_import_backup_title,
            subtitleRes = R.string.setcat_import_backup_subtitle,
            category = SettingsCategory.BACKUP_RESTORE,
            subscreenRoute = backupRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("restore", "import", "backup", "recover", "migrate")
        )
    )

    private val developerSettings = listOf(
        SettingSpec(
            id = "developer_experimental",
            itemKey = "item_developer_experimental",
            titleRes = R.string.setcat_experimental_title,
            subtitleRes = R.string.setcat_experimental_subtitle,
            category = SettingsCategory.DEVELOPER,
            subscreenRoute = Screen.Experimental.route,
            type = SettingType.NAVIGABLE_CARD,

            supportsHighlight = false,
            keywordsStatic = listOf("experimental", "beta", "flags", "labs")
        ),
        SettingSpec(
            id = "developer_test_setup",
            itemKey = "item_developer_test_setup",
            titleRes = R.string.setcat_test_setup_title,
            subtitleRes = R.string.setcat_test_setup_subtitle,
            category = SettingsCategory.DEVELOPER,
            subscreenRoute = developerRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("setup", "onboarding", "first run", "wizard", "reset")
        ),
        SettingSpec(
            id = "developer_force_daily_mix",
            itemKey = "item_developer_force_daily_mix",
            titleRes = R.string.setcat_force_daily_mix_title,
            subtitleRes = R.string.setcat_force_daily_mix_subtitle,
            category = SettingsCategory.DEVELOPER,
            subscreenRoute = developerRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("daily mix", "regenerate", "refresh", "recommendations")
        ),
        SettingSpec(
            id = "developer_force_stats",
            itemKey = "item_developer_force_stats",
            titleRes = R.string.setcat_force_stats_title,
            subtitleRes = R.string.setcat_force_stats_subtitle,
            category = SettingsCategory.DEVELOPER,
            subscreenRoute = developerRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("stats", "statistics", "listening", "recalculate")
        ),
        SettingSpec(
            id = "developer_force_palette",
            itemKey = "item_developer_force_palette",
            titleRes = R.string.setcat_force_palette_title,
            subtitleRes = R.string.setcat_force_palette_subtitle,
            category = SettingsCategory.DEVELOPER,
            subscreenRoute = developerRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("palette", "colors", "regenerate", "album art")
        ),
        SettingSpec(
            id = "developer_trigger_crash",
            itemKey = "item_developer_trigger_crash",
            titleRes = R.string.setcat_trigger_crash_title,
            subtitleRes = R.string.setcat_trigger_crash_subtitle,
            category = SettingsCategory.DEVELOPER,
            subscreenRoute = developerRoute,
            type = SettingType.NAVIGABLE_CARD,
            keywordsStatic = listOf("crash", "test", "diagnostics", "exception")
        )
    )

    /** Categories that open a dedicated screen instead of a `SettingsCategoryScreen` page. */
    private val standaloneSettings = listOf(
        SettingSpec(
            id = "equalizer",
            itemKey = "item_equalizer",
            titleRes = R.string.settings_category_equalizer_title,
            subtitleRes = R.string.settings_category_equalizer_subtitle,
            category = SettingsCategory.EQUALIZER,
            subscreenRoute = Screen.Equalizer.route,
            type = SettingType.NAVIGABLE_CARD,

            supportsHighlight = false,
            keywordsStatic = listOf("equalizer", "eq", "bass", "treble", "preset", "audio effects")
        ),
        SettingSpec(
            id = "device_capabilities",
            itemKey = "item_device_capabilities",
            titleRes = R.string.settings_category_device_capabilities_title,
            subtitleRes = R.string.settings_category_device_capabilities_subtitle,
            category = SettingsCategory.DEVICE_CAPABILITIES,
            subscreenRoute = Screen.DeviceCapabilities.route,
            type = SettingType.NAVIGABLE_CARD,

            supportsHighlight = false,
            keywordsStatic = listOf("device", "capabilities", "codec", "hardware", "support", "offload")
        ),
        SettingSpec(
            id = "accounts",
            itemKey = "item_accounts",
            titleRes = R.string.settings_accounts_row_title,
            subtitleRes = R.string.settings_accounts_row_subtitle,
            category = SettingsCategory.LIBRARY,
            subscreenRoute = Screen.Accounts.route,
            type = SettingType.NAVIGABLE_CARD,

            supportsHighlight = false,
            keywordsStatic = listOf(
                "account", "login", "navidrome", "subsonic", "jellyfin",
                "listenbrainz", "scrobble", "server", "sync"
            )
        ),
        SettingSpec(
            id = "about",
            itemKey = "item_about",
            titleRes = R.string.setcat_about_pixelplayer_title,
            subtitleRes = R.string.setcat_about_pixelplayer_subtitle,
            category = SettingsCategory.ABOUT,
            subscreenRoute = Screen.About.route,
            type = SettingType.NAVIGABLE_CARD,

            supportsHighlight = false,
            keywordsStatic = listOf("about", "version", "license", "credits", "github", "source")
        )
    )
}
