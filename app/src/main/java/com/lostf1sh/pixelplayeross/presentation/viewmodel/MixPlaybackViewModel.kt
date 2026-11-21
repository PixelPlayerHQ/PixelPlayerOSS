package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.lostf1sh.pixelplayeross.data.service.player.ActiveMix
import com.lostf1sh.pixelplayeross.data.service.player.MixPlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Gives the player UI the mixes scheduled for the playing song and the one coming in. */
@HiltViewModel
class MixPlaybackViewModel @Inject constructor(
    mixPlaybackState: MixPlaybackState,
) : ViewModel() {
    val mixes: StateFlow<List<ActiveMix>> = mixPlaybackState.mixes
}
