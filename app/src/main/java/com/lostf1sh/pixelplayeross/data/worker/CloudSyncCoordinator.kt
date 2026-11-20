package com.lostf1sh.pixelplayeross.data.worker

import com.lostf1sh.pixelplayeross.data.jellyfin.JellyfinRepository
import com.lostf1sh.pixelplayeross.data.navidrome.NavidromeRepository
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class CloudSyncCoordinator @Inject constructor(
    private val navidromeRepository: NavidromeRepository,
    private val jellyfinRepository: JellyfinRepository
) {
    val needsActiveCloudSync: Boolean
        get() = navidromeRepository.isLoggedIn &&
            (System.currentTimeMillis() - navidromeRepository.lastFullSyncTime >= NavidromeRepository.SYNC_THRESHOLD_MS)

    suspend fun syncUnifiedCloudLibraries() {
        syncNavidromeData()
        syncJellyfinCache()
    }

    private suspend fun syncNavidromeData() {
        if (!navidromeRepository.isLoggedIn) return

        val lastSync = navidromeRepository.lastFullSyncTime
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastSync < NavidromeRepository.SYNC_THRESHOLD_MS) {
            Timber.tag(TAG).d("Skipping Navidrome server sync - last sync was recent.")
            navidromeRepository.syncUnifiedLibrarySongsFromNavidrome()
            return
        }

        Timber.tag(TAG).i("Syncing Navidrome data from server...")
        try {
            val result = navidromeRepository.syncAllPlaylistsAndSongs()
            result.fold(
                onSuccess = { summary ->
                    Timber.tag(TAG).i(
                        "Navidrome sync complete: ${summary.playlistCount} playlists, " +
                            "${summary.syncedSongCount} songs synced (${summary.failedPlaylistCount} failed)"
                    )
                },
                onFailure = { error ->
                    Timber.tag(TAG).w(error, "Navidrome server sync failed, falling back to local cache sync")
                    navidromeRepository.syncUnifiedLibrarySongsFromNavidrome()
                }
            )
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Failed to sync Navidrome data")
        }
    }

    private suspend fun syncJellyfinCache() {
        if (!jellyfinRepository.isLoggedIn) return

        try {
            jellyfinRepository.syncUnifiedLibrarySongsFromJellyfinIfChanged()
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Failed to sync Jellyfin cached data")
        }
    }

    private companion object {
        private const val TAG = "CloudSyncCoordinator"
    }
}
