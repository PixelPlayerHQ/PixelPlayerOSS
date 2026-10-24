package com.lostf1sh.pixelplayeross.presentation.recap

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository.PlaybackEvent
import com.lostf1sh.pixelplayeross.data.stats.StatsTimeRange
import com.lostf1sh.pixelplayeross.utils.GenreParser
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.Test

/**
 * The running year's recap against the Stats screen's Year view, both built by the real
 * aggregation from one history: every figure the story shows must be the year view's.
 */
class RecapStatsParityTest {

    private val zone = ZoneId.of("America/New_York")

    /** The last play below is five days before this, so the two "last days" differ. */
    private val now = LocalDateTime.of(2026, 10, 23, 12, 0)

    private val repository = PlaybackStatsRepository(
        mockk(relaxed = true) {
            every { filesDir } returns createTempDirectory("recap-stats-parity-test-").toFile()
            every { getString(R.string.presentation_batch_g_stats_week_label, *anyVararg()) } returns "Week"
        }
    )

    private val songs = listOf(
        song("rock", artist = "Aurora Fields", album = "Northern Lines", genre = "Indie Rock"),
        song("alt", artist = "Aurora Fields", album = "Quiet Hours", genre = "Alternative; Indie Rock"),
        song("house", artist = "Neon Duo", album = "Bones", genre = "Electronic/House"),
        song("jazz", artist = "Blue Quartet", album = "Blue Notes", genre = "Jazz"),
        song("untagged", artist = "", album = "", genre = null)
    )

    private val events = listOf(
        // Runs over New Year: only the part after midnight belongs to 2026.
        play("rock", LocalDateTime.of(2025, 12, 31, 23, 40), minutes(50)),
        play("rock", LocalDateTime.of(2026, 1, 3, 8, 0), minutes(47)),
        play("alt", LocalDateTime.of(2026, 3, 14, 21, 10), minutes(83)),
        play("house", LocalDateTime.of(2026, 3, 15, 22, 0), minutes(61)),
        // A crossfade overlapping the previous play: counted once in the totals.
        play("jazz", LocalDateTime.of(2026, 3, 15, 22, 58), minutes(19)),
        play("untagged", LocalDateTime.of(2026, 6, 2, 7, 30), minutes(12)),
        play("deleted", LocalDateTime.of(2026, 7, 9, 18, 0), minutes(25)),
        play("rock", LocalDateTime.of(2026, 10, 16, 19, 0), minutes(33)),
        play("alt", LocalDateTime.of(2026, 10, 17, 19, 5), minutes(28)),
        play("house", LocalDateTime.of(2026, 10, 18, 6, 45), minutes(17))
    )

    private val nowMillis = millis(now)

    private val yearView = repository.buildSummaryFromEvents(
        range = StatsTimeRange.YEAR,
        songs = songs,
        nowMillis = nowMillis,
        allEvents = events,
        zoneId = zone
    )

    private val slides = buildRecapSlides(
        recap = repository.buildRecapFromEvents(
            year = 2026,
            songs = songs,
            nowMillis = nowMillis,
            allEvents = events,
            zoneId = zone
        )!!,
        isSoFar = true,
        artistImages = emptyMap(),
        canPlayTopSongs = true
    )

    private inline fun <reified T : RecapSlide> slide(): T = slides.filterIsInstance<T>().single()

    @Test
    fun `listening time, daily average and active days match the year view`() {
        val minutes = slide<RecapSlide.Minutes>()

        assertThat(minutes.totalMs).isEqualTo(yearView.totalDurationMs)
        assertThat(minutes.averageDailyMs).isEqualTo(yearView.averageDailyDurationMs)
        assertThat(minutes.activeDays).isEqualTo(yearView.activeDays)
        assertThat(slide<RecapSlide.Summary>().totalMs).isEqualTo(yearView.totalDurationMs)
    }

    @Test
    fun `months, streak and longest session match the year view`() {
        val months = slide<RecapSlide.Months>()
        val dedication = slide<RecapSlide.Dedication>()

        assertThat(months.monthlyMs).containsExactlyElementsIn(yearView.timeline.map { it.totalDurationMs }).inOrder()
        assertThat(months.monthlyMs[months.peakMonthIndex]).isEqualTo(yearView.peakTimeline?.totalDurationMs)
        assertThat(dedication.longestStreakDays).isEqualTo(yearView.longestStreakDays)
        assertThat(dedication.longestSessionMs).isEqualTo(yearView.longestSessionDurationMs)
    }

    @Test
    fun `top lists show the year view's entries and listened time`() {
        // The recap looks deeper so its list stays full once the unknown bucket (and, for
        // genres, the cloud source placeholders) is left out; everything else the year view
        // lists must lead it unchanged.
        assertLeads(
            recap = slide<RecapSlide.TopArtists>().artists.map { it.name to it.durationMs },
            yearView = yearView.topArtists
                .filter { it.artist != PlaybackStatsRepository.UNKNOWN_ARTIST }
                .map { it.artist to it.totalDurationMs }
        )
        assertLeads(
            recap = slide<RecapSlide.TopAlbums>().albums.map { it.title to it.durationMs },
            yearView = yearView.topAlbums
                .filter { it.album != PlaybackStatsRepository.UNKNOWN_ALBUM }
                .map { it.album to it.totalDurationMs }
        )
        assertLeads(
            recap = slide<RecapSlide.Genres>().genres.map { it.name to it.durationMs },
            yearView = yearView.topGenres
                .filter { it.genre != PlaybackStatsRepository.UNKNOWN_GENRE && !GenreParser.isSourcePlaceholder(it.genre) }
                .map { it.genre to it.totalDurationMs }
        )
        val topSongs = slide<RecapSlide.TopSongs>()
        assertLeads(
            recap = topSongs.songs.map { it.songId to it.durationMs },
            yearView = yearView.topSongs.map { it.songId to it.totalDurationMs }
        )
        assertThat(topSongs.uniqueSongs).isEqualTo(yearView.uniqueSongs)
    }

    private fun assertLeads(recap: List<Pair<String, Long>>, yearView: List<Pair<String, Long>>) {
        assertThat(yearView).isNotEmpty()
        assertThat(recap.take(yearView.size)).containsExactlyElementsIn(yearView).inOrder()
    }

    private fun millis(dateTime: LocalDateTime): Long = dateTime.atZone(zone).toInstant().toEpochMilli()

    private fun minutes(value: Long): Long = TimeUnit.MINUTES.toMillis(value)

    private fun play(songId: String, start: LocalDateTime, durationMs: Long): PlaybackEvent {
        val startMillis = millis(start)
        return PlaybackEvent(
            songId = songId,
            timestamp = startMillis + durationMs,
            durationMs = durationMs,
            startTimestamp = startMillis,
            endTimestamp = startMillis + durationMs
        )
    }

    private fun song(songId: String, artist: String, album: String, genre: String?): Song = Song(
        id = songId,
        title = "Song $songId",
        artist = artist,
        artistId = artist.hashCode().toLong(),
        album = album,
        albumId = album.hashCode().toLong(),
        path = "/music/$songId.mp3",
        contentUriString = "content://media/external/audio/media/$songId",
        albumArtUriString = null,
        duration = TimeUnit.MINUTES.toMillis(4),
        genre = genre,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )
}
