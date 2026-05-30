package com.lostf1sh.pixelplayeross.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.lostf1sh.pixelplayeross.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Helper utility for creating ZIP archives from multiple songs
 * and sharing them via the system share dialog.
 */
object ZipShareHelper {
    
    private const val ZIP_CACHE_DIR = "shared_zips"
    private const val BUFFER_SIZE = 8192
    private const val MAX_RECOMMENDED_SIZE_BYTES = 100 * 1024 * 1024L // 100MB warning threshold
    
    /**
     * Creates a ZIP file containing all provided songs and shares it.
     * 
     * @param context Application context
     * @param songs List of songs to include in the ZIP
     * @param onProgress Optional callback for progress updates (0.0 to 1.0)
     * @return Result indicating success or failure with error message
     */
    suspend fun createAndShareZip(
        context: Context,
        songs: List<Song>,
        onProgress: ((Float) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (songs.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No songs to share"))
            }
            
            // Create cache directory for ZIP files
            val zipDir = File(context.cacheDir, ZIP_CACHE_DIR)
            if (!zipDir.exists()) {
                zipDir.mkdirs()
            }
            
            // Clean up old ZIP files (older than 1 hour)
            cleanupOldZips(zipDir)
            
            // Generate unique filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val zipFile = File(zipDir, "PixelPlayerOSS_Songs_$timestamp.zip")
            
            // Create ZIP file
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipOut ->
                val totalSongs = songs.size
                val usedNames = mutableSetOf<String>()
                
                songs.forEachIndexed { index, song ->
                    try {
                        val songUri = song.contentUriString.toUri()
                        context.contentResolver.openInputStream(songUri)?.use { inputStream ->
                            BufferedInputStream(inputStream, BUFFER_SIZE).use { bufferedIn ->
                                // Generate unique filename (handle duplicates)
                                val baseName = sanitizeFileName(song.title)
                                val extension = getFileExtension(song.path)
                                var fileName = "$baseName.$extension"
                                var counter = 1
                                while (usedNames.contains(fileName.lowercase())) {
                                    fileName = "${baseName}_$counter.$extension"
                                    counter++
                                }
                                usedNames.add(fileName.lowercase())
                                
                                // Add entry to ZIP
                                zipOut.putNextEntry(ZipEntry(fileName))
                                
                                val buffer = ByteArray(BUFFER_SIZE)
                                var bytesRead: Int
                                while (bufferedIn.read(buffer).also { bytesRead = it } != -1) {
                                    zipOut.write(buffer, 0, bytesRead)
                                }
                                
                                zipOut.closeEntry()
                            }
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to add song to ZIP: ${song.title}")
                        // Continue with other songs
                    }
                    
                    // Report progress
                    onProgress?.invoke((index + 1).toFloat() / totalSongs)
                }
            }
            
            // Get shareable URI via FileProvider
            val zipUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                zipFile
            )
            
            // Share the ZIP file
            withContext(Dispatchers.Main) {
                shareZipFile(context, zipUri, songs.size)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create ZIP archive")
            Result.failure(e)
        }
    }
    
    /**
     * Calculates the total size of songs to be shared.
     * Useful for warning users about large file sizes.
     *
     * @param context Application context
     * @param songs List of songs to calculate size for
     * @return Total size in bytes, or -1 if calculation fails
     */
    suspend fun calculateTotalSize(
        context: Context,
        songs: List<Song>
    ): Long = withContext(Dispatchers.IO) {
        var totalSize = 0L
        songs.forEach { song ->
            try {
                val songUri = song.contentUriString.toUri()
                context.contentResolver.openInputStream(songUri)?.use { inputStream ->
                    totalSize += inputStream.available().toLong()
                }
            } catch (e: Exception) {
                // Ignore and continue
            }
        }
        totalSize
    }
    
    /**
     * Checks if the total size exceeds the recommended threshold.
     */
    fun isLargeZip(totalSizeBytes: Long): Boolean {
        return totalSizeBytes > MAX_RECOMMENDED_SIZE_BYTES
    }
    
    /**
     * Formats file size for display.
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
    
    /**
     * Cleans up temporary ZIP files from the cache.
     */
    fun cleanupTempZips(context: Context) {
        try {
            val zipDir = File(context.cacheDir, ZIP_CACHE_DIR)
            if (zipDir.exists()) {
                zipDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to cleanup ZIP cache")
        }
    }
    
    private fun cleanupOldZips(zipDir: File) {
        try {
            val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
            zipDir.listFiles()?.filter { it.lastModified() < oneHourAgo }?.forEach { 
                it.delete() 
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to cleanup old ZIP files")
        }
    }
    
    private fun shareZipFile(context: Context, zipUri: Uri, songCount: Int) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, zipUri)
            putExtra(Intent.EXTRA_SUBJECT, "PixelPlayerOSS: $songCount Songs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooserIntent = Intent.createChooser(shareIntent, "Share $songCount songs")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    }
    
    private fun sanitizeFileName(name: String): String {
        // Remove or replace characters that are invalid in filenames
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .take(100) // Limit filename length
    }
    
    private fun getFileExtension(path: String): String {
        val extension = path.substringAfterLast('.', "mp3")
        return if (extension.length in 1..4) extension else "mp3"
    }
}
