package com.lostf1sh.pixelplayeross.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ArtworkTransportSanitizer {
    data class Config(
        val maxDimensionPx: Int,
        val maxBytes: Int,
        val initialJpegQuality: Int,
        val minJpegQuality: Int,
        val jpegQualityStep: Int,
        val sourceBytesLimit: Int,
    )

    val WIDGET_CONFIG = Config(
        maxDimensionPx = 512,
        maxBytes = 220 * 1024,
        initialJpegQuality = 84,
        minJpegQuality = 56,
        jpegQualityStep = 7,
        sourceBytesLimit = 2 * 1024 * 1024,
    )

    val WEAR_CONFIG = Config(
        maxDimensionPx = 1024,
        maxBytes = 380 * 1024,
        initialJpegQuality = 88,
        minJpegQuality = 60,
        jpegQualityStep = 7,
        sourceBytesLimit = 4 * 1024 * 1024,
    )

    fun sanitizeEncodedBytes(
        data: ByteArray?,
        config: Config,
    ): ByteArray? {
        val source = data ?: return null
        if (source.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if ((srcWidth <= 0) || (srcHeight <= 0)) return null

        val srcMax = max(srcWidth, srcHeight)
        if ((srcMax <= config.maxDimensionPx) && (source.size <= config.maxBytes)) {
            return source
        }

        val decoded = decodeBoundedBitmap(source, config.maxDimensionPx) ?: return null
        return try {
            encodeBitmap(decoded, config)
        } finally {
            decoded.recycle()
        }
    }

    private fun decodeBoundedBitmap(data: ByteArray, maxDimensionPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        var sampleSize = 1
        while (
            (srcWidth / sampleSize) > maxDimensionPx * 2 ||
            (srcHeight / sampleSize) > maxDimensionPx * 2
        ) {
            sampleSize *= 2
        }

        val decoded = BitmapFactory.decodeByteArray(
            data,
            0,
            data.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = false
            },
        ) ?: return null

        val bounded = scaleBitmapIfNeeded(decoded, maxDimensionPx)
        if (bounded !== decoded) {
            decoded.recycle()
        }
        return bounded
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDimensionPx: Int): Bitmap {
        val currentMax = max(bitmap.width, bitmap.height)
        if (currentMax <= maxDimensionPx) {
            return bitmap
        }

        val scale = maxDimensionPx.toFloat() / currentMax.toFloat()
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return bitmap.scale(targetWidth, targetHeight, filter = true)
    }

    private fun encodeBitmap(bitmap: Bitmap, config: Config): ByteArray? {
        var quality = config.initialJpegQuality
        var lastValidBytes: ByteArray? = null
        while (quality >= config.minJpegQuality) {
            val output = ByteArrayOutputStream()
            val encoded = runCatching {
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            }.getOrDefault(defaultValue = false)
            if (encoded) {
                val bytes = output.toByteArray()
                if (bytes.isNotEmpty()) {
                    lastValidBytes = bytes
                    if (bytes.size <= config.maxBytes) {
                        return bytes
                    }
                }
            }
            quality -= config.jpegQualityStep
        }
        // Return the smallest encoding even if over maxBytes — better than no art
        return lastValidBytes
    }
}
