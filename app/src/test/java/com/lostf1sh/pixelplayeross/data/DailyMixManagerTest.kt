package com.lostf1sh.pixelplayeross.data

import android.content.Context
import com.lostf1sh.pixelplayeross.data.database.EngagementDao
import com.lostf1sh.pixelplayeross.data.model.Song
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DailyMixManagerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `daily mix rotates between days when nothing has been played`() = runTest {
        val context = mockk<Context> { every { filesDir } returns tempDir }
        val dao = mockk<EngagementDao> { coEvery { getAllEngagements() } returns emptyList() }
        val manager = DailyMixManager(context, dao)
        // Distinct artists and genres so diversity caps do not decide the pick.
        val songs = (1..300).map { song(it) }

        val monday = manager.generateDailyMix(songs, calendar = day(2025, Calendar.MARCH, 3)).map { it.id }
        val mondayAgain = manager.generateDailyMix(songs, calendar = day(2025, Calendar.MARCH, 3)).map { it.id }
        val tuesday = manager.generateDailyMix(songs, calendar = day(2025, Calendar.MARCH, 4)).map { it.id }

        assertEquals(monday, mondayAgain)
        val overlap = monday.intersect(tuesday.toSet()).size
        assertTrue(overlap < monday.size / 2, "expected a mostly new mix, overlap=$overlap")
    }

    private fun day(year: Int, month: Int, dayOfMonth: Int) =
        Calendar.getInstance().apply { set(year, month, dayOfMonth) }

    private fun song(index: Int) = Song(
        id = index.toString(),
        title = "Song $index",
        artist = "Artist $index",
        artistId = index.toLong(),
        album = "Album",
        albumId = 1L,
        path = "/music/$index.mp3",
        contentUriString = "content://media/external/audio/media/$index",
        albumArtUriString = null,
        duration = 180_000L,
        genre = "Genre $index",
        // Newest songs used to win every day through the novelty score alone.
        dateAdded = System.currentTimeMillis() - index * 86_400_000L,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )
}
