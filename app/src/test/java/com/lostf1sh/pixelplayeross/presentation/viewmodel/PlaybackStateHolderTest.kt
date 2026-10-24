package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import com.lostf1sh.pixelplayeross.MainCoroutineExtension
import com.lostf1sh.pixelplayeross.data.model.PlaybackQueueItemSnapshot
import com.lostf1sh.pixelplayeross.data.model.PlaybackQueueSnapshot
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.preferences.PreviousButtonBehavior
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.service.player.DualPlayerEngine
import com.lostf1sh.pixelplayeross.utils.MediaItemBuilder
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import java.util.IdentityHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import androidx.media3.session.MediaController

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class PlaybackStateHolderTest {

    private val dualPlayerEngine: DualPlayerEngine = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val queueStateHolder: QueueStateHolder = mockk(relaxed = true)
    private val appContext: Context = mockk(relaxed = true)
    private val powerManager: PowerManager = mockk(relaxed = true)

    private fun createHolder(
        queueStateHolder: QueueStateHolder = this.queueStateHolder
    ) = PlaybackStateHolder(
        dualPlayerEngine = dualPlayerEngine,
        userPreferencesRepository = userPreferencesRepository,
        queueStateHolder = queueStateHolder,
        appContext = appContext
    )

    private fun snapshot(
        mediaId: String = "duplicate-song",
        positionMs: Long = 48_000L
    ) = PlaybackQueueSnapshot(
        items = listOf(
            PlaybackQueueItemSnapshot(
                mediaId = mediaId,
                uri = "file:///music/$mediaId.mp3"
            )
        ),
        currentMediaId = mediaId,
        currentIndex = 0,
        currentPositionMs = positionMs
    )

    init {
        every { appContext.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { powerManager.isInteractive } returns true
    }

    @Test
    fun `paused override does not bleed into later occurrence with same media id`() {
        val holder = createHolder()

        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.rememberPausedPositionOverride("duplicate-song", 91_000L)

        holder.onPlaybackOccurrenceTransition("another-song")
        holder.onPlaybackOccurrenceTransition("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(0L, holder.currentPosition.value)
    }

    @Test
    fun `clearing latest media controller restores previous activity controller`() {
        val holder = createHolder()
        val mainController = mockk<MediaController>(relaxed = true)
        val externalController = mockk<MediaController>(relaxed = true)

        holder.setMediaController(mainController)
        holder.setMediaController(externalController)

        assertSame(externalController, holder.mediaController)

        holder.clearMediaController(externalController)

        assertSame(mainController, holder.mediaController)
    }

    @Test
    fun `clearing stale media controller keeps active controller`() {
        val holder = createHolder()
        val mainController = mockk<MediaController>(relaxed = true)
        val externalController = mockk<MediaController>(relaxed = true)

        holder.setMediaController(externalController)
        holder.setMediaController(mainController)

        holder.clearMediaController(externalController)

        assertSame(mainController, holder.mediaController)
    }

    @Test
    fun `cold start snapshot only applies to the first matching occurrence`() = runTest {
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns snapshot()

        val holder = createHolder()
        holder.initialize(this)
        advanceUntilIdle()

        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)
        assertEquals(48_000L, holder.currentPosition.value)

        holder.onPlaybackOccurrenceTransition("another-song")
        holder.onPlaybackOccurrenceTransition("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(0L, holder.currentPosition.value)
    }

    @Test
    fun `late cold start snapshot binds to the already active first occurrence`() = runTest {
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns snapshot()

        val holder = createHolder()
        holder.initialize(this)
        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)
        assertEquals(0L, holder.currentPosition.value)

        advanceUntilIdle()

        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(48_000L, holder.currentPosition.value)
    }

    @Test
    fun `late cold start snapshot is discarded after playback occurrence advances`() = runTest {
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns snapshot()

        val holder = createHolder()
        holder.initialize(this)
        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.onPlaybackOccurrenceTransition("another-song")
        holder.onPlaybackOccurrenceTransition("duplicate-song")

        advanceUntilIdle()

        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(0L, holder.currentPosition.value)
    }

    @Test
    fun `late cold start snapshot is discarded after first occurrence already ended`() = runTest {
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns snapshot()

        val holder = createHolder()
        holder.initialize(this)
        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.onPlaybackOccurrenceTransition(null)

        advanceUntilIdle()

        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(0L, holder.currentPosition.value)
    }

    // --- Auto-play on skip and Previous ---

    private fun connectedController(
        playWhenReady: Boolean = false,
        hasNext: Boolean = true,
        hasPrevious: Boolean = true,
        positionMs: Long = 5_000L,
        state: Int = Player.STATE_READY
    ): MediaController = mockk<MediaController>(relaxed = true).also { controller ->
        every { controller.isConnected } returns true
        every { controller.playWhenReady } returns playWhenReady
        every { controller.hasNextMediaItem() } returns hasNext
        every { controller.hasPreviousMediaItem() } returns hasPrevious
        every { controller.currentPosition } returns positionMs
        every { controller.playbackState } returns state
        every { controller.mediaItemCount } returns 3
        every { controller.currentMediaItem } returns null
    }

    /**
     * A holder whose "Auto-play on skip" and "Previous button" preferences are [autoplayOnSkip]
     * and [previousButtonBehavior], driving [controller].
     */
    private fun TestScope.holderFor(
        controller: MediaController,
        autoplayOnSkip: Boolean = true,
        previousButtonBehavior: PreviousButtonBehavior = PreviousButtonBehavior.RESTART_TRACK,
        queueStateHolder: QueueStateHolder = this@PlaybackStateHolderTest.queueStateHolder
    ): PlaybackStateHolder {
        // Finite flows, so the collectors started by initialize() let runTest finish.
        every { userPreferencesRepository.autoplayOnSkipFlow } returns flowOf(autoplayOnSkip)
        every { userPreferencesRepository.previousButtonBehaviorFlow } returns flowOf(previousButtonBehavior)
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns null
        return createHolder(queueStateHolder).also { holder ->
            holder.initialize(this)
            advanceUntilIdle()
            holder.setMediaController(controller)
        }
    }

    @Test
    fun `next while paused starts playback when auto-play on skip is on`() = runTest {
        val controller = connectedController()

        holderFor(controller).nextSong()

        verifyOrder {
            controller.seekToNext()
            controller.play()
        }
    }

    @Test
    fun `next while paused stays paused when auto-play on skip is off`() = runTest {
        val controller = connectedController()

        holderFor(controller, autoplayOnSkip = false).nextSong()

        verify { controller.seekToNext() }
        verify(exactly = 0) { controller.play() }
    }

    @Test
    fun `next while already playing does not call play again`() = runTest {
        val controller = connectedController(playWhenReady = true)

        holderFor(controller).nextSong()

        verify { controller.seekToNext() }
        verify(exactly = 0) { controller.play() }
    }

    @Test
    fun `next on the last item does not resume the paused song`() = runTest {
        val controller = connectedController(hasNext = false)

        holderFor(controller).nextSong()

        verify(exactly = 0) { controller.play() }
    }

    @Test
    fun `previous between 3 and 10 seconds moves to the previous track`() = runTest {
        // Media3's seekToPrevious() restarts the track past 3 s, so it must not be used here.
        val controller = connectedController(positionMs = 5_000L)

        holderFor(controller).previousSong()

        verifyOrder {
            controller.seekToPreviousMediaItem()
            controller.play()
        }
        verify(exactly = 0) { controller.seekToPrevious() }
        verify(exactly = 0) { controller.seekTo(0L) }
    }

    @Test
    fun `previous after 10 seconds restarts the track without auto-play`() = runTest {
        val controller = connectedController(positionMs = 12_000L)

        holderFor(controller).previousSong()

        verify { controller.seekTo(0L) }
        verify(exactly = 0) { controller.seekToPreviousMediaItem() }
        verify(exactly = 0) { controller.play() }
    }

    @Test
    fun `previous on the first item restarts it instead of doing nothing`() = runTest {
        val controller = connectedController(hasPrevious = false, positionMs = 1_000L)

        holderFor(controller).previousSong()

        verify { controller.seekTo(0L) }
        verify(exactly = 0) { controller.seekToPreviousMediaItem() }
        verify(exactly = 0) { controller.play() }
    }

    @Test
    fun `previous after 10 seconds moves to the previous track in previous-track mode`() = runTest {
        val controller = connectedController(positionMs = 30_000L)

        holderFor(controller, previousButtonBehavior = PreviousButtonBehavior.PREVIOUS_TRACK).previousSong()

        verifyOrder {
            controller.seekToPreviousMediaItem()
            controller.play()
        }
        verify(exactly = 0) { controller.seekTo(0L) }
        verify(exactly = 0) { controller.seekToPrevious() }
    }

    @Test
    fun `previous early in the song still moves back in previous-track mode`() = runTest {
        val controller = connectedController(positionMs = 1_000L)

        holderFor(controller, previousButtonBehavior = PreviousButtonBehavior.PREVIOUS_TRACK).previousSong()

        verify { controller.seekToPreviousMediaItem() }
        verify(exactly = 0) { controller.seekTo(0L) }
    }

    @Test
    fun `previous on the first item restarts it in previous-track mode`() = runTest {
        val controller = connectedController(hasPrevious = false, positionMs = 30_000L)

        holderFor(controller, previousButtonBehavior = PreviousButtonBehavior.PREVIOUS_TRACK).previousSong()

        verify { controller.seekTo(0L) }
        verify(exactly = 0) { controller.seekToPreviousMediaItem() }
        verify(exactly = 0) { controller.play() }
    }

    @Test
    fun `previous in previous-track mode stays paused when auto-play on skip is off`() = runTest {
        val controller = connectedController(positionMs = 30_000L)

        holderFor(
            controller,
            autoplayOnSkip = false,
            previousButtonBehavior = PreviousButtonBehavior.PREVIOUS_TRACK
        ).previousSong()

        verify { controller.seekToPreviousMediaItem() }
        verify(exactly = 0) { controller.play() }
    }

    @Test
    fun `current previous button behavior mirrors the preference`() = runTest {
        val controller = connectedController()

        assertEquals(
            PreviousButtonBehavior.RESTART_TRACK,
            holderFor(controller).currentPreviousButtonBehavior()
        )
        assertEquals(
            PreviousButtonBehavior.PREVIOUS_TRACK,
            holderFor(controller, previousButtonBehavior = PreviousButtonBehavior.PREVIOUS_TRACK)
                .currentPreviousButtonBehavior()
        )
    }

    @Test
    fun `force previous while paused moves back and starts playback`() = runTest {
        val controller = connectedController(positionMs = 30_000L)

        holderFor(controller).forcePreviousSong()

        verifyOrder {
            controller.seekToPreviousMediaItem()
            controller.play()
        }
    }

    @Test
    fun `skip from an idle player prepares it before playing`() = runTest {
        val controller = connectedController(state = Player.STATE_IDLE)

        holderFor(controller).nextSong()

        verifyOrder {
            controller.seekToNext()
            controller.prepare()
            controller.play()
        }
    }

    // --- Owner-scoped teardown ---

    // Separate Job so each owner has its own identity, while still running on the test
    // dispatcher that advanceTimeBy drives.
    private fun TestScope.ownerScope(): CoroutineScope = CoroutineScope(coroutineContext + Job())

    /** A playing controller whose reported position is read from [positionMs] on every tick. */
    private fun playingController(positionMs: () -> Long): MediaController =
        connectedController(playWhenReady = true).also { controller ->
            every { controller.isPlaying } returns true
            every { controller.currentPosition } answers { positionMs() }
        }

    /** Binds a holder to [owners] in order, so the last one is the current owner. */
    private fun TestScope.holderBoundTo(
        vararg owners: CoroutineScope,
        controller: MediaController
    ): PlaybackStateHolder {
        every { userPreferencesRepository.autoplayOnSkipFlow } returns flowOf(true)
        every { userPreferencesRepository.previousButtonBehaviorFlow } returns
            flowOf(PreviousButtonBehavior.RESTART_TRACK)
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns null
        return createHolder().also { holder ->
            owners.forEach(holder::initialize)
            runCurrent()
            holder.setMediaController(controller)
            // Progress only ticks while someone observes the position, as the player UI does.
            backgroundScope.launch { holder.currentPosition.collect {} }
            holder.startProgressUpdates()
            runCurrent()
        }
    }

    @Test
    fun `stale owner clearing keeps the newer owner's progress updates running`() = runTest {
        var positionMs = 5_000L
        val staleOwner = ownerScope()
        val currentOwner = ownerScope()
        val holder = holderBoundTo(staleOwner, currentOwner, controller = playingController { positionMs })
        assertEquals(5_000L, holder.currentPosition.value)

        holder.onCleared(staleOwner)
        staleOwner.cancel()
        positionMs = 7_000L
        advanceTimeBy(1_500L)
        runCurrent()

        assertEquals(7_000L, holder.currentPosition.value)
        currentOwner.cancel()
    }

    @Test
    fun `current owner clearing stops its progress updates`() = runTest {
        var positionMs = 5_000L
        val owner = ownerScope()
        val holder = holderBoundTo(owner, controller = playingController { positionMs })
        assertEquals(5_000L, holder.currentPosition.value)

        holder.onCleared(owner)
        positionMs = 7_000L
        advanceTimeBy(1_500L)
        runCurrent()
        // Unbound, so a later start request has no scope to run in.
        holder.startProgressUpdates()
        advanceTimeBy(1_500L)
        runCurrent()

        assertEquals(5_000L, holder.currentPosition.value)
        owner.cancel()
    }

    @Test
    fun `current owner clearing moves progress updates to the surviving owner`() = runTest {
        var positionMs = 5_000L
        val mainActivityOwner = ownerScope()
        val externalPlayerOwner = ownerScope()
        // Progress starts while ExternalPlayerActivity's view model is current, so in its scope.
        val holder = holderBoundTo(
            mainActivityOwner,
            externalPlayerOwner,
            controller = playingController { positionMs }
        )
        assertEquals(5_000L, holder.currentPosition.value)

        // ViewModel.clear() cancels the scope before onCleared runs.
        externalPlayerOwner.cancel()
        holder.onCleared(externalPlayerOwner)
        positionMs = 7_000L
        advanceTimeBy(1_500L)
        runCurrent()

        assertEquals(7_000L, holder.currentPosition.value)
        mainActivityOwner.cancel()
    }

    @Test
    fun `stale owner clearing restarts the progress updates it was running`() = runTest {
        var positionMs = 5_000L
        val mainActivityOwner = ownerScope()
        val externalPlayerOwner = ownerScope()
        val holder = holderBoundTo(mainActivityOwner, controller = playingController { positionMs })
        holder.initialize(externalPlayerOwner)
        runCurrent()

        mainActivityOwner.cancel()
        holder.onCleared(mainActivityOwner)
        positionMs = 7_000L
        advanceTimeBy(1_500L)
        runCurrent()

        assertEquals(7_000L, holder.currentPosition.value)
        externalPlayerOwner.cancel()
    }

    // --- Playlist tags across a shuffle rebuild ---

    private fun song(id: String) = Song(
        id = id,
        title = id,
        artist = "Artist",
        artistId = 1L,
        album = "Album",
        albumId = 1L,
        path = "/music/$id.mp3",
        contentUriString = "content://media/$id",
        albumArtUriString = null,
        duration = 180_000L,
        mimeType = "audio/mpeg",
        bitrate = null,
        sampleRate = null
    )

    private fun playlistOf(size: Int) = List(size) { index -> song("song-$index") }

    /**
     * A player queue of [songs], tagged by position with [playlistIds], playing the entry at
     * [currentIndex]. Bundle extras are stubs on the JVM, so the tags live in [tags], and
     * [controller] edits [items] the way the player would.
     */
    private inner class TaggedQueue(
        songs: List<Song>,
        playlistIds: List<String?>,
        currentIndex: Int
    ) {
        val tags = IdentityHashMap<MediaItem, String>()
        val items: MutableList<MediaItem> = songs.mapIndexedTo(mutableListOf()) { index, song ->
            MediaItem.Builder().setMediaId(song.id).build().also { item ->
                playlistIds[index]?.let { tags[item] = it }
            }
        }
        var playing: MediaItem = items[currentIndex]
            private set
        var segmentReplaceCount = 0
            private set
        val controller: MediaController = connectedController().also { controller ->
            every { controller.mediaItemCount } answers { items.size }
            every { controller.getMediaItemAt(any()) } answers { items[firstArg<Int>()] }
            every { controller.currentMediaItem } answers { playing }
            every { controller.currentMediaItemIndex } answers {
                items.indexOfFirst { it === playing }
            }
            every { controller.moveMediaItem(any(), any()) } answers {
                items.add(secondArg<Int>(), items.removeAt(firstArg<Int>()))
            }
            every { controller.replaceMediaItems(any(), any(), any()) } answers {
                val fromIndex = firstArg<Int>()
                repeat(secondArg<Int>() - fromIndex) { items.removeAt(fromIndex) }
                items.addAll(fromIndex, thirdArg<List<MediaItem>>())
                segmentReplaceCount++
            }
            every { controller.setMediaItems(any(), any(), any()) } answers {
                items.clear()
                items.addAll(firstArg<List<MediaItem>>())
                playing = items[secondArg<Int>()]
            }
        }

        /** Each entry as (media id, playlist tag), in player order. */
        fun entries(): List<Pair<String, String?>> = items.map { it.mediaId to tags[it] }
    }

    /**
     * Runs [block] with MediaItemBuilder keeping playlist tags in [tags], and with a clock that
     * moves on so one shuffle toggle can follow another.
     */
    private suspend fun withPlaylistTagsIn(
        tags: IdentityHashMap<MediaItem, String>,
        block: suspend () -> Unit
    ) {
        var nowMs = 10_000L
        mockkStatic(SystemClock::class)
        mockkObject(MediaItemBuilder)
        try {
            every { SystemClock.elapsedRealtime() } answers {
                nowMs += 1_000L
                nowMs
            }
            every { MediaItemBuilder.build(any()) } answers {
                MediaItem.Builder().setMediaId(firstArg<Song>().id).build()
            }
            every { MediaItemBuilder.playlistIdOf(any()) } answers { tags[firstArg<MediaItem>()] }
            every { MediaItemBuilder.withPlaylistId(any(), any()) } answers {
                firstArg<MediaItem>().also { item -> secondArg<String?>()?.let { tags[item] = it } }
            }
            every { userPreferencesRepository.persistentShuffleEnabledFlow } returns flowOf(false)
            block()
        } finally {
            unmockkObject(MediaItemBuilder)
            unmockkStatic(SystemClock::class)
        }
    }

    /** Toggles shuffle while [currentSong] plays, waits for it to finish and returns the new queue. */
    private suspend fun PlaybackStateHolder.toggleShuffleAndWait(
        queue: List<Song>,
        currentSong: Song
    ): List<Song> {
        val wasShuffled = stablePlayerState.value.isShuffleEnabled
        var newQueue = queue
        toggleShuffle(queue, currentSong) { newQueue = it }
        stablePlayerState.first {
            it.isShuffleEnabled != wasShuffled && !it.isShuffleTransitionInProgress
        }
        return newQueue
    }

    @Test
    fun `shuffling a large playlist queue keeps a hand-added copy of a playlist song untagged`() = runTest {
        // Over 80 entries, so the shuffle rebuilds the queue instead of moving items in place.
        val playlistSongs = playlistOf(81)
        val duplicated = playlistSongs[5]
        // The last entry is the copy added through Play next, which has no tag.
        val songs = playlistSongs + duplicated
        val queue = TaggedQueue(songs, playlistSongs.map { "playlist" } + null, currentIndex = 0)

        withPlaylistTagsIn(queue.tags) {
            val holder = holderFor(queue.controller)

            holder.toggleShuffleAndWait(songs, playlistSongs.first())

            val duplicatedTags = queue.entries().filter { it.first == duplicated.id }.map { it.second }
            assertEquals(listOf("playlist", null), duplicatedTags.sortedBy { it == null })
            assertEquals(playlistSongs.size, queue.entries().count { it.second != null })
            verify(exactly = 0) { queue.controller.setMediaItems(any(), any(), any()) }
        }
    }

    @Test
    fun `shuffling while the playlist copy of a song plays keeps its hand-added copy untagged`() = runTest {
        val playlistSongs = playlistOf(81)
        val duplicated = playlistSongs[5]
        val songs = playlistSongs + duplicated
        // The playlist's own copy plays and keeps its tag in place, so the copy added through
        // Play next must not be handed that tag as well.
        val queue = TaggedQueue(songs, playlistSongs.map { "playlist" } + null, currentIndex = 5)
        val playing = queue.playing

        withPlaylistTagsIn(queue.tags) {
            val holder = holderFor(queue.controller)

            holder.toggleShuffleAndWait(songs, duplicated)

            assertSame(playing, queue.items[5])
            val rebuilt = queue.entries().filterIndexed { index, _ -> index != 5 }
            assertEquals(listOf(null), rebuilt.filter { it.first == duplicated.id }.map { it.second })
            assertEquals(playlistSongs.size - 1, rebuilt.count { it.second != null })
            verify(exactly = 0) { queue.controller.setMediaItems(any(), any(), any()) }
        }
    }

    @Test
    fun `shuffling through one setMediaItems call keeps a hand-added copy of the playing song untagged`() = runTest {
        val playlistSongs = playlistOf(81)
        val duplicated = playlistSongs[5]
        val songs = playlistSongs + duplicated
        val queue = TaggedQueue(songs, playlistSongs.map { "playlist" } + null, currentIndex = 5)
        val playing = queue.playing
        // The player reports another current index once the entries around the playing one are
        // replaced, so the shuffle replaces the whole queue in one call instead.
        every { queue.controller.currentMediaItemIndex } answers {
            if (queue.segmentReplaceCount > 0) 0 else 5
        }

        withPlaylistTagsIn(queue.tags) {
            val holder = holderFor(queue.controller)

            holder.toggleShuffleAndWait(songs, duplicated)

            verify(exactly = 1) { queue.controller.setMediaItems(any(), 5, any()) }
            assertSame(playing, queue.items[5])
            val rebuilt = queue.entries().filterIndexed { index, _ -> index != 5 }
            assertEquals(listOf(null), rebuilt.filter { it.first == duplicated.id }.map { it.second })
            assertEquals(playlistSongs.size - 1, rebuilt.count { it.second != null })
        }
    }

    @Test
    fun `turning shuffle off after a cold start puts each copy's tag back at its own place`() = runTest {
        // A restored queue: the playlist plays at song-2 and song-50 was added again through
        // Play next. Nothing set an original order, so turning shuffle on takes it from the
        // player, and the queue is over 80 entries, so turning shuffle off rebuilds it.
        val playlistSongs = playlistOf(81)
        val songs = playlistSongs.take(3) + playlistSongs[50] + playlistSongs.drop(3)
        val playlistIds = songs.indices.map { index -> if (index == 3) null else "playlist" }
        val queue = TaggedQueue(songs, playlistIds, currentIndex = 2)
        val originalEntries = queue.entries()

        withPlaylistTagsIn(queue.tags) {
            val holder = holderFor(queue.controller, queueStateHolder = QueueStateHolder())

            val shuffled = holder.toggleShuffleAndWait(songs, playlistSongs[2])
            holder.toggleShuffleAndWait(shuffled, playlistSongs[2])

            assertEquals(originalEntries, queue.entries())
        }
    }

    @Test
    fun `turning shuffle off while the playlist copy of a song plays puts it back at its own place`() = runTest {
        val playlistSongs = playlistOf(81)
        val duplicated = playlistSongs[50]
        // The copy added through Play next sits at 3, the playlist's own copy at 51.
        val original = playlistSongs.take(3) + duplicated + playlistSongs.drop(3)
        val originalIds = original.indices.map { index -> if (index == 3) null else "playlist" }
        val queueStateHolder = QueueStateHolder().apply { setOriginalQueueOrder(original, originalIds) }
        // Shuffled around the playing entry, the playlist's copy, which shuffle keeps in place.
        val shuffledOrder = (original.indices - 51).reversed().toMutableList().apply { add(51, 51) }
        val shuffled = shuffledOrder.map { original[it] }
        val queue = TaggedQueue(shuffled, shuffledOrder.map { originalIds[it] }, currentIndex = 51)
        val playing = queue.playing

        withPlaylistTagsIn(queue.tags) {
            val holder = holderFor(queue.controller, queueStateHolder = queueStateHolder)
            holder.updateStablePlayerState { it.copy(isShuffleEnabled = true) }

            holder.toggleShuffleAndWait(shuffled, duplicated)

            assertEquals(original.map { it.id }.zip(originalIds), queue.entries())
            assertSame(playing, queue.items[51])
        }
    }

    @Test
    fun `turning shuffle off on a short queue moves each copy back to its own place`() = runTest {
        // 80 entries or fewer, so turning shuffle off moves the player's items instead.
        val playlistSongs = playlistOf(10)
        val duplicated = playlistSongs[6]
        // The copy added through Play next sits at 3, the playlist's own copy at 7.
        val original = playlistSongs.take(3) + duplicated + playlistSongs.drop(3)
        val originalIds = original.indices.map { index -> if (index == 3) null else "playlist" }
        val queueStateHolder = QueueStateHolder().apply { setOriginalQueueOrder(original, originalIds) }
        // Reversed, so the playlist's copy comes before the hand-added one.
        val shuffledOrder = original.indices.reversed()
        val shuffled = shuffledOrder.map { original[it] }
        val queue = TaggedQueue(shuffled, shuffledOrder.map { originalIds[it] }, currentIndex = 0)

        withPlaylistTagsIn(queue.tags) {
            val holder = holderFor(queue.controller, queueStateHolder = queueStateHolder)
            holder.updateStablePlayerState { it.copy(isShuffleEnabled = true) }

            holder.toggleShuffleAndWait(shuffled, shuffled.first())

            assertEquals(original.map { it.id }.zip(originalIds), queue.entries())
            verify(exactly = 0) { queue.controller.replaceMediaItems(any(), any(), any()) }
            verify(exactly = 0) { queue.controller.setMediaItems(any(), any(), any()) }
        }
    }
}
