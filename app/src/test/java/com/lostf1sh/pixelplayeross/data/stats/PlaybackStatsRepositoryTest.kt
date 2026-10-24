package com.lostf1sh.pixelplayeross.data.stats

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.ArtistRef
import com.lostf1sh.pixelplayeross.data.model.Song
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import io.mockk.every
import io.mockk.mockk
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class PlaybackStatsRepositoryTest {

    @Test
    fun `loadSummary excludes event that only touches the start boundary`() = runTest {
        val repository = createRepository()
        val zoneId = ZoneId.systemDefault()
        val now = LocalDate.of(2026, 4, 10)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

        val boundaryTouchingEvent = PlaybackStatsRepository.PlaybackEvent(
            songId = "song-1",
            timestamp = now,
            durationMs = 10_000L,
            startTimestamp = now - 10_000L,
            endTimestamp = now
        )

        val summary = repository.buildSummaryFromEvents(
            range = StatsTimeRange.DAY,
            songs = listOf(song("song-1")),
            allEvents = listOf(boundaryTouchingEvent),
            nowMillis = now
        )

        assertThat(summary.totalDurationMs).isEqualTo(0L)
        assertThat(summary.totalPlayCount).isEqualTo(0)
    }

    @Test
    fun `loadSummary preserves playback longer than track duration`() = runTest {
        val repository = createRepository()
        val zoneId = ZoneId.systemDefault()
        val start = LocalDate.of(2026, 4, 10)
            .atTime(10, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val listenedMs = TimeUnit.MINUTES.toMillis(15)
        val event = PlaybackStatsRepository.PlaybackEvent(
            songId = "song-1",
            timestamp = start + listenedMs,
            durationMs = listenedMs,
            startTimestamp = start,
            endTimestamp = start + listenedMs
        )

        val summary = repository.buildSummaryFromEvents(
            range = StatsTimeRange.DAY,
            songs = listOf(song("song-1", durationMs = TimeUnit.MINUTES.toMillis(3))),
            allEvents = listOf(event),
            nowMillis = start + listenedMs + 1_000L
        )

        assertThat(summary.totalDurationMs).isEqualTo(listenedMs)
        assertThat(summary.totalPlayCount).isEqualTo(1)
        assertThat(summary.songs.single().totalDurationMs).isEqualTo(listenedMs)
    }

    @Test
    fun `loadSummary does not count short gaps between spans as listened time`() = runTest {
        val repository = createRepository()
        val zoneId = ZoneId.systemDefault()
        val start = LocalDate.of(2026, 4, 10)
            .atTime(12, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val firstDurationMs = 10_000L
        val secondDurationMs = 10_000L
        val gapMs = 1_000L
        val events = listOf(
            PlaybackStatsRepository.PlaybackEvent(
                songId = "song-1",
                timestamp = start + firstDurationMs,
                durationMs = firstDurationMs,
                startTimestamp = start,
                endTimestamp = start + firstDurationMs
            ),
            PlaybackStatsRepository.PlaybackEvent(
                songId = "song-2",
                timestamp = start + firstDurationMs + gapMs + secondDurationMs,
                durationMs = secondDurationMs,
                startTimestamp = start + firstDurationMs + gapMs,
                endTimestamp = start + firstDurationMs + gapMs + secondDurationMs
            )
        )

        val summary = repository.buildSummaryFromEvents(
            range = StatsTimeRange.DAY,
            songs = listOf(song("song-1"), song("song-2")),
            allEvents = events,
            nowMillis = start + firstDurationMs + gapMs + secondDurationMs + 1_000L
        )

        assertThat(summary.totalDurationMs).isEqualTo(firstDurationMs + secondDurationMs)
        assertThat(summary.totalPlayCount).isEqualTo(2)
    }

    @Test
    fun `buildSummaryFromEvents uses event spans without filesystem persistence`() = runTest {
        val repository = createRepository()
        val start = LocalDate.of(2026, 4, 10)
            .atTime(9, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val durationMs = 30_000L
        val events = listOf(
            PlaybackStatsRepository.PlaybackEvent(
                songId = "song-1",
                timestamp = start + durationMs,
                durationMs = durationMs,
                startTimestamp = start,
                endTimestamp = start + durationMs
            )
        )

        val summary = repository.buildSummaryFromEvents(
            range = StatsTimeRange.DAY,
            songs = listOf(song("song-1")),
            allEvents = events,
            nowMillis = start + durationMs + 1_000L
        )

        assertThat(summary.totalDurationMs).isEqualTo(durationMs)
        assertThat(summary.uniqueSongs).isEqualTo(1)
    }

    @Test
    fun `loadSummary separates multi artist playback in top artists`() = runTest {
        val repository = createRepository()
        val zoneId = ZoneId.systemDefault()
        val start = LocalDate.of(2026, 4, 10)
            .atTime(14, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val durationMs = 60_000L
        val event = PlaybackStatsRepository.PlaybackEvent(
            songId = "song-1",
            timestamp = start + durationMs,
            durationMs = durationMs,
            startTimestamp = start,
            endTimestamp = start + durationMs
        )
        val collaboration = song(
            songId = "song-1",
            artist = "Artist A",
            artists = listOf(
                ArtistRef(id = 1L, name = "Artist A", isPrimary = true),
                ArtistRef(id = 2L, name = "Artist B", isPrimary = false)
            )
        )

        val summary = repository.buildSummaryFromEvents(
            range = StatsTimeRange.DAY,
            songs = listOf(collaboration),
            allEvents = listOf(event),
            nowMillis = start + durationMs + 1_000L
        )

        assertThat(summary.topArtists.map { it.artist })
            .containsExactly("Artist A", "Artist B")
            .inOrder()
        summary.topArtists.forEach { artist ->
            assertThat(artist.totalDurationMs).isEqualTo(durationMs)
            assertThat(artist.playCount).isEqualTo(1)
            assertThat(artist.uniqueSongs).isEqualTo(1)
        }
    }

    @Test
    fun `loadSummary counts separated artists in genre uniqueness`() = runTest {
        val repository = createRepository()
        val zoneId = ZoneId.systemDefault()
        val start = LocalDate.of(2026, 4, 10)
            .atTime(15, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val durationMs = 30_000L
        val event = PlaybackStatsRepository.PlaybackEvent(
            songId = "song-1",
            timestamp = start + durationMs,
            durationMs = durationMs,
            startTimestamp = start,
            endTimestamp = start + durationMs
        )
        val collaboration = song(
            songId = "song-1",
            artist = "Artist A",
            artists = listOf(
                ArtistRef(id = 1L, name = "Artist A", isPrimary = true),
                ArtistRef(id = 2L, name = "Artist B", isPrimary = false)
            ),
            genre = "Pop"
        )

        val summary = repository.buildSummaryFromEvents(
            range = StatsTimeRange.DAY,
            songs = listOf(collaboration),
            allEvents = listOf(event),
            nowMillis = start + durationMs + 1_000L
        )

        assertThat(summary.topGenres.single().uniqueArtists).isEqualTo(2)
    }

    @Test
    fun `loadSummary credits a multi-genre song to each of its genres`() = runTest {
        val repository = createRepository()
        val start = LocalDate.of(2026, 4, 10)
            .atTime(15, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        fun play(songId: String, offsetMs: Long, durationMs: Long) = PlaybackStatsRepository.PlaybackEvent(
            songId = songId,
            timestamp = start + offsetMs + durationMs,
            durationMs = durationMs,
            startTimestamp = start + offsetMs,
            endTimestamp = start + offsetMs + durationMs
        )

        val summary = repository.buildSummaryFromEvents(
            range = StatsTimeRange.DAY,
            songs = listOf(
                song("song-1", genre = "House; Electronic"),
                song("song-2", genre = "house"),
                song("song-3", genre = null)
            ),
            allEvents = listOf(
                play("song-1", offsetMs = 0L, durationMs = 60_000L),
                play("song-2", offsetMs = 120_000L, durationMs = 30_000L),
                play("song-3", offsetMs = 240_000L, durationMs = 10_000L)
            ),
            nowMillis = start + 600_000L
        )

        val genres = summary.topGenres.associate { it.genre to it.totalDurationMs }
        assertThat(genres).containsExactly(
            "House", 90_000L,
            "Electronic", 60_000L,
            "Unknown Genre", 10_000L
        )
        assertThat(summary.topGenres.single { it.genre == "House" }.playCount).isEqualTo(2)
    }

    @Test
    fun `hasListeningActivity is false for an empty summary and true when a play falls in range`() {
        val repository = createRepository()
        val start = LocalDate.of(2026, 4, 10)
            .atTime(9, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val now = start + 60_000L

        val empty = repository.buildSummaryFromEvents(
            range = StatsTimeRange.WEEK,
            songs = listOf(song("song-1")),
            allEvents = emptyList(),
            nowMillis = now
        )
        val active = repository.buildSummaryFromEvents(
            range = StatsTimeRange.WEEK,
            songs = listOf(song("song-1")),
            allEvents = listOf(event("song-1", start, durationMs = 30_000L)),
            nowMillis = now
        )

        assertThat(empty.hasListeningActivity()).isFalse()
        assertThat(active.hasListeningActivity()).isTrue()
    }

    @Test
    fun `month timeline week labels use the localized week string`() {
        val repository = createRepository(weekLabel = { week -> "$week. Hafta" })
        val start = LocalDate.of(2026, 4, 10)
            .atTime(9, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val summary = repository.buildSummaryFromEvents(
            range = StatsTimeRange.MONTH,
            songs = listOf(song("song-1")),
            allEvents = listOf(event("song-1", start, durationMs = 30_000L)),
            nowMillis = start + 60_000L
        )

        assertThat(summary.timeline.map { it.label })
            .containsExactly("1. Hafta", "2. Hafta", "3. Hafta", "4. Hafta")
            .inOrder()
    }

    @Test
    fun `weekday month and peak day labels follow the default locale`() {
        val turkish = Locale.forLanguageTag("tr-TR")
        withDefaultLocale(turkish) {
            val repository = createRepository()
            val zoneId = ZoneId.systemDefault()
            // Wednesday 2026-04-08, inside the week and the year that end on Friday 2026-04-10.
            val start = LocalDate.of(2026, 4, 8)
                .atTime(10, 0)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
            val now = LocalDate.of(2026, 4, 10)
                .atTime(18, 0)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
            val events = listOf(event("song-1", start, durationMs = 120_000L))

            val week = repository.buildSummaryFromEvents(
                range = StatsTimeRange.WEEK,
                songs = listOf(song("song-1")),
                allEvents = events,
                nowMillis = now
            )
            val year = repository.buildSummaryFromEvents(
                range = StatsTimeRange.YEAR,
                songs = listOf(song("song-1")),
                allEvents = events,
                nowMillis = now
            )

            assertThat(week.timeline.map { it.label })
                .containsExactlyElementsIn(
                    DayOfWeek.values().map { it.getDisplayName(TextStyle.SHORT_STANDALONE, turkish) }
                )
                .inOrder()
            assertThat(year.timeline.map { it.label })
                .containsExactlyElementsIn(
                    Month.values().map { it.getDisplayName(TextStyle.SHORT_STANDALONE, turkish) }
                )
                .inOrder()
            assertThat(week.peakDayLabel)
                .isEqualTo(DayOfWeek.WEDNESDAY.getDisplayName(TextStyle.FULL, turkish))
        }
    }

    @Test
    fun `year month labels use the standalone form rather than the in-date form`() {
        val russian = Locale.forLanguageTag("ru-RU")
        val mayInDate = Month.MAY.getDisplayName(TextStyle.SHORT, russian)
        val mayStandalone = Month.MAY.getDisplayName(TextStyle.SHORT_STANDALONE, russian)
        // Russian's in-date May is genitive ("of May"); only meaningful while the forms differ.
        assumeTrue(mayInDate != mayStandalone)
        withDefaultLocale(russian) {
            val repository = createRepository()
            val start = LocalDate.of(2026, 5, 12)
                .atTime(9, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            val year = repository.buildSummaryFromEvents(
                range = StatsTimeRange.YEAR,
                songs = listOf(song("song-1")),
                allEvents = listOf(event("song-1", start, durationMs = 30_000L)),
                nowMillis = start + 60_000L
            )

            assertThat(year.timeline.map { it.label })
                .containsExactlyElementsIn(
                    Month.values().map { it.getDisplayName(TextStyle.SHORT_STANDALONE, russian) }
                )
                .inOrder()
            assertThat(year.timeline.map { it.label }).doesNotContain(mayInDate)
        }
    }

    @Test
    fun `day timeline hour labels stay english am pm so the screen can reformat them`() {
        withDefaultLocale(Locale.forLanguageTag("tr-TR")) {
            val repository = createRepository()
            val start = LocalDate.of(2026, 4, 10)
                .atTime(9, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            val summary = repository.buildSummaryFromEvents(
                range = StatsTimeRange.DAY,
                songs = listOf(song("song-1")),
                allEvents = listOf(event("song-1", start, durationMs = 30_000L)),
                nowMillis = start + 60_000L
            )

            assertThat(summary.timeline).hasSize(6)
            summary.timeline.forEach { entry ->
                assertThat(entry.label).matches("^[0-9]{1,2}(am|pm)$")
            }
        }
    }

    private fun withDefaultLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun event(songId: String, startMillis: Long, durationMs: Long) =
        PlaybackStatsRepository.PlaybackEvent(
            songId = songId,
            timestamp = startMillis + durationMs,
            durationMs = durationMs,
            startTimestamp = startMillis,
            endTimestamp = startMillis + durationMs
        )

    private fun createRepository(
        weekLabel: (Int) -> String = { week -> "Week $week" }
    ): PlaybackStatsRepository {
        val uniqueDir = createTempDirectory(
            "playback-stats-test-${Instant.now().toEpochMilli()}-"
        ).toFile()
        val testContext = mockk<android.content.Context>(relaxed = true)
        every { testContext.filesDir } returns uniqueDir
        every {
            testContext.getString(R.string.presentation_batch_g_stats_week_label, *anyVararg())
        } answers {
            // MockK may hand the vararg over as one array argument or as separate arguments.
            val formatArgs = args.drop(1).flatMap { arg ->
                if (arg is Array<*>) arg.toList() else listOf(arg)
            }
            weekLabel(formatArgs.first() as Int)
        }
        return PlaybackStatsRepository(testContext)
    }

    private fun song(
        songId: String,
        durationMs: Long = 5 * 60 * 1000L,
        artist: String = "Artist",
        artists: List<ArtistRef> = emptyList(),
        genre: String? = null
    ): Song = Song(
        id = songId,
        title = "Song $songId",
        artist = artist,
        artistId = 1L,
        artists = artists,
        album = "Album",
        albumId = 1L,
        path = "/music/$songId.mp3",
        contentUriString = "content://media/external/audio/media/$songId",
        albumArtUriString = null,
        duration = durationMs,
        genre = genre,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )
}
