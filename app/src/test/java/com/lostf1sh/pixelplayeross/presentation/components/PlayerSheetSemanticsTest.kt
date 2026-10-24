package com.lostf1sh.pixelplayeross.presentation.components

import android.content.res.Resources
import com.lostf1sh.pixelplayeross.LocaleStringResources
import com.lostf1sh.pixelplayeross.LocaleStringResources.assertTranslatedEverywhere
import com.lostf1sh.pixelplayeross.LocaleStringResources.localeFolders
import com.lostf1sh.pixelplayeross.LocaleStringResources.nameOf
import com.lostf1sh.pixelplayeross.LocaleStringResources.stringsIn
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerSheetState
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerSheetSemanticsTest {

    @Test
    fun `collapsed and expanded sheets each describe their own state`() {
        assertEquals(
            R.string.cd_player_sheet_collapsed,
            playerSheetContentDescriptionRes(PlayerSheetState.COLLAPSED, hasTitle = true),
        )
        assertEquals(
            R.string.cd_player_sheet_expanded,
            playerSheetContentDescriptionRes(PlayerSheetState.EXPANDED, hasTitle = true),
        )
        assertEquals(
            R.string.cd_player_sheet_collapsed_no_track,
            playerSheetContentDescriptionRes(PlayerSheetState.COLLAPSED, hasTitle = false),
        )
        assertEquals(
            R.string.cd_player_sheet_expanded_no_track,
            playerSheetContentDescriptionRes(PlayerSheetState.EXPANDED, hasTitle = false),
        )
    }

    @Test
    fun `collapsed and expanded sheets carry distinct plain test tags`() {
        assertEquals(PLAYER_SHEET_COLLAPSED_TEST_TAG, playerSheetTestTag(PlayerSheetState.COLLAPSED))
        assertEquals(PLAYER_SHEET_EXPANDED_TEST_TAG, playerSheetTestTag(PlayerSheetState.EXPANDED))
        assertNotEquals(PLAYER_SHEET_COLLAPSED_TEST_TAG, PLAYER_SHEET_EXPANDED_TEST_TAG)
        PlayerSheetState.entries.map(::playerSheetTestTag).forEach { tag ->
            assertTrue(RESOURCE_ID_SAFE.matches(tag), "Not a plain resource id: $tag")
        }
    }

    @Test
    fun `player sheet exposes its state tag as a resource id`() {
        val sheet = File(javaSourceDirectory(), "$COMPONENTS_DIR/UnifiedPlayerSheetV2.kt").readText()

        assertTrue(sheet.contains("testTagsAsResourceId = true"), "The sheet tag is not exposed as a resource id")
        assertTrue(
            sheet.contains("testTag = playerSheetTestTag(currentSheetContentState)"),
            "The sheet does not tag its current state",
        )
    }

    @Test
    fun `player sheet benchmarks wait on the app's sheet tags instead of the translated description`() {
        val benchmarks = benchmarkSource().readText()

        assertTrue(
            benchmarks.contains("SHEET_COLLAPSED_RESOURCE_ID = \"$PLAYER_SHEET_COLLAPSED_TEST_TAG\""),
            "PlayerSheetAnimationBenchmarks no longer matches $PLAYER_SHEET_COLLAPSED_TEST_TAG",
        )
        assertTrue(
            benchmarks.contains("SHEET_EXPANDED_RESOURCE_ID = \"$PLAYER_SHEET_EXPANDED_TEST_TAG\""),
            "PlayerSheetAnimationBenchmarks no longer matches $PLAYER_SHEET_EXPANDED_TEST_TAG",
        )
        val englishDescriptions = listOf(
            R.string.cd_player_sheet_collapsed_no_track,
            R.string.cd_player_sheet_expanded_no_track,
        ).map { stringsIn("values").getValue(nameOf(it)) }
        englishDescriptions.forEach { description ->
            assertTrue(
                !benchmarks.contains(description, ignoreCase = true),
                "PlayerSheetAnimationBenchmarks still waits on the translated text \"$description\"",
            )
        }
    }

    @Test
    fun `english description names the sheet state and the track`() {
        val english = resourcesFor("values")

        assertEquals(
            "PixelPlayer player sheet collapsed, Bones",
            playerSheetContentDescription(english, PlayerSheetState.COLLAPSED, "Bones"),
        )
        assertEquals(
            "PixelPlayer player sheet expanded, Bones",
            playerSheetContentDescription(english, PlayerSheetState.EXPANDED, "Bones"),
        )
    }

    @Test
    fun `a missing or blank title reads the sheet state alone`() {
        val english = resourcesFor("values")

        assertEquals(
            "PixelPlayer player sheet collapsed",
            playerSheetContentDescription(english, PlayerSheetState.COLLAPSED, null),
        )
        assertEquals(
            "PixelPlayer player sheet expanded",
            playerSheetContentDescription(english, PlayerSheetState.EXPANDED, "  "),
        )
    }

    @Test
    fun `simplified chinese description is translated and still names the track`() {
        val description = playerSheetContentDescription(
            resourcesFor("values-zh-rCN"),
            PlayerSheetState.COLLAPSED,
            "Bones",
        )

        assertEquals("PixelPlayer 播放器面板已收起，Bones", description)
    }

    @Test
    fun `player chrome strings are translated in every locale`() {
        assertTranslatedEverywhere(PLAYER_CHROME_STRINGS.map(LocaleStringResources::nameOf))
    }

    @Test
    fun `formatted player chrome strings keep their single argument in every locale`() {
        val formatted = listOf(
            R.string.cd_player_sheet_collapsed,
            R.string.cd_player_sheet_expanded,
            R.string.add_bookmark_default_name,
        ).map(LocaleStringResources::nameOf)

        localeFolders().forEach { folder ->
            val strings = stringsIn(folder)
            formatted.forEach { name ->
                val text = strings.getValue(name)
                assertEquals(1, FORMAT_ARG.findAll(text).count(), "$folder/$name: $text")
                assertTrue(text.contains("%1\$s"), "$folder/$name lost its title argument: $text")
            }
        }
    }

    @Test
    fun `simplified chinese player chrome strings are not left in english`() {
        val english = stringsIn("values")
        val chinese = stringsIn("values-zh-rCN")

        PLAYER_CHROME_STRINGS.map(LocaleStringResources::nameOf).forEach { name ->
            val text = chinese.getValue(name)
            assertNotEquals(english[name], text, "$name is still English in values-zh-rCN")
            assertTrue(
                text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN },
                "$name has no Chinese text in values-zh-rCN: $text",
            )
        }
    }

    @Test
    fun `player chrome sources pass labels through string resources`() {
        val sources = File(javaSourceDirectory(), COMPONENTS_DIR)
        val leftovers = PLAYER_CHROME_SOURCES.flatMap { path ->
            val file = File(sources, path)
            assertTrue(file.isFile, "Missing ${file.path}")
            file.readLines().withIndex()
                .filter { (_, line) -> HARDCODED_LABEL.containsMatchIn(line) }
                .map { (index, line) -> "$path:${index + 1}: ${line.trim()}" }
        }

        assertEquals(emptyList<String>(), leftovers)
    }

    private fun javaSourceDirectory(): File = sequenceOf(
        File("src/main/java"),
        File("app/src/main/java"),
    ).first { it.isDirectory }

    private fun benchmarkSource(): File = sequenceOf(
        File("../baselineprofile"),
        File("baselineprofile"),
    ).map { File(it, BENCHMARK_SOURCE) }.first { it.isFile }

    /** A [Resources] that answers from the XML of [folder], the way the device would for that locale. */
    private fun resourcesFor(folder: String): Resources {
        val strings = stringsIn(folder)
        fun template(id: Int): String = strings.getValue(nameOf(id)).replace("\\'", "'")
        return mockk {
            every { getString(any()) } answers { template(firstArg()) }
            every { getString(any(), *anyVararg()) } answers {
                String.format(Locale.ROOT, template(firstArg()), *formatArgs(args.drop(1)))
            }
        }
    }

    /** MockK may hand a vararg over either as one array argument or already spread. */
    private fun formatArgs(rest: List<Any?>): Array<Any?> {
        val single = rest.singleOrNull()
        return if (single is Array<*>) arrayOf(*single) else rest.toTypedArray()
    }

    private companion object {
        val FORMAT_ARG = Regex("""%\d+\$[a-z]""")
        val RESOURCE_ID_SAFE = Regex("[a-z_]+")

        const val COMPONENTS_DIR = "com/lostf1sh/pixelplayeross/presentation/components"
        const val BENCHMARK_SOURCE =
            "src/main/java/com/lostf1sh/pixelplayeross/baselineprofile/PlayerSheetAnimationBenchmarks.kt"

        /** A label or TalkBack text given as an English literal: `contentDesc = "Shuffle"`, `isX -> "Loading…"`. */
        val HARDCODED_LABEL = Regex(
            """\b(text|contentDescription|contentDesc|semanticsLabel)\s*=\s*(if\s*\(.*\)\s*)?"[A-Za-z]|->\s*"[A-Za-z]"""
        )

        /** The player sheet, mini player and full player files whose labels went through review. */
        val PLAYER_CHROME_SOURCES = listOf(
            "UnifiedPlayerSheetV2.kt",
            "UnifiedPlayerSheetShared.kt",
            "PlayerInternalNavigationBar.kt",
            "PlayerNavigationRail.kt",
            "player/BottomToggleRow.kt",
            "player/FullPlayerContent.kt",
            "player/AddBookmarkDialog.kt",
            "subcomps/PlayerSeekBar.kt",
            "external/ExternalPlayerOverlay.kt",
        )

        val PLAYER_CHROME_STRINGS = listOf(
            R.string.cd_player_sheet_collapsed,
            R.string.cd_player_sheet_expanded,
            R.string.cd_player_sheet_collapsed_no_track,
            R.string.cd_player_sheet_expanded_no_track,
            R.string.mini_player_connecting_device,
            R.string.mini_player_preparing_playback,
            R.string.mini_player_loading_audio,
            R.string.cd_shuffle,
            R.string.cd_playback_position,
            R.string.add_bookmark_title,
            R.string.add_bookmark_saved_point,
            R.string.add_bookmark_use_lyric_line,
            R.string.add_bookmark_default_name,
        )
    }
}
