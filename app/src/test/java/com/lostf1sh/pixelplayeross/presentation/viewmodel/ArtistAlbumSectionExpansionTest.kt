package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ArtistAlbumSectionExpansionTest {

    @Test
    fun `albums open expanded when the collapsed default is off`() {
        assertThat(
            isAlbumSectionExpanded("a", overrides = emptyMap(), collapsedByDefault = false, sectionCount = 5)
        ).isTrue()
    }

    @Test
    fun `albums open collapsed when the collapsed default is on`() {
        assertThat(
            isAlbumSectionExpanded("a", overrides = emptyMap(), collapsedByDefault = true, sectionCount = 5)
        ).isFalse()
    }

    @Test
    fun `the only album of an artist opens expanded even with the collapsed default on`() {
        assertThat(
            isAlbumSectionExpanded("a", overrides = emptyMap(), collapsedByDefault = true, sectionCount = 1)
        ).isTrue()
    }

    @Test
    fun `a toggle made on the page wins over either default`() {
        val overrides = mapOf("open" to true, "closed" to false)

        listOf(true, false).forEach { collapsedByDefault ->
            assertThat(isAlbumSectionExpanded("open", overrides, collapsedByDefault, sectionCount = 3)).isTrue()
            assertThat(isAlbumSectionExpanded("closed", overrides, collapsedByDefault, sectionCount = 3)).isFalse()
        }
        // A single album the user collapsed stays collapsed.
        assertThat(isAlbumSectionExpanded("closed", overrides, collapsedByDefault = true, sectionCount = 1)).isFalse()
    }

    @Test
    fun `a toggle on one album leaves the others on the default`() {
        val overrides = mapOf("b" to true)

        assertThat(isAlbumSectionExpanded("a", overrides, collapsedByDefault = true, sectionCount = 3)).isFalse()
        assertThat(isAlbumSectionExpanded("b", overrides, collapsedByDefault = true, sectionCount = 3)).isTrue()
    }

    @Test
    fun `any album counts as expanded while the defaults leave them open`() {
        val keys = listOf("a", "b", "c")

        assertThat(isAnyAlbumSectionExpanded(keys, overrides = emptyMap(), collapsedByDefault = false)).isTrue()
        // The only album opens even with the collapsed default on.
        assertThat(isAnyAlbumSectionExpanded(listOf("a"), overrides = emptyMap(), collapsedByDefault = true)).isTrue()
    }

    @Test
    fun `no album counts as expanded when every album starts or was set collapsed`() {
        val keys = listOf("a", "b", "c")
        val allCollapsed = keys.associateWith { false }

        assertThat(isAnyAlbumSectionExpanded(keys, overrides = emptyMap(), collapsedByDefault = true)).isFalse()
        assertThat(isAnyAlbumSectionExpanded(keys, allCollapsed, collapsedByDefault = false)).isFalse()
        assertThat(isAnyAlbumSectionExpanded(emptyList(), overrides = emptyMap(), collapsedByDefault = false)).isFalse()
    }

    @Test
    fun `one album opened on the page counts as expanded`() {
        val keys = listOf("a", "b", "c")

        assertThat(isAnyAlbumSectionExpanded(keys, mapOf("b" to true), collapsedByDefault = true)).isTrue()
        // Overrides left over for albums that are not listed do not count.
        assertThat(isAnyAlbumSectionExpanded(keys, mapOf("gone" to true), collapsedByDefault = true)).isFalse()
    }

    @Test
    fun `albums sharing an id but not a title get their own key`() {
        val first = section(albumId = 7L, title = "Live")
        val second = section(albumId = 7L, title = "Live (Deluxe)")

        assertThat(first.collapseKey()).isNotEqualTo(second.collapseKey())
        assertThat(first.collapseKey()).isEqualTo(section(albumId = 7L, title = "Live").collapseKey())
    }

    private fun section(albumId: Long, title: String) = ArtistAlbumSection(
        albumId = albumId,
        title = title,
        year = null,
        albumArtUriString = null,
        songs = emptyList()
    )
}
