package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.presentation.model.SettingsCategory
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsMainListTest {

    private val firstShape =
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    private val middleShape = RoundedCornerShape(4.dp)
    private val lastShape =
        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)

    private val warmScheme = darkColorScheme(
        primary = Color(0xFFFFB4A8),
        primaryContainer = Color(0xFF8C1D18),
        onPrimaryContainer = Color(0xFFFFDAD5),
        secondary = Color(0xFFE7BDB6),
        secondaryContainer = Color(0xFF5D3F3B),
        onSecondaryContainer = Color(0xFFFFDAD5),
        tertiary = Color(0xFFDEC48C),
        tertiaryContainer = Color(0xFF574419),
        onTertiaryContainer = Color(0xFFFBDFA6)
    )

    private val coolScheme = lightColorScheme(
        primary = Color(0xFF00639B),
        primaryContainer = Color(0xFFCEE5FF),
        onPrimaryContainer = Color(0xFF001D33),
        secondary = Color(0xFF51606F),
        secondaryContainer = Color(0xFFD5E4F7),
        onSecondaryContainer = Color(0xFF0E1D2A),
        tertiary = Color(0xFF006A60),
        tertiaryContainer = Color(0xFF9EF2E4),
        onTertiaryContainer = Color(0xFF00201C)
    )

    @Test
    fun `main list ends with updates followed by about`() {
        assertEquals(MainSettingsEntry.Category(SettingsCategory.ABOUT), mainSettingsEntries.last())
        assertEquals(MainSettingsEntry.Destination.UPDATES, mainSettingsEntries[mainSettingsEntries.lastIndex - 1])
    }

    @Test
    fun `main list shows every category and destination exactly once`() {
        val expected = SettingsCategory.entries.map { MainSettingsEntry.Category(it) } +
            MainSettingsEntry.Destination.entries

        assertEquals(expected.size, mainSettingsEntries.size)
        assertEquals(expected.toSet(), mainSettingsEntries.toSet())
    }

    @Test
    fun `lyrics row sits directly below playback`() {
        val playbackIndex = mainSettingsEntries.indexOf(MainSettingsEntry.Category(SettingsCategory.PLAYBACK))

        assertEquals(MainSettingsEntry.Destination.LYRICS, mainSettingsEntries[playbackIndex + 1])
    }

    @Test
    fun `only the last row of the main list gets rounded bottom corners`() {
        val count = mainSettingsEntries.size

        assertEquals(firstShape, settingsGroupItemShape(0, count))
        for (index in 1 until count - 1) {
            assertEquals(middleShape, settingsGroupItemShape(index, count), "row $index: ${mainSettingsEntries[index]}")
        }
        assertEquals(lastShape, settingsGroupItemShape(count - 1, count))
    }

    @Test
    fun `group shape rounds only the outer corners of the group`() {
        assertEquals(RoundedCornerShape(24.dp), settingsGroupItemShape(0, 1))
        assertEquals(firstShape, settingsGroupItemShape(0, 2))
        assertEquals(lastShape, settingsGroupItemShape(1, 2))
        assertEquals(firstShape, settingsGroupItemShape(0, 3))
        assertEquals(middleShape, settingsGroupItemShape(1, 3))
        assertEquals(lastShape, settingsGroupItemShape(2, 3))
    }

    @Test
    fun `group shapes are shared instances`() {
        assertSame(settingsGroupItemShape(1, 5), settingsGroupItemShape(3, 5))
    }

    @Test
    fun `icon colours are the matching container roles of the active scheme`() {
        assertEquals(
            warmScheme.primaryContainer to warmScheme.onPrimaryContainer,
            warmScheme.settingsIconColors(SettingsIconTone.PRIMARY)
        )
        assertEquals(
            warmScheme.secondaryContainer to warmScheme.onSecondaryContainer,
            warmScheme.settingsIconColors(SettingsIconTone.SECONDARY)
        )
        assertEquals(
            warmScheme.tertiaryContainer to warmScheme.onTertiaryContainer,
            warmScheme.settingsIconColors(SettingsIconTone.TERTIARY)
        )
    }

    @Test
    fun `every main list icon follows a change of colour scheme`() {
        mainSettingsEntries.forEach { entry ->
            val warm = warmScheme.settingsIconColors(entry.iconTone)
            val cool = coolScheme.settingsIconColors(entry.iconTone)

            assertTrue(warm in warmScheme.containerPairs(), "$entry uses a colour outside the scheme")
            assertTrue(cool in coolScheme.containerPairs(), "$entry uses a colour outside the scheme")
            assertNotEquals(warm, cool, "$entry ignores the active scheme")
        }
    }

    @Test
    fun `search accent uses the accent role of the category's icon tone`() {
        SettingsCategory.entries.forEach { category ->
            val expected = when (category.iconTone) {
                SettingsIconTone.PRIMARY -> coolScheme.primary
                SettingsIconTone.SECONDARY -> coolScheme.secondary
                SettingsIconTone.TERTIARY -> coolScheme.tertiary
            }
            assertEquals(expected, coolScheme.settingsAccentColor(category.iconTone), category.name)
        }
    }

    @Test
    fun `lyrics shares playback's tone so search groups it under the same colour`() {
        assertEquals(SettingsCategory.PLAYBACK.iconTone, MainSettingsEntry.Destination.LYRICS.iconTone)
    }

    @Test
    fun `rows open their own screens`() {
        assertEquals(
            Screen.SettingsCategory.createRoute("library"),
            MainSettingsEntry.Category(SettingsCategory.LIBRARY).route
        )
        assertEquals(Screen.Equalizer.createRoute(), MainSettingsEntry.Category(SettingsCategory.EQUALIZER).route)
        assertEquals(
            Screen.DeviceCapabilities.createRoute(),
            MainSettingsEntry.Category(SettingsCategory.DEVICE_CAPABILITIES).route
        )
        assertEquals(Screen.About.route, MainSettingsEntry.Category(SettingsCategory.ABOUT).route)
        assertEquals(Screen.LyricsSettings.createRoute(), MainSettingsEntry.Destination.LYRICS.route)
        assertEquals(Screen.CloudDownloads.route, MainSettingsEntry.Destination.DOWNLOADS.route)
        assertEquals(Screen.Accounts.createRoute(), MainSettingsEntry.Destination.ACCOUNTS.route)
        assertEquals(Screen.Updates.route, MainSettingsEntry.Destination.UPDATES.route)
    }

    private fun ColorScheme.containerPairs(): Set<Pair<Color, Color>> = setOf(
        primaryContainer to onPrimaryContainer,
        secondaryContainer to onSecondaryContainer,
        tertiaryContainer to onTertiaryContainer
    )
}
