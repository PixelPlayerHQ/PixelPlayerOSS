package com.lostf1sh.pixelplayeross.presentation.components.scoped

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import coil.request.CachePolicy
import coil.size.Size
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.presentation.components.albumArtMemoryCacheKey
import com.lostf1sh.pixelplayeross.presentation.components.safeAlbumArtTargetSize
import com.lostf1sh.pixelplayeross.utils.LocalArtworkUri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun PrefetchAlbumNeighborsImg(
    current: Song?,
    queue: ImmutableList<Song>,
    radius: Int = 1
) {
    if (current == null) return
    val context = LocalContext.current
    val loader = remember { coil.ImageLoader(context) }
    val index = remember(current, queue) { queue.indexOfFirst { it.id == current.id } }
    LaunchedEffect(index, queue) {
        if (index == -1) return@LaunchedEffect
        val bounds = (maxOf(0, index - radius))..(minOf(queue.lastIndex, index + radius))
        for (i in bounds) {
            if (i == index) continue
            queue[i].albumArtUriString?.let { data ->
                val diskPolicy = if (LocalArtworkUri.isLocalArtworkUri(data)) CachePolicy.DISABLED else CachePolicy.ENABLED
                val targetSize = safeAlbumArtTargetSize(Size.ORIGINAL)
                val memoryCacheKey = albumArtMemoryCacheKey(data, targetSize)
                val req = coil.request.ImageRequest.Builder(context)
                    .data(data)
                    .apply {
                        if (memoryCacheKey != null) {
                            memoryCacheKey(memoryCacheKey)
                        }
                    }
                    .diskCacheKey(if (diskPolicy == CachePolicy.DISABLED) null else memoryCacheKey)
                    .diskCachePolicy(diskPolicy)
                    .size(targetSize)
                    .build()
                loader.enqueue(req)
            }
        }
    }
}


@Composable
fun PrefetchAlbumNeighbors(
    isActive: Boolean,
    pagerState: PagerState,
    queue: ImmutableList<Song>,
    radius: Int = 1,
    targetSize: Size = Size(600, 600),
    anchorIndex: Int? = null
) {
    if (!isActive || queue.isEmpty()) return
    val context = LocalContext.current
    val imageLoader = coil.Coil.imageLoader(context)
    val requestTargetSize = remember(targetSize) {
        safeAlbumArtTargetSize(targetSize)
    }

    LaunchedEffect(pagerState, queue, anchorIndex, requestTargetSize) {
        snapshotFlow { 
            // If the user is manually scrolling, follow the PagerState.
            // If the Pager is idle, prioritize the provided anchorIndex (which is tied to the current song)
            // to avoid fetching neighbors of a stale index after a queue shift.
            if (pagerState.isScrollInProgress) pagerState.currentPage 
            else anchorIndex ?: pagerState.currentPage 
        }
            .distinctUntilChanged()
            .collect { page ->
                val indices = (page - radius..page + radius)
                    .filter { it in queue.indices && it != page }
                indices.forEach { idx ->
                    queue[idx].albumArtUriString?.let { uri ->
                        val diskPolicy = if (LocalArtworkUri.isLocalArtworkUri(uri)) coil.request.CachePolicy.DISABLED else coil.request.CachePolicy.ENABLED
                        val memoryCacheKey = albumArtMemoryCacheKey(uri, requestTargetSize)
                        val req = coil.request.ImageRequest.Builder(context)
                            .data(uri)
                            .size(requestTargetSize)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .apply {
                                if (memoryCacheKey != null) {
                                    memoryCacheKey(memoryCacheKey)
                                }
                            }
                            .diskCachePolicy(diskPolicy)
                            .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                            .allowHardware(true)
                            .build()
                        imageLoader.enqueue(req) // fire-and-forget
                    }
                }
            }
    }
}
