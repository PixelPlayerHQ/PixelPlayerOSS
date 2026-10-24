package com.lostf1sh.pixelplayeross.presentation.settings.search

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.presentation.model.SettingsCategory
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

/**
 * Settings search only finds what the registry declares, and a result only highlights
 * its row when the route carries the right item key. These checks keep the registry
 * consistent as settings are added.
 */
class SettingsRegistryTest {

    private val settings = SettingsRegistry.allSettings

    private fun spec(id: String): SettingSpec = settings.single { it.id == id }

    @Test
    fun `every setting has a unique id`() {
        val duplicates = settings.groupBy { it.id }.filterValues { it.size > 1 }.keys

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `every setting has a unique item key`() {
        // The item key is what a result carries into the destination to highlight the row;
        // a duplicate would highlight the wrong setting.
        val duplicates = settings.groupBy { it.itemKey }.filterValues { it.size > 1 }.keys

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `every setting has a title source`() {
        val untitled = settings.filter { it.titleStatic == null && it.titleRes == null }

        assertThat(untitled.map { it.id }).isEmpty()
    }

    @Test
    fun `every setting has a navigable route`() {
        val routeless = settings.filter { it.subscreenRoute.isBlank() }

        assertThat(routeless.map { it.id }).isEmpty()
    }

    @Test
    fun `every switch can be read and toggled from the search results`() {
        // Switch results render an inline toggle; without both callbacks the row would read
        // as off and do nothing when tapped.
        val incomplete = settings
            .filter { it.type == SettingType.SWITCH }
            .filter { it.getValue == null || it.onToggle == null }

        assertThat(incomplete.map { it.id }).isEmpty()
    }

    @Test
    fun `only switches carry toggle callbacks`() {
        val misdeclared = settings
            .filter { it.type != SettingType.SWITCH }
            .filter { it.getValue != null || it.onToggle != null }

        assertThat(misdeclared.map { it.id }).isEmpty()
    }

    @Test
    fun `highlight key is appended to the route as a single query parameter`() {
        settings.forEach { spec ->
            val route = spec.createNavigationRoute()

            assertThat(route).startsWith(spec.subscreenRoute.substringBefore('?'))
            assertThat(route).contains("highlightKey=${spec.itemKey}")
            // A raw `.route` would leave "{highlightKey}" behind and add a second key.
            assertThat(route).doesNotContain("{")
            assertThat(route.split("highlightKey=")).hasSize(2)
        }
    }

    @Test
    fun `artist settings rows route to the artist page with their own highlight key`() {
        val expected = mapOf(
            "artist_char_delimiters" to "artist_settings?highlightKey=item_artist_char_delimiters",
            "artist_word_delimiters" to "artist_settings?highlightKey=item_artist_word_delimiters",
            "artist_extract_from_title" to "artist_settings?highlightKey=item_artist_extract_from_title",
            "artist_group_by_album_artist" to "artist_settings?highlightKey=item_artist_group_by_album_artist",
            "artist_albums_collapsed_by_default" to
                "artist_settings?highlightKey=item_artist_albums_collapsed_by_default",
        )

        expected.forEach { (id, route) ->
            val spec = spec(id)
            assertThat(spec.category).isEqualTo(SettingsCategory.LIBRARY)
            assertThat(spec.subscreenRoute).isEqualTo(Screen.ArtistSettings.createRoute())
            assertThat(spec.createNavigationRoute()).isEqualTo(route)
        }
    }

    @Test
    fun `listening stats is an action found by statistics keywords`() {
        val stats = spec("listening_stats")

        assertThat(stats.type).isEqualTo(SettingType.ACTION)
        assertThat(stats.category).isEqualTo(SettingsCategory.LIBRARY)
        assertThat(stats.keywordsStatic).containsAtLeast("stats", "statistics")
        assertThat(stats.onAction).isNotNull()
    }

    @Test
    fun `listening stats result opens the stats route without a highlight query`() {
        // The stats destination declares no highlightKey argument, so the result must not
        // fall back to createNavigationRoute().
        val navController = mockk<NavController>(relaxed = true)
        every { navController.currentBackStackEntry?.lifecycle?.currentState } returns
            Lifecycle.State.RESUMED

        spec("listening_stats").onAction!!.invoke(navController)

        verify(exactly = 1) { navController.navigate(Screen.Stats.route, any<NavOptionsBuilder.() -> Unit>()) }
    }

    @Test
    fun `previous button setting is a behavior card found by rewind keywords`() {
        val previousButton = spec("behavior_previous_button")

        assertThat(previousButton.type).isEqualTo(SettingType.NAVIGABLE_CARD)
        assertThat(previousButton.category).isEqualTo(SettingsCategory.BEHAVIOR)
        assertThat(previousButton.itemKey).isEqualTo("item_behavior_previous_button")
        assertThat(previousButton.keywordsStatic).containsAtLeast("previous", "rewind", "restart")
    }

    @Test
    fun `every user facing category is represented`() {
        // A category with no registered setting is invisible to search.
        val covered = settings.map { it.category }.toSet()

        assertThat(covered).containsAtLeastElementsIn(SettingsCategory.entries)
    }

    @Test
    fun `keywords are lowercase trimmed and non blank`() {
        val bad = settings.flatMap { spec ->
            spec.keywordsStatic
                .filter { it.isBlank() || it != it.lowercase() || it != it.trim() }
                .map { "${spec.id}: '$it'" }
        }

        assertThat(bad).isEmpty()
    }

    @Test
    fun `registry covers the settings surface broadly`() {
        // A floor, not a target: a thin registry is what makes search feel broken.
        assertThat(settings.size).isAtLeast(60)
    }

    @Test
    fun `default highlight shape matches the settings row corner radius`() {
        assertThat(DefaultSettingHighlightShape).isEqualTo(RoundedCornerShape(10.dp))
    }
}
