package com.lostf1sh.pixelplayeross.presentation.navigation

import android.net.Uri
import androidx.compose.runtime.Immutable


@Immutable
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Library : Screen("library")
    object Settings : Screen("settings")
    object Accounts : Screen("settings_accounts?highlightKey={highlightKey}") {
        fun createRoute(highlightKey: String? = null) =
            if (highlightKey != null) "settings_accounts?highlightKey=$highlightKey" else "settings_accounts"
    }
    object SettingsCategory : Screen("settings_category/{categoryId}?highlightKey={highlightKey}") {
        fun createRoute(categoryId: String, highlightKey: String? = null) =
            if (highlightKey != null) "settings_category/$categoryId?highlightKey=$highlightKey" else "settings_category/$categoryId"
    }
    object PaletteStyle : Screen("palette_style_settings?highlightKey={highlightKey}") {
        fun createRoute(highlightKey: String? = null) =
            if (highlightKey != null) "palette_style_settings?highlightKey=$highlightKey" else "palette_style_settings"
    }
    object Experimental : Screen("experimental_settings?highlightKey={highlightKey}") {
        fun createRoute(highlightKey: String? = null) =
            if (highlightKey != null) "experimental_settings?highlightKey=$highlightKey" else "experimental_settings"
    }
    object NavBarCrRad : Screen("nav_bar_corner_radius?highlightKey={highlightKey}") {
        fun createRoute(highlightKey: String? = null) =
            if (highlightKey != null) "nav_bar_corner_radius?highlightKey=$highlightKey" else "nav_bar_corner_radius"
    }
    object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist_detail/$playlistId"
    }

    object  DailyMixScreen : Screen("daily_mix")
    object RecentlyPlayed : Screen("recently_played")
    object Stats : Screen("stats")
    object Duplicates : Screen("duplicates?highlightKey={highlightKey}") {
        fun createRoute(highlightKey: String? = null) =
            if (highlightKey != null) "duplicates?highlightKey=$highlightKey" else "duplicates"
    }
    object GenreDetail : Screen("genre_detail/{genreId}") {
        fun createRoute(genreId: String) = "genre_detail/$genreId"
    }
    object DJSpace : Screen("dj_space")
    object AlbumDetail : Screen("album_detail/{albumId}") {
        fun createRoute(albumId: Long) = "album_detail/$albumId"
    }

    object ArtistDetail : Screen("artist_detail/{artistId}") {
        fun createRoute(artistId: Long) = "artist_detail/$artistId"
    }

    object EditTransition : Screen("edit_transition?playlistId={playlistId}") {
        fun createRoute(playlistId: String?) =
            if (playlistId != null) "edit_transition?playlistId=$playlistId" else "edit_transition"
    }

    object About : Screen("about")
    object EasterEgg : Screen("easter_egg")

    object ArtistSettings : Screen("artist_settings?highlightKey={highlightKey}") {
        fun createRoute(highlightKey: String? = null) =
            if (highlightKey != null) "artist_settings?highlightKey=$highlightKey" else "artist_settings"
    }
    object DelimiterConfig : Screen("delimiter_config")
    object WordDelimiterConfig : Screen("word_delimiter_config")
    object Equalizer : Screen("equalizer?highlightKey={highlightKey}") {
        fun createRoute(highlightKey: String? = null) =
            if (highlightKey != null) "equalizer?highlightKey=$highlightKey" else "equalizer"
    }
    object DeviceCapabilities : Screen("device_capabilities?highlightKey={highlightKey}") {
        fun createRoute(highlightKey: String? = null) =
            if (highlightKey != null) "device_capabilities?highlightKey=$highlightKey" else "device_capabilities"
    }
    object NavidromeDashboard : Screen("navidrome_dashboard")
    object JellyfinDashboard : Screen("jellyfin_dashboard")
    object CloudDownloads : Screen("cloud_downloads")

    object AudioBookmarks : Screen("audio_bookmarks")
    object AudioBookmarkFolder : Screen("audio_bookmarks/{songId}") {
        fun createRoute(songId: String) = "audio_bookmarks/${Uri.encode(songId)}"
    }

}
