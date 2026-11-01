package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.lostf1sh.pixelplayeross.data.DailyMixManager
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DailyMixStateHolderTest {

    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val musicRepository = mockk<MusicRepository>(relaxed = true)
    private val dailyMixManager = mockk<DailyMixManager>(relaxed = true)
    private val favorites = flowOf(emptySet<String>())

    @Volatile
    private var library: List<Song> = emptyList()

    private fun holder(
        lastUpdateMillis: Long,
        dailyMixIds: List<String>,
        songsStillInLibrary: List<Song>
    ): DailyMixStateHolder {
        library = songsStillInLibrary
        every { preferences.lastDailyMixUpdateFlow } returns flowOf(lastUpdateMillis)
        every { preferences.dailyMixSongIdsFlow } returns flowOf(dailyMixIds)
        every { musicRepository.getSongsByIds(any()) } answers {
            val ids = firstArg<List<String>>()
            flowOf(library.filter { it.id in ids })
        }
        return DailyMixStateHolder(dailyMixManager, preferences, musicRepository)
    }

    @Test
    fun `a sync on the same day keeps today's mix without reading the library`() = runTest {
        val owner = ownerScope()
        val holder = holder(
            lastUpdateMillis = System.currentTimeMillis(),
            dailyMixIds = listOf("a", "b"),
            songsStillInLibrary = listOf(song("a"), song("b"))
        )
        holder.initialize(owner)

        holder.checkAndUpdateIfNeeded(favorites)
        runCurrent()

        coVerify(exactly = 0) { musicRepository.getAllSongsOnce() }
        coVerify(exactly = 0) { preferences.saveLastDailyMixUpdateTimestamp(any()) }
        owner.cancel()
    }

    @Test
    fun `a new day generates the mixes again`() = runTest {
        val owner = ownerScope()
        val twoDaysAgo = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L
        val holder = holder(
            lastUpdateMillis = twoDaysAgo,
            dailyMixIds = listOf("a"),
            songsStillInLibrary = listOf(song("a"))
        )
        holder.initialize(owner)

        holder.checkAndUpdateIfNeeded(favorites)
        runCurrent()

        coVerify(timeout = 5_000) { musicRepository.getAllSongsOnce() }
        owner.cancel()
    }

    @Test
    fun `the same day generates the mixes when none of today's mix is in the library`() = runTest {
        val owner = ownerScope()
        val holder = holder(
            lastUpdateMillis = System.currentTimeMillis(),
            dailyMixIds = listOf("removed"),
            songsStillInLibrary = emptyList()
        )
        holder.initialize(owner)

        holder.checkAndUpdateIfNeeded(favorites)
        runCurrent()

        coVerify(timeout = 5_000) { musicRepository.getAllSongsOnce() }
        owner.cancel()
    }

    @Test
    fun `checks during a generation wait for it and then run once more`() = runTest {
        // Real threads: the second run resumes on the owner's dispatcher while this test waits.
        val owner = CoroutineScope(Dispatchers.Default + Job())
        val holder = holder(
            lastUpdateMillis = 0L,
            dailyMixIds = emptyList(),
            songsStillInLibrary = emptyList()
        )
        val firstRead = CompletableDeferred<Unit>()
        val reads = AtomicInteger()
        coEvery { musicRepository.getAllSongsOnce() } coAnswers {
            if (reads.incrementAndGet() == 1) firstRead.await()
            emptyList()
        }
        holder.initialize(owner)

        holder.checkAndUpdateIfNeeded(favorites)
        coVerify(timeout = 5_000) { musicRepository.getAllSongsOnce() }
        // e.g. the first sync finishing while the startup check still reads an empty library
        holder.checkAndUpdateIfNeeded(favorites)
        holder.checkAndUpdateIfNeeded(favorites)
        coVerify(exactly = 1) { musicRepository.getAllSongsOnce() }

        firstRead.complete(Unit)
        // The check still finds no mix for today, so its second run generates again.
        coVerify(timeout = 5_000, exactly = 2) { musicRepository.getAllSongsOnce() }
        owner.cancel()
    }

    @Test
    fun `a same-day check drops mix songs a sync removed without reshuffling`() = runTest {
        val owner = ownerScope()
        val holder = holder(
            lastUpdateMillis = System.currentTimeMillis(),
            dailyMixIds = listOf("a", "b", "c"),
            songsStillInLibrary = listOf(song("a"), song("b"), song("c"))
        )
        holder.initialize(owner)
        holder.setDailyMixSongs(listOf(song("c"), song("a"), song("b")))
        runCurrent()

        library = listOf(song("a"), song("c").copy(title = "Renamed"))
        holder.checkAndUpdateIfNeeded(favorites)
        runCurrent()

        assertEquals(listOf("c", "a"), holder.dailyMixSongs.value.map { it.id })
        assertEquals("Renamed", holder.dailyMixSongs.value.first().title)
        coVerify(exactly = 0) { musicRepository.getAllSongsOnce() }
        owner.cancel()
    }

    @Test
    fun `days are compared by calendar date in the given zone`() {
        val zone = ZoneId.of("Europe/Istanbul")
        val lateEvening = ZonedDateTime.of(2026, 3, 14, 23, 50, 0, 0, zone).toInstant().toEpochMilli()
        val nextMorning = ZonedDateTime.of(2026, 3, 15, 0, 10, 0, 0, zone).toInstant().toEpochMilli()
        val sameDayLater = ZonedDateTime.of(2026, 3, 14, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val sameDayNextYear = ZonedDateTime.of(2027, 3, 14, 23, 50, 0, 0, zone).toInstant().toEpochMilli()

        assertTrue(isDifferentDay(lateEvening, nextMorning, zone))
        assertFalse(isDifferentDay(sameDayLater, lateEvening, zone))
        assertTrue(isDifferentDay(lateEvening, sameDayNextYear, zone))
    }

    private fun TestScope.ownerScope(): CoroutineScope = CoroutineScope(coroutineContext + Job())

    private fun song(id: String) = Song(
        id = id,
        title = id,
        artist = "Artist",
        artistId = 1L,
        album = "Album",
        albumId = 1L,
        path = "/music/$id.mp3",
        contentUriString = "content://media/external/audio/media/$id",
        albumArtUriString = null,
        duration = 180_000L,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )
}
