package com.lostf1sh.pixelplayeross.data.offline

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CloudDownloadExportRecoveryTest {
    @Test
    fun resumeReusesTheAttemptDocumentAndSkipsACompletedCopy() = runBlocking {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val scratch = File(appContext.cacheDir, "export-test-${UUID.randomUUID()}")
        assertThat(scratch.mkdir()).isTrue()
        val dataStoreJob = SupervisorJob()
        try {
            val file = File(scratch, "private.mp3").apply { writeText("offline audio fixture") }
            val resolver = mockk<ContentResolver>()
            val context = object : ContextWrapper(appContext) {
                override fun getContentResolver(): ContentResolver = resolver
            }
            val folder = Uri.parse("content://fixture.documents/tree/root")
            val document = DocumentsContract.buildDocumentUriUsingTree(folder, "exported-track")
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, "root")
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + dataStoreJob)) {
                File(scratch, "export.preferences_pb")
            }
            store.edit { it[stringPreferencesKey("cloud_download_export_tree_uri")] = folder.toString() }
            val exports = CloudDownloadExports(context, store, mockk())
            val attempt = UUID.randomUUID().toString()
            val name = exportFileName("Track", attempt, "mp3", idLength = attempt.length)
            val output = ByteArrayOutputStream()
            var size = 5L
            every { resolver.query(eq(children), any(), null, null, null) } answers {
                MatrixCursor(secondArg<Array<String>>()).apply {
                    addRow(arrayOf<Any>("exported-track", name, size, "audio/mpeg"))
                }
            }
            every { resolver.openOutputStream(document, "wt") } returns output

            assertThat(exports.exportDownloadedTrack(file, "Track", attempt, true))
                .isEqualTo(CloudExportResult.EXPORTED)
            assertThat(output.toByteArray()).isEqualTo(file.readBytes())
            size = file.length()
            assertThat(exports.exportDownloadedTrack(file, "Track", attempt, true))
                .isEqualTo(CloudExportResult.EXPORTED)
            verify(exactly = 1) { resolver.openOutputStream(document, "wt") }
        } finally {
            dataStoreJob.cancelAndJoin()
            scratch.deleteRecursively()
        }
        Unit
    }
}
