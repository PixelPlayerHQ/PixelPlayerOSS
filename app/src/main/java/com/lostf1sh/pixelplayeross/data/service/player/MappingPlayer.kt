package com.lostf1sh.pixelplayeross.data.service.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.lostf1sh.pixelplayeross.utils.MediaItemBuilder

/**
 * Rewrites app-internal artwork URIs on outgoing media items to shareable content URIs, so
 * external session consumers (system media surfaces, notification listeners, etc.) can load
 * the artwork instead of receiving an unreadable custom scheme.
 */
@OptIn(UnstableApi::class)
class MappingPlayer(
    val innerPlayer: Player,
    private val context: Context
) : ForwardingPlayer(innerPlayer) {

    private fun mapMediaItem(mediaItem: MediaItem?): MediaItem? {
        if (mediaItem == null) return null
        val artworkUri = mediaItem.mediaMetadata.artworkUri ?: return mediaItem
        val exposedArtworkUri = MediaItemBuilder.externalControllerArtworkUri(
            context = context,
            rawArtworkUri = artworkUri.toString()
        ) ?: return mediaItem
        if (exposedArtworkUri == artworkUri) return mediaItem

        val mappedMetadata = mediaItem.mediaMetadata.buildUpon()
            .setArtworkUri(exposedArtworkUri)
            .build()
        return mediaItem.buildUpon()
            .setMediaMetadata(mappedMetadata)
            .build()
    }

    override fun getCurrentMediaItem(): MediaItem? {
        return mapMediaItem(super.getCurrentMediaItem())
    }

    override fun getMediaItemAt(index: Int): MediaItem {
        return mapMediaItem(super.getMediaItemAt(index))!!
    }

    override fun getMediaMetadata(): MediaMetadata {
        val metadata = super.getMediaMetadata()
        val artworkUri = metadata.artworkUri ?: return metadata
        val exposedArtworkUri = MediaItemBuilder.externalControllerArtworkUri(
            context = context,
            rawArtworkUri = artworkUri.toString()
        ) ?: return metadata
        if (exposedArtworkUri == artworkUri) return metadata

        return metadata.buildUpon()
            .setArtworkUri(exposedArtworkUri)
            .build()
    }

    override fun getCurrentTimeline(): Timeline {
        val timeline = super.getCurrentTimeline()
        if (timeline.isEmpty) return timeline
        return object : Timeline() {
            override fun getWindowCount(): Int = timeline.windowCount
            override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
                val w = timeline.getWindow(windowIndex, window, defaultPositionProjectionUs)
                w.mediaItem = mapMediaItem(w.mediaItem) ?: MediaItem.EMPTY
                return w
            }
            override fun getPeriodCount(): Int = timeline.periodCount
            override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
                return timeline.getPeriod(periodIndex, period, setIds)
            }
            override fun getIndexOfPeriod(periodUid: Any): Int = timeline.getIndexOfPeriod(periodUid)
            override fun getUidOfPeriod(periodIndex: Int): Any = timeline.getUidOfPeriod(periodIndex)
        }
    }
}
