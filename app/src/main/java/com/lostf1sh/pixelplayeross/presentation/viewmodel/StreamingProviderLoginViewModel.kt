package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.lostf1sh.pixelplayeross.data.jellyfin.JellyfinRepository
import com.lostf1sh.pixelplayeross.data.navidrome.NavidromeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Login state of the streaming providers for screens that only show it, such as Home's
 * provider sheet. The dashboard ViewModels call the server and may start a full library sync
 * as soon as they are created, which Home must not do on every launch.
 */
@HiltViewModel
class StreamingProviderLoginViewModel @Inject constructor(
    navidromeRepository: NavidromeRepository,
    jellyfinRepository: JellyfinRepository,
) : ViewModel() {
    val isNavidromeLoggedIn: StateFlow<Boolean> = navidromeRepository.isLoggedInFlow
    val isJellyfinLoggedIn: StateFlow<Boolean> = jellyfinRepository.isLoggedInFlow
}
