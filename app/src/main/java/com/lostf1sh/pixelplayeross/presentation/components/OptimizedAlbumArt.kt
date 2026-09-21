package com.lostf1sh.pixelplayeross.presentation.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Size
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.utils.LocalArtworkUri

internal const val MaxSafeAlbumArtDimensionPx = 2048
internal val SafeOriginalAlbumArtSize = Size(MaxSafeAlbumArtDimensionPx, MaxSafeAlbumArtDimensionPx)

@OptIn(ExperimentalCoilApi::class, ExperimentalComposeUiApi::class)
@Composable
fun OptimizedAlbumArt(
    uri: Any?,
    title: String,
    modifier: Modifier = Modifier,
    targetSize: Size = SafeOriginalAlbumArtSize,
    placeholderModel: Any? = null
) {
    val artworkModel = rememberArtworkModelWithCacheVersion(uri)
    val context = LocalContext.current
    val requestTargetSize = remember(targetSize) {
        safeAlbumArtTargetSize(targetSize)
    }
    val isStableLocalArtwork = remember(artworkModel) {
        when (artworkModel) {
            is String -> LocalArtworkUri.isLocalArtworkUri(artworkModel)
            is Uri -> LocalArtworkUri.isLocalArtworkUri(artworkModel)
            is ImageRequest -> {
                val data = artworkModel.data
                (data as? String)?.let(LocalArtworkUri::isLocalArtworkUri) == true ||
                    LocalArtworkUri.isLocalArtworkUri(data as? Uri)
            }
            else -> false
        }
    }

    if (renderDirectAlbumArt(
            model = artworkModel,
            title = title,
            modifier = modifier
        )
    ) {
        return
    }

    val memoryCacheKey = remember(artworkModel, requestTargetSize) {
        albumArtMemoryCacheKey(artworkModel, requestTargetSize)
    }
    val placeholderMemoryCacheKey = remember(memoryCacheKey, artworkModel) {
        when (artworkModel) {
            is ImageRequest -> artworkModel.placeholderMemoryCacheKey
                ?: artworkModel.memoryCacheKey
                ?: memoryCacheKey?.let { MemoryCache.Key(it) }
            else -> memoryCacheKey?.let { MemoryCache.Key(it) }
        }
    }
    val requestModel = remember(context, artworkModel, requestTargetSize) {
        when (artworkModel) {
            is ImageRequest -> artworkModel.newBuilder(context).apply {
                size(requestTargetSize)
                if (artworkModel.memoryCacheKey == null) {
                    memoryCacheKey(memoryCacheKey)
                }
                placeholderMemoryCacheKey(placeholderMemoryCacheKey)
            }.build()
            else -> ImageRequest.Builder(context)
                .data(artworkModel)
                .crossfade(350)
                .error(R.drawable.ic_music_placeholder)
                .size(requestTargetSize)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(if (isStableLocalArtwork) CachePolicy.DISABLED else CachePolicy.ENABLED)
                .apply {
                    if (memoryCacheKey != null) {
                        memoryCacheKey(memoryCacheKey)
                    }
                    if (placeholderMemoryCacheKey != null) {
                        placeholderMemoryCacheKey(placeholderMemoryCacheKey)
                    }
                }
                .build()
        }
    }
    var lastSuccessPainter by remember(requestModel.data) { mutableStateOf<Painter?>(null) }

    SubcomposeAsyncImage(
        model = requestModel,
        contentDescription = stringResource(R.string.cd_album_art_of, title),
        modifier = modifier,
        contentScale = ContentScale.Crop,
        onSuccess = { state ->
            lastSuccessPainter = state.painter
        },
        loading = { state ->
            val cachedPainter = state.painter ?: lastSuccessPainter
            if (cachedPainter != null) {
                SubcomposeAsyncImageContent(painter = cachedPainter)
            } else if (placeholderModel != null) {
                 SubcomposeAsyncImage(
                    model = placeholderModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = { PlaceholderContent(title = title) },
                    error = { PlaceholderContent(title = title) }
                )
            } else {
                PlaceholderContent(title = title)
            }
        },
        error = {
            val cachedPainter = lastSuccessPainter
            if (cachedPainter != null) {
                SubcomposeAsyncImageContent(painter = cachedPainter)
            } else {
                PlaceholderContent(title = title)
            }
        },
        success = {
            SubcomposeAsyncImageContent()
        }
    )
}

@Composable
private fun PlaceholderContent(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_music_placeholder),
            contentDescription = stringResource(R.string.cd_album_art_placeholder, title),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(96.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                MaterialTheme.colorScheme.onSurfaceVariant
            ),
        )
    }
}

@Composable
private fun renderDirectAlbumArt(
    model: Any?,
    title: String,
    modifier: Modifier
): Boolean {
    return when (model) {
        is ImageRequest -> renderDirectAlbumArt(model.data, title, modifier)
        is ImageVector -> {
            Image(
                imageVector = model,
                contentDescription = stringResource(R.string.cd_album_art_of, title),
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        is Painter -> {
            Image(
                painter = model,
                contentDescription = stringResource(R.string.cd_album_art_of, title),
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        is ImageBitmap -> {
            Image(
                bitmap = model,
                contentDescription = stringResource(R.string.cd_album_art_of, title),
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        is Bitmap -> {
            Image(
                bitmap = model.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_album_art_of, title),
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        else -> false
    }
}

internal fun safeAlbumArtTargetSize(targetSize: Size): Size {
    return if (targetSize == Size.ORIGINAL) {
        SafeOriginalAlbumArtSize
    } else {
        targetSize
    }
}

internal fun albumArtMemoryCacheKey(model: Any?, targetSize: Size): String? {
    val data = when (model) {
        is ImageRequest -> model.data
        else -> model
    } ?: return null

    val baseKey = when (data) {
        is String -> data.takeIf { it.isNotBlank() }
        is Uri -> data.toString().takeIf { it.isNotBlank() }
        else -> null
    } ?: return null

    if (targetSize == Size.ORIGINAL) return baseKey

    val width = (targetSize.width as? Dimension.Pixels)?.px
    val height = (targetSize.height as? Dimension.Pixels)?.px
    return if (width != null && height != null) {
        "${baseKey}_${width}x${height}"
    } else {
        "${baseKey}_${targetSize.width}x${targetSize.height}"
    }
}
