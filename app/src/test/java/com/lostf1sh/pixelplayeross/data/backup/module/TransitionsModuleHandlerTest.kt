package com.lostf1sh.pixelplayeross.data.backup.module

import com.google.gson.GsonBuilder
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.database.SongSummary
import com.lostf1sh.pixelplayeross.data.database.TransitionDao
import com.lostf1sh.pixelplayeross.data.database.TransitionRuleEntity
import com.lostf1sh.pixelplayeross.data.model.MixTransition
import com.lostf1sh.pixelplayeross.data.model.TransitionSettings
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TransitionsModuleHandlerTest {
    private val gson = GsonBuilder().serializeNulls().create()
    private val sourceDao = mockk<MusicDao>()
    private val targetDao = mockk<MusicDao>()
    private val transitions = mockk<TransitionDao>(relaxed = true)

    private fun summary(id: Long, title: String) = SongSummary(id, title, "Artist", "Album", 200_000)

    @Test
    fun `rules follow their songs to another device's ids`() = runTest {
        // Backup device: songs 1, 2, 3.
        coEvery { sourceDao.getAllLocalSongSummaries() } returns listOf(summary(1, "One"), summary(2, "Two"), summary(3, "Three"))
        coEvery { transitions.getAllRulesOnce() } returns listOf(
            TransitionRuleEntity(10, "p", "1", "2", TransitionSettings(durationMs = 4000)),
            TransitionRuleEntity(11, "p", null, null, TransitionSettings()),
        )
        val payload = TransitionsModuleHandler(transitions, BackupSongResolver(sourceDao), gson).export()

        // New device: same songs, different MediaStore ids, and id 1 now names another song.
        coEvery { targetDao.getAllLocalSongSummaries() } returns listOf(summary(1, "Other"), summary(41, "One"), summary(42, "Two"))
        val restored = slot<List<TransitionRuleEntity>>()
        coEvery { transitions.replaceAllRules(capture(restored)) } just Runs
        TransitionsModuleHandler(transitions, BackupSongResolver(targetDao), gson).restore(payload)

        val pair = restored.captured.single { it.fromTrackId != null }
        assertEquals("41" to "42", pair.fromTrackId to pair.toTrackId)
        assertEquals(4000, pair.settings.durationMs)
        assertTrue(restored.captured.any { it.fromTrackId == null && it.toTrackId == null })
    }

    @Test
    fun `mix data survives a backup round trip`() = runTest {
        val mixJson = MixTransition.toJson(MixTransition(outPointMs = 90_000, windowMs = 16_000, bars = 8))
        coEvery { sourceDao.getAllLocalSongSummaries() } returns listOf(summary(1, "One"), summary(2, "Two"))
        coEvery { transitions.getAllRulesOnce() } returns listOf(
            TransitionRuleEntity(10, "p", "1", "2", TransitionSettings(), mixJson),
        )
        val payload = TransitionsModuleHandler(transitions, BackupSongResolver(sourceDao), gson).export()

        coEvery { targetDao.getAllLocalSongSummaries() } returns listOf(summary(1, "One"), summary(2, "Two"))
        val restored = slot<List<TransitionRuleEntity>>()
        coEvery { transitions.replaceAllRules(capture(restored)) } just Runs
        TransitionsModuleHandler(transitions, BackupSongResolver(targetDao), gson).restore(payload)

        assertEquals(mixJson, restored.captured.single().mixJson)
    }

    @Test
    fun `a legacy payload without mix data restores with no mix`() = runTest {
        val legacy = """[{"id":3,"playlistId":"p","fromTrackId":"7","toTrackId":"8","settings":{"mode":"OVERLAP","durationMs":6000,"curveIn":"LINEAR","curveOut":"LINEAR"}}]"""
        val restored = slot<List<TransitionRuleEntity>>()
        coEvery { transitions.replaceAllRules(capture(restored)) } just Runs
        TransitionsModuleHandler(transitions, BackupSongResolver(targetDao), gson).restore(legacy)
        assertEquals(null, restored.captured.single().mixJson)
    }

    @Test
    fun `a legacy payload without metadata restores its ids unchanged`() = runTest {
        val legacy = """[{"id":3,"playlistId":"p","fromTrackId":"7","toTrackId":"8","settings":{"mode":"OVERLAP","durationMs":6000,"curveIn":"LINEAR","curveOut":"LINEAR"}}]"""
        val restored = slot<List<TransitionRuleEntity>>()
        coEvery { transitions.replaceAllRules(capture(restored)) } just Runs
        TransitionsModuleHandler(transitions, BackupSongResolver(targetDao), gson).restore(legacy)
        assertEquals("7" to "8", restored.captured.single().let { it.fromTrackId to it.toTrackId })
        coVerify(exactly = 0) { targetDao.getAllLocalSongSummaries() }
    }
}
