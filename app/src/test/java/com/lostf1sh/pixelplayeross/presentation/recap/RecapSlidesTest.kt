package com.lostf1sh.pixelplayeross.presentation.recap

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.stats.ListeningPersonality
import com.lostf1sh.pixelplayeross.data.stats.ListeningRecap
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository.AlbumPlaybackSummary
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository.ArtistPlaybackSummary
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository.GenrePlaybackSummary
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository.PlaybackStatsSummary
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository.SongPlaybackSummary
import com.lostf1sh.pixelplayeross.data.stats.RecapSong
import com.lostf1sh.pixelplayeross.data.stats.StatsTimeRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test

class RecapSlidesTest {

    private val hour = TimeUnit.HOURS.toMillis(1)

    @Test
    fun `a full year tells every slide in story order`() {
        val slides = buildRecapSlides(recap(), isSoFar = false, artistImages = emptyMap(), canPlayTopSongs = true)

        assertThat(slides.map { it::class }).containsExactly(
            RecapSlide.Intro::class,
            RecapSlide.Minutes::class,
            RecapSlide.Months::class,
            RecapSlide.Clock::class,
            RecapSlide.Dedication::class,
            RecapSlide.Genres::class,
            RecapSlide.Decade::class,
            RecapSlide.TopAlbums::class,
            RecapSlide.TopArtists::class,
            RecapSlide.TopSongs::class,
            RecapSlide.Summary::class
        ).inOrder()
    }

    @Test
    fun `slide keys are unique`() {
        val slides = buildRecapSlides(recap(), isSoFar = false, artistImages = emptyMap(), canPlayTopSongs = true)

        assertThat(slides.map { it.key }).containsNoDuplicates()
    }

    @Test
    fun `always includes intro minutes and summary`() {
        val bare = recap(
            summary = summary(songs = emptyList(), artists = emptyList(), albums = emptyList(), genres = emptyList()),
            monthly = List(12) { 0L },
            hourly = List(24) { 0L },
            biggestDay = null,
            topDecade = null
        )

        val slides = buildRecapSlides(bare, isSoFar = true, artistImages = emptyMap(), canPlayTopSongs = false)

        assertThat(slides.map { it::class }).containsExactly(
            RecapSlide.Intro::class,
            RecapSlide.Minutes::class,
            RecapSlide.Summary::class
        ).inOrder()
        val summary = slides.last() as RecapSlide.Summary
        assertThat(summary.isSoFar).isTrue()
        assertThat(summary.canPlayTopSongs).isFalse()
        assertThat(summary.topArtist).isNull()
    }

    @Test
    fun `omits genres slide when only unknown genre`() {
        val onlyUnknown = recap(
            summary = summary(genres = listOf(genre(PlaybackStatsRepository.UNKNOWN_GENRE, 3 * hour)))
        )

        val slides = buildRecapSlides(onlyUnknown, isSoFar = false, artistImages = emptyMap(), canPlayTopSongs = true)

        assertThat(slides.none { it is RecapSlide.Genres }).isTrue()
        assertThat((slides.last() as RecapSlide.Summary).topGenre).isNull()
    }

    @Test
    fun `cloud source placeholders are left out of the genre ranking and the summary`() {
        // What the Jellyfin and Navidrome sync store for songs the server sent without a genre.
        val cloudLed = recap(
            summary = summary(
                genres = listOf(
                    genre("Jellyfin", 40 * hour),
                    genre("Navidrome", 35 * hour),
                    genre("Indie Rock", 30 * hour),
                    genre("Electronic", 10 * hour)
                )
            )
        )

        val slides = buildRecapSlides(cloudLed, isSoFar = false, artistImages = emptyMap(), canPlayTopSongs = true)

        val genres = slides.filterIsInstance<RecapSlide.Genres>().single().genres
        assertThat(genres.map { it.name }).containsExactly("Indie Rock", "Electronic").inOrder()
        assertThat((slides.last() as RecapSlide.Summary).topGenre).isEqualTo("Indie Rock")
    }

    @Test
    fun `omits genres slide when only cloud source placeholders and unknown genre`() {
        val onlyPlaceholders = recap(
            summary = summary(
                genres = listOf(
                    genre("jellyfin", 5 * hour),
                    genre(PlaybackStatsRepository.UNKNOWN_GENRE, 4 * hour),
                    genre("NAVIDROME", 3 * hour)
                )
            )
        )

        val slides = buildRecapSlides(onlyPlaceholders, isSoFar = false, artistImages = emptyMap(), canPlayTopSongs = true)

        assertThat(slides.none { it is RecapSlide.Genres }).isTrue()
        assertThat((slides.last() as RecapSlide.Summary).topGenre).isNull()
    }

    @Test
    fun `unknown buckets are left out of the rankings`() {
        val withUnknowns = recap(
            summary = summary(
                artists = listOf(
                    ArtistPlaybackSummary(PlaybackStatsRepository.UNKNOWN_ARTIST, 9 * hour, 90, 30),
                    ArtistPlaybackSummary("Aurora Fields", 4 * hour, 40, 6)
                ),
                albums = listOf(
                    AlbumPlaybackSummary(PlaybackStatsRepository.UNKNOWN_ALBUM, null, 9 * hour, 90, 30),
                    AlbumPlaybackSummary("Low Tide", "content://art/low-tide", 2 * hour, 20, 3)
                )
            )
        )

        val slides = buildRecapSlides(
            withUnknowns,
            isSoFar = false,
            artistImages = mapOf("Aurora Fields" to "content://artist/aurora"),
            canPlayTopSongs = true
        )

        val artists = slides.filterIsInstance<RecapSlide.TopArtists>().single().artists
        assertThat(artists.map { it.name }).containsExactly("Aurora Fields")
        assertThat(artists.single().imageUri).isEqualTo("content://artist/aurora")
        val albums = slides.filterIsInstance<RecapSlide.TopAlbums>().single().albums
        assertThat(albums.map { it.title }).containsExactly("Low Tide")
    }

    @Test
    fun `omits top artist slide when only unknown artist`() {
        val onlyUnknown = recap(
            summary = summary(
                artists = listOf(ArtistPlaybackSummary(PlaybackStatsRepository.UNKNOWN_ARTIST, 9 * hour, 90, 30))
            )
        )

        val slides = buildRecapSlides(onlyUnknown, isSoFar = false, artistImages = emptyMap(), canPlayTopSongs = true)

        assertThat(slides.none { it is RecapSlide.TopArtists }).isTrue()
    }

    @Test
    fun `omits decade slide without release years`() {
        val slides = buildRecapSlides(
            recap(topDecade = null),
            isSoFar = false,
            artistImages = emptyMap(),
            canPlayTopSongs = true
        )

        assertThat(slides.none { it is RecapSlide.Decade }).isTrue()
    }

    @Test
    fun `song artists fall back to none instead of the unknown bucket name`() {
        val slides = buildRecapSlides(
            recap(
                summary = summary(
                    songs = listOf(
                        SongPlaybackSummary("1", "Morning Static", PlaybackStatsRepository.UNKNOWN_ARTIST, null, hour, 12)
                    )
                )
            ),
            isSoFar = false,
            artistImages = emptyMap(),
            canPlayTopSongs = true
        )

        val topSong = slides.filterIsInstance<RecapSlide.TopSongs>().single().songs.single()
        assertThat(topSong.artist).isNull()
    }

    @Test
    fun `minutes slide shows the Stats year view's daily average and active days`() {
        // The year view averages up to the last day with music, here not the recap's last day.
        val slides = buildRecapSlides(
            recap(summary = summary(totalMs = 300 * hour, averageDailyMs = 2 * hour), daysInRange = 296),
            isSoFar = true,
            artistImages = emptyMap(),
            canPlayTopSongs = true
        )

        val minutes = slides.filterIsInstance<RecapSlide.Minutes>().single()
        assertThat(minutes.totalMs).isEqualTo(300 * hour)
        assertThat(minutes.averageDailyMs).isEqualTo(2 * hour)
        assertThat(minutes.activeDays).isEqualTo(120)
    }

    @Test
    fun `genre shares are relative to all listening`() {
        val slides = buildRecapSlides(
            recap(summary = summary(totalMs = 10 * hour, genres = listOf(genre("Indie Rock", 4 * hour)))),
            isSoFar = false,
            artistImages = emptyMap(),
            canPlayTopSongs = true
        )

        val genres = slides.filterIsInstance<RecapSlide.Genres>().single().genres
        assertThat(genres.single().share).isWithin(0.001f).of(0.4f)
    }

    private fun recap(
        summary: PlaybackStatsSummary = summary(),
        monthly: List<Long> = List(12) { if (it == 6) 50 * hour else hour },
        hourly: List<Long> = List(24) { if (it == 23) 30 * hour else hour },
        biggestDay: LocalDate? = LocalDate.of(2025, 7, 4),
        topDecade: Int? = 1990,
        daysInRange: Int = 365
    ) = ListeningRecap(
        year = 2025,
        isYearComplete = true,
        summary = summary,
        daysInRange = daysInRange,
        uniqueArtists = 12,
        uniqueAlbums = 9,
        monthlyDurationsMs = monthly,
        hourlyDurationsMs = hourly,
        personality = ListeningPersonality.NIGHT_OWL,
        favoriteDayOfWeek = DayOfWeek.SATURDAY,
        biggestDay = biggestDay,
        biggestDayDurationMs = 5 * hour,
        firstSong = RecapSong("1", "Morning Static", "Aurora Fields", null),
        topDecade = topDecade,
        topDecadeShare = 0.6f
    )

    private fun summary(
        totalMs: Long = 100 * hour,
        averageDailyMs: Long = 0L,
        songs: List<SongPlaybackSummary> = listOf(
            SongPlaybackSummary("1", "Morning Static", "Aurora Fields", null, 9 * hour, 80),
            SongPlaybackSummary("2", "Low Tide", "Aurora Fields feat. Mira", null, 5 * hour, 40)
        ),
        artists: List<ArtistPlaybackSummary> = listOf(ArtistPlaybackSummary("Aurora Fields", 20 * hour, 200, 6)),
        albums: List<AlbumPlaybackSummary> = listOf(AlbumPlaybackSummary("Morning Static", null, 12 * hour, 100, 3)),
        genres: List<GenrePlaybackSummary> = listOf(genre("Indie Rock", 30 * hour), genre("Electronic", 10 * hour))
    ) = PlaybackStatsSummary(
        range = StatsTimeRange.YEAR,
        startTimestamp = 0L,
        endTimestamp = 1L,
        totalDurationMs = totalMs,
        totalPlayCount = 500,
        uniqueSongs = 42,
        averageDailyDurationMs = averageDailyMs,
        songs = songs,
        topSongs = songs,
        topGenres = genres,
        timeline = emptyList(),
        topArtists = artists,
        topAlbums = albums,
        activeDays = 120,
        longestStreakDays = 10,
        totalSessions = 200,
        averageSessionDurationMs = hour / 2,
        longestSessionDurationMs = 3 * hour,
        averageSessionsPerDay = 1.0,
        dayListeningDistribution = null,
        peakTimeline = null,
        peakDayLabel = null,
        peakDayDurationMs = 0L
    )

    private fun genre(name: String, durationMs: Long) =
        GenrePlaybackSummary(genre = name, totalDurationMs = durationMs, playCount = 10, uniqueArtists = 2)
}
