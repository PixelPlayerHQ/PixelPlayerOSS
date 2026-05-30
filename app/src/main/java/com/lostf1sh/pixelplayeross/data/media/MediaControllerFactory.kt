package com.lostf1sh.pixelplayeross.data.media

import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaControllerFactory @Inject constructor() {
    fun create(
        context: Context,
        token: SessionToken,
        listener: MediaController.Listener
    ): ListenableFuture<MediaController> {
        return MediaController.Builder(context, token)
            .setListener(listener)
            .buildAsync()
    }
}
