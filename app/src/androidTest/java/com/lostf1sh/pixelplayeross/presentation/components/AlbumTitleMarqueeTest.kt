package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlbumTitleMarqueeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onlyOverflowingTitlesMoveAndTheirFullTextRemainsAccessible() {
        val shortTitle = "Fits"
        val longTitle = "An album title that is much wider than its available label"
        val active = mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Column(Modifier.width(90.dp).background(Color.White)) {
                for (title in listOf(shortTitle, longTitle)) {
                    Text(
                        text = title,
                        modifier = Modifier.albumTitleMarquee(active.value),
                        color = Color.Black,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(30_000)
        composeRule.runOnUiThread { active.value = true }
        composeRule.mainClock.advanceTimeBy(64)
        val shortLabel = composeRule.onNodeWithText(shortTitle).assertIsDisplayed()
        val longLabel = composeRule.onNodeWithText(longTitle).assertIsDisplayed()
        val shortBefore = shortLabel.captureToImage().asAndroidBitmap()
        val longBefore = longLabel.captureToImage().asAndroidBitmap()

        composeRule.mainClock.advanceTimeBy(2_500)

        assertThat(shortBefore.sameAs(shortLabel.captureToImage().asAndroidBitmap())).isTrue()
        assertThat(longBefore.sameAs(longLabel.captureToImage().asAndroidBitmap())).isFalse()
    }
}
