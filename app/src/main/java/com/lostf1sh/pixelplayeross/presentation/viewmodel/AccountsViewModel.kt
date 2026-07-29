package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.jellyfin.JellyfinRepository
import com.lostf1sh.pixelplayeross.data.listenbrainz.ListenBrainzRepository
import com.lostf1sh.pixelplayeross.data.navidrome.NavidromeRepository
import com.lostf1sh.pixelplayeross.data.preferences.ListenBrainzPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

enum class ExternalServiceAccount {
    NAVIDROME,
    JELLYFIN,
    LISTENBRAINZ
}

data class ExternalAccountUiModel(
    val service: ExternalServiceAccount,
    val title: String,
    val accountLabel: String,
    val syncedContentLabel: String,
    val isLoggingOut: Boolean
)

data class ListenBrainzUiModel(
    val userName: String?,
    val pendingCount: Int,
    val needsReauth: Boolean,
    val scrobbleLocal: Boolean,
    val scrobbleNavidrome: Boolean,
    val scrobbleJellyfin: Boolean
)

sealed interface ListenBrainzConnectState {
    data object Idle : ListenBrainzConnectState
    data object Connecting : ListenBrainzConnectState
    data object Success : ListenBrainzConnectState
    data object Failed : ListenBrainzConnectState
}

data class AccountsUiState(
    val connectedAccounts: List<ExternalAccountUiModel> = emptyList(),
    val disconnectedServices: ImmutableList<ExternalServiceAccount> = persistentListOf(),
    val listenBrainz: ListenBrainzUiModel? = null
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val navidromeRepository: NavidromeRepository,
    private val jellyfinRepository: JellyfinRepository,
    private val listenBrainzRepository: ListenBrainzRepository,
    private val listenBrainzPreferences: ListenBrainzPreferencesRepository
) : ViewModel() {

    private val loggingOutServices = MutableStateFlow<Set<ExternalServiceAccount>>(emptySet())

    private val navidromeStateFlow = combine(
        navidromeRepository.isLoggedInFlow,
        navidromeRepository.getPlaylists().map { it.size }
    ) { connected, playlistCount ->
        connected to playlistCount
    }

    private val jellyfinStateFlow = combine(
        jellyfinRepository.isLoggedInFlow,
        jellyfinRepository.getPlaylists().map { it.size }
    ) { connected, playlistCount ->
        connected to playlistCount
    }

    private val listenBrainzStateFlow = combine(
        listenBrainzRepository.accountState,
        listenBrainzRepository.pendingListenCount,
        listenBrainzPreferences.scrobbleLocalFlow,
        listenBrainzPreferences.scrobbleNavidromeFlow,
        listenBrainzPreferences.scrobbleJellyfinFlow
    ) { account, pendingCount, local, navidrome, jellyfin ->
        if (account.isConnected) {
            ListenBrainzUiModel(
                userName = account.userName,
                pendingCount = pendingCount,
                needsReauth = account.needsReauth,
                scrobbleLocal = local,
                scrobbleNavidrome = navidrome,
                scrobbleJellyfin = jellyfin
            )
        } else {
            null
        }
    }

    private val _listenBrainzConnectState =
        MutableStateFlow<ListenBrainzConnectState>(ListenBrainzConnectState.Idle)
    val listenBrainzConnectState: StateFlow<ListenBrainzConnectState> =
        _listenBrainzConnectState.asStateFlow()

    val uiState: StateFlow<AccountsUiState> = combine(
        combine(
            listOf(
                navidromeStateFlow,
                jellyfinStateFlow
            )
        ) { it.toList() },
        listenBrainzStateFlow,
        loggingOutServices
    ) { states, listenBrainz, activeLogouts ->
        val (navidromeConnected, navidromePlaylistCount) = states[0]
        val (jellyfinConnected, jellyfinPlaylistCount) = states[1]

        val connectedAccounts = buildList {
            if (navidromeConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.NAVIDROME,
                        title = "Subsonic",
                        accountLabel = navidromeRepository.username
                            ?.takeIf { it.isNotBlank() }
                            ?: "Subsonic account connected",
                        syncedContentLabel = formatCount(
                            count = navidromePlaylistCount,
                            singular = "synced playlist",
                            plural = "synced playlists"
                        ),
                        isLoggingOut = ExternalServiceAccount.NAVIDROME in activeLogouts
                    )
                )
            }
            if (jellyfinConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.JELLYFIN,
                        title = "Jellyfin",
                        accountLabel = jellyfinRepository.username
                            ?.takeIf { it.isNotBlank() }
                            ?: "Jellyfin account connected",
                        syncedContentLabel = formatCount(
                            count = jellyfinPlaylistCount,
                            singular = "synced playlist",
                            plural = "synced playlists"
                        ),
                        isLoggingOut = ExternalServiceAccount.JELLYFIN in activeLogouts
                    )
                )
            }
            if (listenBrainz != null) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.LISTENBRAINZ,
                        title = "ListenBrainz",
                        accountLabel = listenBrainz.userName
                            ?.takeIf { it.isNotBlank() }
                            ?: "ListenBrainz account connected",
                        syncedContentLabel = if (listenBrainz.pendingCount > 0) {
                            formatCount(
                                count = listenBrainz.pendingCount,
                                singular = "queued listen",
                                plural = "queued listens"
                            )
                        } else {
                            "Scrobbling listens as you play"
                        },
                        isLoggingOut = ExternalServiceAccount.LISTENBRAINZ in activeLogouts
                    )
                )
            }
        }

        val disconnectedServices = buildList {
            if (!navidromeConnected) add(ExternalServiceAccount.NAVIDROME)
            if (!jellyfinConnected) add(ExternalServiceAccount.JELLYFIN)
            if (listenBrainz == null) add(ExternalServiceAccount.LISTENBRAINZ)
        }

        AccountsUiState(
            connectedAccounts = connectedAccounts,
            disconnectedServices = disconnectedServices.toImmutableList(),
            listenBrainz = listenBrainz
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun logout(service: ExternalServiceAccount) {
        if (service in loggingOutServices.value) return

        viewModelScope.launch {
            loggingOutServices.update { it + service }
            try {
                runCatching {
                    when (service) {
                        ExternalServiceAccount.NAVIDROME -> navidromeRepository.logout()
                        ExternalServiceAccount.JELLYFIN -> jellyfinRepository.logout()
                        ExternalServiceAccount.LISTENBRAINZ -> listenBrainzRepository.disconnect()
                    }
                }
            } finally {
                loggingOutServices.update { it - service }
            }
        }
    }

    fun connectListenBrainz(token: String) {
        if (_listenBrainzConnectState.value == ListenBrainzConnectState.Connecting) return
        viewModelScope.launch {
            _listenBrainzConnectState.value = ListenBrainzConnectState.Connecting
            val result = listenBrainzRepository.connect(token)
            _listenBrainzConnectState.value = if (result.isSuccess) {
                ListenBrainzConnectState.Success
            } else {
                ListenBrainzConnectState.Failed
            }
        }
    }

    fun resetListenBrainzConnectState() {
        _listenBrainzConnectState.value = ListenBrainzConnectState.Idle
    }

    fun setListenBrainzScrobbleLocal(enabled: Boolean) {
        viewModelScope.launch { listenBrainzPreferences.setScrobbleLocal(enabled) }
    }

    fun setListenBrainzScrobbleNavidrome(enabled: Boolean) {
        viewModelScope.launch { listenBrainzPreferences.setScrobbleNavidrome(enabled) }
    }

    fun setListenBrainzScrobbleJellyfin(enabled: Boolean) {
        viewModelScope.launch { listenBrainzPreferences.setScrobbleJellyfin(enabled) }
    }

    private fun formatCount(count: Int, singular: String, plural: String): String {
        return if (count == 1) {
            "1 $singular"
        } else {
            "$count $plural"
        }
    }
}
