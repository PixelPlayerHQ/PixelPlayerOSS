package com.lostf1sh.pixelplayeross.data.stats

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.ArtistRef
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.utils.GenreParser
import io.mockk.every
import io.mockk.mockk
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.Test

class PlaybackStatsRepositoryRecapTest {

    private val zone = ZoneId.of("America/New_York")
    private val repository = PlaybackStatsRepository(
        mockk(relaxed = true) {
            every { filesDir } returns createTempDirectory("playback-stats-recap-test-").toFile()
            every { getString(R.string.presentation_batch_g_stats_week_label, *anyVararg()) } returns "Week"
        }
    )

    private fun millis(dateTime: LocalDateTime): Long = dateTime.atZone(zone).toInstant().toEpochMilli()

    private fun minutes(value: Long): Long = TimeUnit.MINUTES.toMillis(value)

    private fun play(songId: String, start: LocalDateTime, durationMs: Long): PlaybackStatsRepository.PlaybackEvent {
        val startMillis = millis(start)
        return PlaybackStatsRepository.PlaybackEvent(
            songId = songId,
            timestamp = startMillis + durationMs,
            durationMs = durationMs,
            startTimestamp = startMillis,
            endTimestamp = startMillis + durationMs
        )
    }

    private fun recap(
        year: Int,
        events: List<PlaybackStatsRepository.PlaybackEvent>,
        songs: List<Song> = emptyList(),
        now: LocalDateTime = LocalDateTime.of(2026, 2, 1, 12, 0)
    ): ListeningRecap? = repository.buildRecapFromEvents(
        year = year,
        songs = songs,
        nowMillis = millis(now),
        allEvents = events,
        zoneId = zone
    )

    @Test
    fun `buildRecapFromEvents clips a play spanning new year to the recap year`() {
        val events = listOf(play("song-1", LocalDateTime.of(2025, 12, 31, 23, 50), minutes(20)))

        val endingYear = recap(2025, events)!!
        val newYear = recap(2026, events)!!

        // The ending year runs up to and including its last millisecond.
        assertThat(endingYear.summary.totalDurationMs).isAtLeast(minutes(10) - 1)
        assertThat(endingYear.summary.totalDurationMs).isAtMost(minutes(10))
        assertThat(newYear.summary.totalDurationMs).isEqualTo(minutes(10))
    }

    @Test
    fun `past year recap covers the full year with twelve monthly buckets`() {
        val events = listOf(
            play("song-1", LocalDateTime.of(2025, 1, 10, 9, 0), minutes(30)),
            play("song-1", LocalDateTime.of(2025, 7, 4, 20, 0), minutes(90)),
            play("song-2", LocalDateTime.of(2025, 7, 5, 20, 0), minutes(45)),
            play("song-2", LocalDateTime.of(2025, 12, 31, 22, 0), minutes(15))
        )

        val result = recap(2025, events, songs = listOf(song("song-1"), song("song-2")))!!

        assertThat(result.isYearComplete).isTrue()
        assertThat(result.daysInRange).isEqualTo(365)
        assertThat(result.monthlyDurationsMs).hasSize(12)
        assertThat(result.monthlyDurationsMs.sum()).isEqualTo(result.summary.totalDurationMs)
        assertThat(result.monthlyDurationsMs[6]).isEqualTo(minutes(135))
        assertThat(peakIndex(result.monthlyDurationsMs)).isEqualTo(6)
        assertThat(result.hourlyDurationsMs).hasSize(24)
        assertThat(result.hourlyDurationsMs.sum()).isEqualTo(result.summary.totalDurationMs)
    }

    @Test
    fun `current year recap is marked incomplete and ends at now`() {
        val now = LocalDateTime.of(2026, 3, 15, 12, 0)
        val events = listOf(
            play("song-1", LocalDateTime.of(2026, 3, 1, 10, 0), minutes(40)),
            // Recorded later than "now": a recap made at noon cannot include it.
            play("song-1", LocalDateTime.of(2026, 3, 15, 18, 0), minutes(40))
        )

        val result = recap(2026, events, songs = listOf(song("song-1")), now = now)!!

        assertThat(result.isYearComplete).isFalse()
        assertThat(result.daysInRange).isEqualTo(LocalDate.of(2026, 3, 15).dayOfYear)
        assertThat(result.summary.totalDurationMs).isEqualTo(minutes(40))
    }

    @Test
    fun `buildRecapFromEvents returns null for a future year`() {
        assertThat(recap(2027, emptyList())).isNull()
    }

    @Test
    fun `unique artists counts separated multi-artist songs once and ignores unknown artist`() {
        val songs = listOf(
            song(
                "duet",
                artists = listOf(
                    ArtistRef(id = 1L, name = "Aurora Fields", isPrimary = true),
                    ArtistRef(id = 2L, name = "Mira", isPrimary = false)
                )
            ),
            song("solo", artist = "aurora fields"),
            song("other", artist = "Blue Quartet")
        )
        val events = listOf(
            play("duet", LocalDateTime.of(2025, 5, 1, 10, 0), minutes(4)),
            play("solo", LocalDateTime.of(2025, 5, 2, 10, 0), minutes(4)),
            play("other", LocalDateTime.of(2025, 5, 3, 10, 0), minutes(4)),
            // No longer in the library: lands in the repository's unknown-artist bucket.
            play("deleted", LocalDateTime.of(2025, 5, 4, 10, 0), minutes(4))
        )

        val result = recap(2025, events, songs = songs)!!

        assertThat(result.uniqueArtists).isEqualTo(3)
        assertThat(result.uniqueAlbums).isEqualTo(3)
    }

    @Test
    fun `first song is the earliest play of the year that is still in the library`() {
        val events = listOf(
            play("deleted", LocalDateTime.of(2025, 1, 1, 0, 5), minutes(3)),
            play("kept", LocalDateTime.of(2025, 1, 1, 0, 9), minutes(3)),
            play("later", LocalDateTime.of(2025, 1, 2, 9, 0), minutes(3)),
            // Last year's plays are outside the recap.
            play("older", LocalDateTime.of(2024, 12, 31, 12, 0), minutes(3))
        )

        val result = recap(2025, events, songs = listOf(song("kept"), song("later"), song("older")))!!

        assertThat(result.firstSong?.songId).isEqualTo("kept")
        assertThat(result.firstSong?.title).isEqualTo("Song kept")
    }

    @Test
    fun `biggest day and favorite weekday follow listened time`() {
        val events = listOf(
            // Saturday 2025-03-08: 2 h. Wednesday 2025-03-12: 1 h, Wednesday 2025-03-19: 1.5 h.
            play("song-1", LocalDateTime.of(2025, 3, 8, 14, 0), minutes(120)),
            play("song-1", LocalDateTime.of(2025, 3, 12, 14, 0), minutes(60)),
            play("song-1", LocalDateTime.of(2025, 3, 19, 14, 0), minutes(90))
        )

        val result = recap(2025, events, songs = listOf(song("song-1")))!!

        assertThat(result.biggestDay).isEqualTo(LocalDate.of(2025, 3, 8))
        assertThat(result.biggestDayDurationMs).isEqualTo(minutes(120))
        assertThat(result.favoriteDayOfWeek).isEqualTo(DayOfWeek.WEDNESDAY)
    }

    @Test
    fun `personality and top decade come from the year's plays`() {
        val songs = listOf(song("nineties", year = 1996), song("recent", year = 2021))
        val events = listOf(
            play("nineties", LocalDateTime.of(2025, 4, 1, 23, 0), minutes(120)),
            play("recent", LocalDateTime.of(2025, 4, 2, 15, 0), minutes(30))
        )

        val result = recap(2025, events, songs = songs)!!

        assertThat(result.personality).isEqualTo(ListeningPersonality.NIGHT_OWL)
        assertThat(result.topDecade).isEqualTo(1990)
        assertThat(result.topDecadeShare).isWithin(0.001f).of(0.8f)
    }

    @Test
    fun `recap top lists keep five named entries when the unknown bucket ranks among them`() {
        val songs = (1..5).map { song("tagged-$it", genre = "Genre $it") }
        val events = songs.mapIndexed { index, song ->
            play(song.id, LocalDateTime.of(2025, 2, 1 + index, 10, 0), minutes(10L - index))
        } +
            // No longer in the library: the unknown artist, album and genre all lead their lists.
            play("deleted", LocalDateTime.of(2025, 2, 10, 10, 0), minutes(60))

        val summary = recap(2025, events, songs = songs)!!.summary

        assertThat(summary.topArtists.map { it.artist }.filter { it != PlaybackStatsRepository.UNKNOWN_ARTIST })
            .containsExactly("Artist tagged-1", "Artist tagged-2", "Artist tagged-3", "Artist tagged-4", "Artist tagged-5")
            .inOrder()
        assertThat(summary.topAlbums.map { it.album }.filter { it != PlaybackStatsRepository.UNKNOWN_ALBUM })
            .hasSize(5)
        assertThat(summary.topGenres.map { it.genre }.filter { it != PlaybackStatsRepository.UNKNOWN_GENRE })
            .hasSize(5)
    }

    @Test
    fun `recap genre list keeps five named genres when the cloud placeholders and unknown bucket lead it`() {
        val tagged = (1..5).map { song("tagged-$it", genre = "Genre $it") }
        val songs = tagged + song("jellyfin", genre = "Jellyfin") + song("navidrome", genre = "Navidrome")
        val events = tagged.mapIndexed { index, song ->
            play(song.id, LocalDateTime.of(2025, 2, 1 + index, 10, 0), minutes(10L - index))
        } +
            play("jellyfin", LocalDateTime.of(2025, 2, 8, 10, 0), minutes(60)) +
            play("navidrome", LocalDateTime.of(2025, 2, 9, 10, 0), minutes(55)) +
            // No longer in the library: counted under the unknown genre.
            play("deleted", LocalDateTime.of(2025, 2, 10, 10, 0), minutes(50))

        val summary = recap(2025, events, songs = songs)!!.summary

        assertThat(
            summary.topGenres
                .map { it.genre }
                .filter { it != PlaybackStatsRepository.UNKNOWN_GENRE && !GenreParser.isSourcePlaceholder(it) }
        ).containsExactly("Genre 1", "Genre 2", "Genre 3", "Genre 4", "Genre 5").inOrder()
    }

    @Test
    fun `recapYearTotals splits listening by local calendar year and merges overlapping events`() {
        val events = listOf(
            play("song-1", LocalDateTime.of(2024, 6, 1, 10, 0), minutes(30)),
            // Two overlapping events (e.g. a crossfade) count once.
            play("song-1", LocalDateTime.of(2025, 6, 1, 10, 0), minutes(30)),
            play("song-2", LocalDateTime.of(2025, 6, 1, 10, 20), minutes(30)),
            play("song-3", LocalDateTime.of(2025, 12, 31, 23, 45), minutes(30))
        )

        val totals = repository.recapYearTotals(events, zone)

        assertThat(totals).containsExactly(
            RecapYearTotal(year = 2024, listenedMs = minutes(30)),
            RecapYearTotal(year = 2025, listenedMs = minutes(50) + minutes(15)),
            RecapYearTotal(year = 2026, listenedMs = minutes(15))
        ).inOrder()
    }

    private fun song(
        songId: String,
        artist: String = "Artist $songId",
        artists: List<ArtistRef> = emptyList(),
        year: Int = 0,
        genre: String? = null
    ): Song = Song(
        id = songId,
        title = "Song $songId",
        artist = artist,
        artistId = 1L,
        artists = artists,
        album = "Album $songId",
        albumId = songId.hashCode().toLong(),
        path = "/music/$songId.mp3",
        contentUriString = "content://media/external/audio/media/$songId",
        albumArtUriString = null,
        duration = TimeUnit.MINUTES.toMillis(4),
        year = year,
        genre = genre,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )
}
