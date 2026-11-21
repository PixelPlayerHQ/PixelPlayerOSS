package com.lostf1sh.pixelplayeross.data.service.player

import com.lostf1sh.pixelplayeross.data.model.VolumeKeyframe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** The mix scheduled for one song, as the player UI needs it. */
data class ActiveMix(
    /** Id of the song the mix leaves. */
    val songId: String,
    /** Position in that song where the mix begins; the seek bar ends here. */
    val outPointMs: Long,
    val windowMs: Long,
    val outVolume: List<VolumeKeyframe>,
    val inVolume: List<VolumeKeyframe>,
)

/**
 * Hands scheduled mixes from [TransitionController] to the player UI, one per song. It holds the
 * playing song's mix and, while a mix is running, the incoming song's as well, so the new song's
 * seek bar is already the right length when the player switches to it.
 */
@Singleton
class MixPlaybackState @Inject constructor() {
    private val _mixes = MutableStateFlow<List<ActiveMix>>(emptyList())
    val mixes: StateFlow<List<ActiveMix>> = _mixes.asStateFlow()

    /** Adds [mix], replacing any earlier entry for the same song. */
    fun set(mix: ActiveMix) {
        _mixes.update { list -> list.filterNot { it.songId == mix.songId } + mix }
    }

    fun remove(songId: String) {
        _mixes.update { list -> list.filterNot { it.songId == songId } }
    }

    /** Drops every entry except the one for [songId]: the song that is now playing. */
    fun retainOnly(songId: String) {
        _mixes.update { list -> list.filter { it.songId == songId } }
    }

    fun clear() {
        _mixes.value = emptyList()
    }
}
