package com.lostf1sh.pixelplayeross.presentation.recap

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.core.content.FileProvider
import com.lostf1sh.pixelplayeross.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RECAP_SHARE_DIRECTORY = "recap"

/**
 * Saves what [layer] last recorded as a PNG in the cache and opens the share sheet for it.
 * Each year overwrites its previous image, so the cache never collects old copies.
 */
internal suspend fun shareRecapImage(context: Context, layer: GraphicsLayer, year: Int) {
    val captured = layer.toImageBitmap().asAndroidBitmap()
    val uri = withContext(Dispatchers.IO) {
        // Recorded layers come back as hardware bitmaps, which cannot be encoded directly.
        val bitmap = if (captured.config == Bitmap.Config.HARDWARE) {
            captured.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            captured
        }
        val directory = File(context.cacheDir, RECAP_SHARE_DIRECTORY).apply { mkdirs() }
        val file = File(directory, "recap_$year.png")
        file.outputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG encoding failed" }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.recap_share_text, year))
        // ClipData lets the share sheet preview the image and carries the read grant.
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.recap_share_chooser_title))
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
