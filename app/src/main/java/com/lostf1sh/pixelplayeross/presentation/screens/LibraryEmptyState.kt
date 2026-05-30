package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.LibraryTabId
import com.lostf1sh.pixelplayeross.data.model.StorageFilter
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans

private data class LibraryEmptySpec(
    val iconRes: Int,
    val titleRes: Int,
    val subtitleRes: Int
)

private fun libraryEmptySpec(
    tabId: LibraryTabId,
    storageFilter: StorageFilter
): LibraryEmptySpec {
    return when (tabId) {
        LibraryTabId.SONGS -> when (storageFilter) {
            StorageFilter.ALL -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_music_off_24,
                titleRes = R.string.lib_empty_songs_all_title,
                subtitleRes = R.string.lib_empty_songs_all_subtitle
            )
            StorageFilter.OFFLINE -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_music_off_24,
                titleRes = R.string.lib_empty_songs_offline_title,
                subtitleRes = R.string.lib_empty_songs_offline_subtitle
            )
            StorageFilter.ONLINE -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_music_off_24,
                titleRes = R.string.lib_empty_songs_online_title,
                subtitleRes = R.string.lib_empty_songs_online_subtitle
            )
        }

        LibraryTabId.ALBUMS -> when (storageFilter) {
            StorageFilter.ALL -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_album_24,
                titleRes = R.string.lib_empty_albums_all_title,
                subtitleRes = R.string.lib_empty_albums_all_subtitle
            )
            StorageFilter.OFFLINE -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_album_24,
                titleRes = R.string.lib_empty_albums_offline_title,
                subtitleRes = R.string.lib_empty_albums_offline_subtitle
            )
            StorageFilter.ONLINE -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_album_24,
                titleRes = R.string.lib_empty_albums_online_title,
                subtitleRes = R.string.lib_empty_albums_online_subtitle
            )
        }

        LibraryTabId.ARTISTS -> when (storageFilter) {
            StorageFilter.ALL -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_artist_24,
                titleRes = R.string.lib_empty_artists_all_title,
                subtitleRes = R.string.lib_empty_artists_all_subtitle
            )
            StorageFilter.OFFLINE -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_artist_24,
                titleRes = R.string.lib_empty_artists_offline_title,
                subtitleRes = R.string.lib_empty_artists_offline_subtitle
            )
            StorageFilter.ONLINE -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_artist_24,
                titleRes = R.string.lib_empty_artists_online_title,
                subtitleRes = R.string.lib_empty_artists_online_subtitle
            )
        }

        LibraryTabId.LIKED -> when (storageFilter) {
            StorageFilter.ALL -> LibraryEmptySpec(
                iconRes = R.drawable.round_favorite_24,
                titleRes = R.string.lib_empty_liked_all_title,
                subtitleRes = R.string.lib_empty_liked_all_subtitle
            )
            StorageFilter.OFFLINE -> LibraryEmptySpec(
                iconRes = R.drawable.round_favorite_24,
                titleRes = R.string.lib_empty_liked_offline_title,
                subtitleRes = R.string.lib_empty_liked_offline_subtitle
            )
            StorageFilter.ONLINE -> LibraryEmptySpec(
                iconRes = R.drawable.round_favorite_24,
                titleRes = R.string.lib_empty_liked_online_title,
                subtitleRes = R.string.lib_empty_liked_online_subtitle
            )
        }

        LibraryTabId.FOLDERS -> LibraryEmptySpec(
            iconRes = R.drawable.ic_folder,
            titleRes = R.string.lib_empty_folders_title,
            subtitleRes = R.string.lib_empty_folders_subtitle
        )

        LibraryTabId.PLAYLISTS -> LibraryEmptySpec(
            iconRes = R.drawable.rounded_playlist_play_24,
            titleRes = R.string.lib_empty_playlists_title,
            subtitleRes = R.string.lib_empty_playlists_subtitle
        )
    }
}

@Composable
internal fun LibraryExpressiveEmptyState(
    tabId: LibraryTabId,
    storageFilter: StorageFilter,
    bottomBarHeight: Dp,
    modifier: Modifier = Modifier
) {
    val spec = remember(tabId, storageFilter) { libraryEmptySpec(tabId, storageFilter) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = 28.dp,
                end = 28.dp,
                bottom = bottomBarHeight + MiniPlayerHeight + 24.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                tonalElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = spec.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(spec.titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = RoundedSans,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(spec.subtitleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
