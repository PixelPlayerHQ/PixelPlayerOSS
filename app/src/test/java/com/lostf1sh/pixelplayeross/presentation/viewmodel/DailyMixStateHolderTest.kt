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
import kotlinx.coroutines.flow.flow
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
        songsStillInLibrary: List<Song>,
        savedReplacedAtMillis: Long = 0L
    ): DailyMixStateHolder {
        library = songsStillInLibrary
        every { preferences.lastDailyMixUpdateFlow } returns flowOf(lastUpdateMillis)
        every { preferences.dailyMixReplacedAtFlow } returns flowOf(savedReplacedAtMillis)
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
    fun `a check waits for a running regeneration instead of reading the library alongside it`() = runTest {
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

        holder.updateDailyMix(favorites)
        coVerify(timeout = 5_000) { musicRepository.getAllSongsOnce() }
        holder.checkAndUpdateIfNeeded(favorites)
        coVerify(exactly = 1) { musicRepository.getAllSongsOnce() }

        firstRead.complete(Unit)
        coVerify(timeout = 5_000, exactly = 2) { musicRepository.getAllSongsOnce() }
        owner.cancel()
    }

    @Test
    fun `a new day keeps a Daily Mix the user replaced today and still updates Your Mix`() = runTest {
        val owner = CoroutineScope(Dispatchers.Default + Job())
        val holder = holder(
            lastUpdateMillis = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L,
            dailyMixIds = listOf("smart"),
            songsStillInLibrary = listOf(song("smart"), song("other"))
        )
        coEvery { musicRepository.getAllSongsOnce() } returns listOf(song("smart"), song("other"))
        coEvery { dailyMixManager.generateDailyMix(any(), any(), any()) } returns listOf(song("other"))
        coEvery { dailyMixManager.generateYourMix(any(), any(), any()) } returns listOf(song("other"))
        holder.initialize(owner)

        // e.g. a Smart Mix applied after midnight, before the day's first check
        holder.setDailyMixSongs(listOf(song("smart")))
        holder.checkAndUpdateIfNeeded(favorites)

        coVerify(timeout = 5_000) { preferences.saveYourMixSongIds(listOf("other")) }
        assertEquals(listOf("smart"), holder.dailyMixSongs.value.map { it.id })
        assertEquals(listOf("other"), holder.yourMixSongs.value.map { it.id })
        coVerify(exactly = 0) { preferences.saveDailyMixSongIds(listOf("other")) }
        owner.cancel()
    }

    @Test
    fun `a Daily Mix replaced today is kept after a restart while Your Mix updates`() = runTest {
        val owner = CoroutineScope(Dispatchers.Default + Job())
        // A fresh holder, as after a process restart: only the saved replacement date remains.
        val holder = holder(
            lastUpdateMillis = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L,
            dailyMixIds = listOf("smart"),
            songsStillInLibrary = listOf(song("smart"), song("other")),
            savedReplacedAtMillis = System.currentTimeMillis()
        )
        coEvery { musicRepository.getAllSongsOnce() } returns listOf(song("smart"), song("other"))
        coEvery { dailyMixManager.generateDailyMix(any(), any(), any()) } returns listOf(song("other"))
        coEvery { dailyMixManager.generateYourMix(any(), any(), any()) } returns listOf(song("other"))
        holder.initialize(owner)

        holder.checkAndUpdateIfNeeded(favorites)

        coVerify(timeout = 5_000) { preferences.saveYourMixSongIds(listOf("other")) }
        coVerify(exactly = 0) { preferences.saveDailyMixSongIds(any()) }
        owner.cancel()
    }

    @Test
    fun `a Daily Mix replaced today is generated again once none of it is in the library`() = runTest {
        val owner = CoroutineScope(Dispatchers.Default + Job())
        val holder = holder(
            lastUpdateMillis = System.currentTimeMillis(),
            dailyMixIds = listOf("removed"),
            songsStillInLibrary = listOf(song("other")),
            savedReplacedAtMillis = System.currentTimeMillis()
        )
        coEvery { musicRepository.getAllSongsOnce() } returns listOf(song("other"))
        coEvery { dailyMixManager.generateDailyMix(any(), any(), any()) } returns listOf(song("other"))
        coEvery { dailyMixManager.generateYourMix(any(), any(), any()) } returns listOf(song("other"))
        holder.initialize(owner)
        // A Smart Mix applied earlier today whose only song a sync has since removed
        holder.setDailyMixSongs(listOf(song("removed")))

        holder.checkAndUpdateIfNeeded(favorites)

        coVerify(timeout = 5_000) { preferences.saveDailyMixSongIds(listOf("other")) }
        assertEquals(listOf("other"), holder.dailyMixSongs.value.map { it.id })
        owner.cancel()
    }

    @Test
    fun `a Smart Mix applied while a mix is generated is not overwritten`() = runTest {
        val owner = CoroutineScope(Dispatchers.Default + Job())
        // Today's saved mix is gone from the library, so the check generates a new one.
        val holder = holder(
            lastUpdateMillis = System.currentTimeMillis(),
            dailyMixIds = listOf("removed"),
            songsStillInLibrary = listOf(song("smart"), song("other"))
        )
        coEvery { musicRepository.getAllSongsOnce() } returns listOf(song("smart"), song("other"))
        val generating = CompletableDeferred<Unit>()
        val finishGenerating = CompletableDeferred<Unit>()
        coEvery { dailyMixManager.generateDailyMix(any(), any(), any()) } coAnswers {
            generating.complete(Unit)
            finishGenerating.await()
            listOf(song("other"))
        }
        coEvery { dailyMixManager.generateYourMix(any(), any(), any()) } returns listOf(song("other"))
        holder.initialize(owner)

        holder.checkAndUpdateIfNeeded(favorites)
        generating.await()
        holder.setDailyMixSongs(listOf(song("smart")))
        finishGenerating.complete(Unit)

        coVerify(timeout = 5_000) { preferences.saveYourMixSongIds(listOf("other")) }
        coVerify(timeout = 5_000) { preferences.saveDailyMixSongIds(listOf("smart")) }
        assertEquals(listOf("smart"), holder.dailyMixSongs.value.map { it.id })
        coVerify(exactly = 0) { preferences.saveDailyMixSongIds(listOf("other")) }
        owner.cancel()
    }

    @Test
    fun `a Smart Mix applied while the keep check looks up the previous mix is not overwritten`() = runTest {
        val owner = CoroutineScope(Dispatchers.Default + Job())
        // After a restart: the previous replacement's songs are gone and nothing is shown yet.
        val holder = holder(
            lastUpdateMillis = System.currentTimeMillis(),
            dailyMixIds = listOf("removed"),
            songsStillInLibrary = listOf(song("smart"), song("other")),
            savedReplacedAtMillis = System.currentTimeMillis()
        )
        coEvery { musicRepository.getAllSongsOnce() } returns listOf(song("smart"), song("other"))
        coEvery { dailyMixManager.generateDailyMix(any(), any(), any()) } returns listOf(song("other"))
        coEvery { dailyMixManager.generateYourMix(any(), any(), any()) } returns listOf(song("other"))
        val lookups = AtomicInteger()
        val keepCheckLooking = CompletableDeferred<Unit>()
        val finishLookup = CompletableDeferred<Unit>()
        every { musicRepository.getSongsByIds(listOf("removed")) } returns flow {
            // The first lookup decides the check is due; the second is the keep check.
            if (lookups.incrementAndGet() == 2) {
                keepCheckLooking.complete(Unit)
                finishLookup.await()
            }
            emit(emptyList())
        }
        holder.initialize(owner)

        holder.checkAndUpdateIfNeeded(favorites)
        keepCheckLooking.await()
        holder.setDailyMixSongs(listOf(song("smart")))
        finishLookup.complete(Unit)

        coVerify(timeout = 5_000) { preferences.saveYourMixSongIds(listOf("other")) }
        coVerify(timeout = 5_000) { preferences.saveDailyMixSongIds(listOf("smart")) }
        assertEquals(listOf("smart"), holder.dailyMixSongs.value.map { it.id })
        coVerify(exactly = 0) { preferences.saveDailyMixSongIds(listOf("other")) }
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
