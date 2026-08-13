package com.lostf1sh.pixelplayeross.data.playlist.nlp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalMetadataHeuristicsTest {

    @Test
    fun `completeMetadata cleans casing and does not hardcode Pop`() {
        val prompt = "<title>hello world</title><artist>SOME ARTIST</artist><album>demo</album>"
        val result = LocalMetadataHeuristics.completeMetadata(prompt)

        assertThat(result).contains("\"title\":\"Hello World\"")
        assertThat(result).contains("\"artist\":\"Some Artist\"")
        // With no signal we expect Unknown, never a hardcoded genre.
        assertThat(result).contains("\"genre\":\"Unknown\"")
        assertThat(result).doesNotContain("\"genre\":\"Pop\"")
    }

    @Test
    fun `completeMetadata infers genre from strong title signal`() {
        val prompt = "<title>Club Banger Remix</title><artist>DJ Someone</artist>"
        val result = LocalMetadataHeuristics.completeMetadata(prompt)
        assertThat(result).contains("\"genre\":\"Electronic\"")
    }

    @Test
    fun `generateTags varies with genre, duration and year`() {
        val rock = LocalMetadataHeuristics.generateTags(
            "<genre>rock</genre><duration>320000</duration><year>1990</year>"
        )
        assertThat(rock).contains("guitar-driven")
        assertThat(rock).contains("extended")
        assertThat(rock).contains("throwback")

        val jazz = LocalMetadataHeuristics.generateTags("<genre>jazz</genre>")
        assertThat(jazz).isNotEqualTo(rock)
        assertThat(jazz).contains("smooth")
    }

    @Test
    fun `generateTags returns at least six tags even with no fields`() {
        val tags = LocalMetadataHeuristics.generateTags("no fields here").split(", ")
        assertThat(tags.size).isAtLeast(6)
    }

    @Test
    fun `analyzeMood derives distinct vectors per genre`() {
        val metal = LocalMetadataHeuristics.analyzeMood("<genre>metal</genre>")
        val ambient = LocalMetadataHeuristics.analyzeMood("<genre>ambient</genre>")

        assertThat(metal).contains("Energy:0.9")
        assertThat(ambient).contains("Calm")
        assertThat(metal).isNotEqualTo(ambient)
    }
}
