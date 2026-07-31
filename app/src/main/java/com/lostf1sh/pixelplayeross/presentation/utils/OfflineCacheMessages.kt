package com.lostf1sh.pixelplayeross.presentation.utils

import androidx.annotation.StringRes
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.offline.OfflineCacheRequestResult

@StringRes
fun OfflineCacheRequestResult.messageRes(): Int = when (this) {
    OfflineCacheRequestResult.Accepted -> R.string.offline_cache_started
    OfflineCacheRequestResult.AlreadyCached -> R.string.offline_cache_already_saved
    is OfflineCacheRequestResult.LimitExceeded -> R.string.offline_cache_limit_reached
    is OfflineCacheRequestResult.LowStorage -> R.string.offline_cache_low_storage
    OfflineCacheRequestResult.NoRemoteMedia -> R.string.offline_cache_no_remote_media
}
