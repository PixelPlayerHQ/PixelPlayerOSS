package com.lostf1sh.pixelplayeross.data.repository

import com.lostf1sh.pixelplayeross.data.database.TransitionDao
import com.lostf1sh.pixelplayeross.data.database.TransitionRuleEntity
import com.lostf1sh.pixelplayeross.data.model.MixTransition
import com.lostf1sh.pixelplayeross.data.model.TransitionMode
import com.lostf1sh.pixelplayeross.data.model.TransitionSettings
import com.lostf1sh.pixelplayeross.data.model.TransitionSource
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TransitionRepositoryImplTest {
    private val dao = mockk<TransitionDao>()
    private val preferences = mockk<UserPreferencesRepository>()
    private val repository = TransitionRepositoryImpl(dao, preferences)

    private val global = TransitionSettings(mode = TransitionMode.OVERLAP, durationMs = 12_000)

    private fun pairRule(mixJson: String?) = TransitionRuleEntity(
        id = 1,
        playlistId = "p",
        fromTrackId = "a",
        toTrackId = "b",
        settings = TransitionSettings(mode = TransitionMode.OVERLAP, durationMs = 30_000),
        mixJson = mixJson,
    )

    @Test
    fun `a pair row that carries a mix is not used as an ordinary crossfade setting`() = runTest {
        every { dao.getSpecificRule("p", "a", "b") } returns
            flowOf(pairRule(MixTransition.toJson(MixTransition(outPointMs = 1, windowMs = 30_000))))
        every { dao.getPlaylistDefaultRule("p") } returns flowOf(null)
        every { preferences.globalTransitionSettingsFlow } returns flowOf(global)

        val resolution = repository.resolveTransitionSettings("p", "a", "b").first()

        // Mix turned off: the playlist goes back to the global setting, not the mix's 30 s window.
        assertEquals(TransitionSource.GLOBAL_DEFAULT, resolution.source)
        assertEquals(global, resolution.settings)
    }

    @Test
    fun `a plain pair row still resolves as before`() = runTest {
        val rule = pairRule(mixJson = null)
        every { dao.getSpecificRule("p", "a", "b") } returns flowOf(rule)

        val resolution = repository.resolveTransitionSettings("p", "a", "b").first()

        assertEquals(TransitionSource.PLAYLIST_SPECIFIC, resolution.source)
        assertEquals(rule.settings, resolution.settings)
    }
}
