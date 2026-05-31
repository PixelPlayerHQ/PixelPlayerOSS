package com.lostf1sh.pixelplayeross.baselineprofile

import android.content.Intent
import android.view.KeyEvent
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
@LargeTest
class PlayerSheetAnimationBenchmarks {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun playerSheetOpenCloseGestures() {
        val packageName = benchmarkTargetPackageName()

        benchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.UseIfAvailable
            ),
            iterations = 6,
            startupMode = StartupMode.WARM,
            setupBlock = {
                val shouldRebuildLibrary = !libraryRebuiltForThisRun
                setupBenchmarkPermissions(packageName)
                pressHome()
                killProcess()
                startActivityAndWait { intent ->
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.putExtra(BENCHMARK_EXTRA, true)
                    intent.putExtra(BENCHMARK_REBUILD_DATABASE_EXTRA, shouldRebuildLibrary)
                }
                waitForTargetPackageVisible(packageName)
                waitForAppForeground("Player sheet benchmark setup", packageName)
                device.waitForIdle(IDLE_WAIT_MS)
                assertDeviceMediaStoreHasAudio()
                dismissBenchmarkBlockingDialogs()
                if (shouldRebuildLibrary) {
                    Thread.sleep(BENCHMARK_REBUILD_WAIT_MS)
                    dismissBenchmarkBlockingDialogs()
                }
                ensureSongIsReady()
                libraryRebuiltForThisRun = true
                openHomeTab()
                waitForSheetState(SHEET_COLLAPSED_PATTERN, "setup after opening Home")
                device.waitForIdle(IDLE_WAIT_MS)
            }
        ) {
            runPlayerSheetAnimationSequence()
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.assertDeviceMediaStoreHasAudio() {
        val firstAudioRow = executeBenchmarkShellCommand(
            "content query --uri content://media/external/audio/media --projection _id:title"
        )
        if (!firstAudioRow.contains("Row:")) {
            throw IllegalStateException(
                "Player sheet benchmark needs real audio in MediaStore before it can measure the " +
                    "real player path. MediaStore query returned: $firstAudioRow"
            )
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.dismissBenchmarkBlockingDialogs() {
        repeat(3) {
            val button = findByTextOrDescription(DISMISS_DIALOG_PATTERN, TINY_WAIT_MS) ?: return
            click(button)
            Thread.sleep(DEFAULT_WAIT_MS)
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.ensureSongIsReady() {
        if (isCollapsedSheetVisible()) return
        if (isExpandedSheetVisible()) {
            collapseExpandedPlayer()
            waitForSheetState(SHEET_COLLAPSED_PATTERN, "collapsing existing player during setup")
            return
        }

        openLibraryTab()
        waitForLibraryContent()
        tap(device.displayWidth / 2, (device.displayHeight * 0.30f).toInt())
        waitForAnySheetState("after selecting the first library song")

        if (isExpandedSheetVisible()) {
            collapseExpandedPlayer()
            waitForSheetState(SHEET_COLLAPSED_PATTERN, "collapsing newly selected song")
        }

        if (!isCollapsedSheetVisible()) {
            throw IllegalStateException(
                "A library row was tapped, but the real UnifiedPlayerSheetV2 mini-player did not appear. " +
                    "Visible UI: ${visibleUiSnapshot()}"
            )
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.runPlayerSheetAnimationSequence() {
        val midX = device.displayWidth / 2
        val collapsedY = (device.displayHeight * 0.86f).toInt()
        val fullTopY = (device.displayHeight * 0.12f).toInt()
        val fullMidY = (device.displayHeight * 0.48f).toInt()
        val fullBottomY = (device.displayHeight * 0.88f).toInt()

        openCollapsedPlayerByTap(collapsedY)
        waitForSheetState(SHEET_EXPANDED_PATTERN, "tap open")
        collapseExpandedPlayer()
        waitForSheetState(SHEET_COLLAPSED_PATTERN, "tap close")

        swipeOpenFromCollapsed(midX, collapsedY, fullTopY, steps = 12)
        waitForSheetState(SHEET_EXPANDED_PATTERN, "fast swipe open")
        swipe(midX, fullTopY, midX, fullBottomY, steps = 8)
        waitForSheetState(SHEET_COLLAPSED_PATTERN, "fast swipe close")

        swipeOpenFromCollapsed(midX, collapsedY, fullTopY, steps = 55)
        waitForSheetState(SHEET_EXPANDED_PATTERN, "slow swipe open")
        swipe(midX, fullTopY, midX, fullBottomY, steps = 55)
        waitForSheetState(SHEET_COLLAPSED_PATTERN, "slow swipe close")

        swipeOpenFromCollapsed(midX, collapsedY, fullMidY, steps = 45)
        waitForAnySheetState("partial drag and release")
        collapseIfExpandedBySwipe(midX, fullTopY, fullBottomY)

        swipeCancelledFromCollapsed(midX, collapsedY)
        waitForSheetState(SHEET_COLLAPSED_PATTERN, "cancelled/undershoot drag")

        repeat(3) {
            openCollapsedPlayerByTap(collapsedY)
            waitForSheetState(SHEET_EXPANDED_PATTERN, "repeated tap open $it")
            collapseExpandedPlayer()
            waitForSheetState(SHEET_COLLAPSED_PATTERN, "repeated tap close $it")
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openCollapsedPlayerByTap(fallbackY: Int) {
        findByTextOrDescription(SHEET_COLLAPSED_PATTERN, TINY_WAIT_MS)?.let {
            click(it)
            return
        }
        tap(device.displayWidth / 2, fallbackY)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.swipeOpenFromCollapsed(
        fallbackX: Int,
        fallbackY: Int,
        targetY: Int,
        steps: Int
    ) {
        val center = findByTextOrDescription(SHEET_COLLAPSED_PATTERN, TINY_WAIT_MS)
            ?.let { runCatching { it.visibleCenter }.getOrNull() }
        val startX = center?.x ?: fallbackX
        val startY = center?.y ?: fallbackY
        swipe(startX, startY, startX, targetY, steps)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.swipeCancelledFromCollapsed(
        fallbackX: Int,
        fallbackY: Int
    ) {
        val center = findByTextOrDescription(SHEET_COLLAPSED_PATTERN, TINY_WAIT_MS)
            ?.let { runCatching { it.visibleCenter }.getOrNull() }
        val startX = center?.x ?: fallbackX
        val startY = center?.y ?: fallbackY
        assertAppForeground("Before benchmark cancelled drag")
        executeBenchmarkShellCommand("input motionevent DOWN $startX $startY")
        Thread.sleep(16L)
        executeBenchmarkShellCommand("input motionevent MOVE $startX ${startY - 2}")
        Thread.sleep(16L)
        executeBenchmarkShellCommand("input motionevent CANCEL $startX ${startY - 2}")
        Thread.sleep(ANIMATION_WAIT_MS)
        assertAppForeground("After benchmark cancelled drag")
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.collapseIfExpandedBySwipe(
        midX: Int,
        fullTopY: Int,
        fullBottomY: Int
    ) {
        if (!isExpandedSheetVisible()) return
        swipe(midX, fullTopY, midX, fullBottomY, steps = 30)
        waitForSheetState(SHEET_COLLAPSED_PATTERN, "closing after partial drag")
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.collapseExpandedPlayer() {
        findByTextOrDescription(COLLAPSE_PLAYER_PATTERN, SHORT_WAIT_MS)?.let {
            click(it)
            return
        }
        pressBack()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openHomeTab() {
        findByTextOrDescription(HOME_TAB_PATTERN, SHORT_WAIT_MS)?.let {
            click(it)
            Thread.sleep(DEFAULT_WAIT_MS)
            return
        }
        repeat(4) {
            if (!hasTextOrDescription(BACK_PATTERN)) return@repeat
            device.pressKeyCode(KeyEvent.KEYCODE_BACK)
            Thread.sleep(DEFAULT_WAIT_MS)
            findByTextOrDescription(HOME_TAB_PATTERN, TINY_WAIT_MS)?.let { home ->
                click(home)
                Thread.sleep(DEFAULT_WAIT_MS)
                return
            }
        }
        tap((device.displayWidth * 0.18f).toInt(), (device.displayHeight * 0.94f).toInt())
        Thread.sleep(DEFAULT_WAIT_MS)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openLibraryTab() {
        findByTextOrDescription(LIBRARY_TAB_PATTERN, SHORT_WAIT_MS)?.let {
            click(it)
            Thread.sleep(DEFAULT_WAIT_MS)
            return
        }
        tap((device.displayWidth * 0.82f).toInt(), (device.displayHeight * 0.94f).toInt())
        Thread.sleep(DEFAULT_WAIT_MS)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.waitForLibraryContent() {
        repeat(75) {
            Thread.sleep(DEFAULT_WAIT_MS)
            if (!hasTextOrDescription(EMPTY_LIBRARY_PATTERN)) return
            swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.72f).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.28f).toInt(),
                steps = 35
            )
        }
        throw IllegalStateException("Library stayed empty after rebuild. Visible UI: ${visibleUiSnapshot()}")
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.waitForSheetState(
        pattern: Pattern,
        context: String
    ) {
        val deadline = System.currentTimeMillis() + SHEET_STATE_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            val visible = when (pattern) {
                SHEET_EXPANDED_PATTERN -> isExpandedSheetVisible()
                SHEET_COLLAPSED_PATTERN -> isCollapsedSheetVisible()
                else -> hasTextOrDescription(pattern)
            }
            if (visible) return
            Thread.sleep(100L)
        }
        throw IllegalStateException(
            "Timed out waiting for UnifiedPlayerSheetV2 state during $context. " +
                "Visible UI: ${visibleUiSnapshot()}"
        )
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.waitForAnySheetState(context: String) {
        val deadline = System.currentTimeMillis() + SHEET_STATE_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (
                isCollapsedSheetVisible() ||
                isExpandedSheetVisible()
            ) {
                return
            }
            Thread.sleep(100L)
        }
        throw IllegalStateException(
            "Timed out waiting for UnifiedPlayerSheetV2 to appear during $context. " +
                "Visible UI: ${visibleUiSnapshot()}"
        )
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.findByTextOrDescription(
        pattern: Pattern,
        timeoutMs: Long
    ): UiObject2? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val target = device.findObjects(By.desc(pattern))
                .firstOrNull { belongsToTargetPackage(it) }
                ?: device.findObjects(By.text(pattern))
                    .firstOrNull { belongsToTargetPackage(it) }
            if (target != null) return target
            Thread.sleep(100L)
        }
        return null
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.hasTextOrDescription(pattern: Pattern): Boolean {
        return device.findObjects(By.desc(pattern)).any { belongsToTargetPackage(it) } ||
            device.findObjects(By.text(pattern)).any { belongsToTargetPackage(it) }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.isExpandedSheetVisible(): Boolean =
        hasTextOrDescription(SHEET_EXPANDED_PATTERN)

    private fun androidx.benchmark.macro.MacrobenchmarkScope.isCollapsedSheetVisible(): Boolean =
        hasTextOrDescription(SHEET_COLLAPSED_PATTERN) ||
            (!isExpandedSheetVisible() && hasTextOrDescription(COLLAPSED_PLAYER_ANCHOR_PATTERN))

    private fun androidx.benchmark.macro.MacrobenchmarkScope.visibleUiSnapshot(): String {
        val descriptions = device.findObjects(By.pkg(benchmarkTargetPackageName()))
            .asSequence()
            .mapNotNull { node ->
                runCatching { node.text ?: node.contentDescription }.getOrNull()
            }
            .filter { it.isNotBlank() }
            .take(24)
            .joinToString(separator = " | ")
        return descriptions.ifBlank { "<no text or content descriptions exposed>" }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.click(target: UiObject2) {
        val objectPackage = runCatching { target.applicationPackage.orEmpty() }.getOrDefault("")
        if (objectPackage.isNotBlank() && objectPackage != benchmarkTargetPackageName()) {
            throw IllegalStateException("Refusing to click non-target package $objectPackage")
        }
        val center = runCatching { target.visibleCenter }.getOrNull() ?: return
        tap(center.x, center.y)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.belongsToTargetPackage(node: UiObject2): Boolean =
        runCatching { node.applicationPackage == benchmarkTargetPackageName() }.getOrDefault(false)

    private fun androidx.benchmark.macro.MacrobenchmarkScope.tap(x: Int, y: Int) {
        assertAppForeground("Before benchmark tap")
        device.click(x, y)
        assertAppForeground("After benchmark tap")
        Thread.sleep(DEFAULT_WAIT_MS)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        steps: Int
    ) {
        assertAppForeground("Before benchmark swipe")
        device.swipe(startX, startY, endX, endY, steps)
        assertAppForeground("After benchmark swipe")
        Thread.sleep(ANIMATION_WAIT_MS)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.pressBack() {
        assertAppForeground("Before benchmark back")
        device.pressKeyCode(KeyEvent.KEYCODE_BACK)
        Thread.sleep(ANIMATION_WAIT_MS)
        assertAppForeground("After benchmark back")
    }

    private companion object {
        private const val IDLE_WAIT_MS = 2_000L
        private const val DEFAULT_WAIT_MS = 900L
        private const val TINY_WAIT_MS = 500L
        private const val SHORT_WAIT_MS = 1_500L
        private const val BENCHMARK_REBUILD_WAIT_MS = 20_000L
        private const val ANIMATION_WAIT_MS = 360L
        private const val SHEET_STATE_WAIT_MS = 3_000L

        private var libraryRebuiltForThisRun = false
        private const val BENCHMARK_REBUILD_DATABASE_EXTRA = "benchmark_rebuild_database"

        private val SHEET_COLLAPSED_PATTERN = pattern("PixelPlayer player sheet collapsed")
        private val SHEET_EXPANDED_PATTERN = pattern(
            "PixelPlayer player sheet expanded|Collapse player|Contraer reproductor|Now Playing|Reproduciendo"
        )
        private val COLLAPSED_PLAYER_ANCHOR_PATTERN = pattern(
            "PixelPlayer player sheet collapsed|Album art of|Car[aá]tula de|Anterior|Pausar|Reproducir|Siguiente"
        )
        private val HOME_TAB_PATTERN = pattern("Home|Inicio")
        private val BACK_PATTERN = exactPattern("Back|Atr[aá]s")
        private val LIBRARY_TAB_PATTERN = pattern("Library|Biblioteca")
        private val COLLAPSE_PLAYER_PATTERN = exactPattern("Collapse player|Contraer reproductor")
        private val DISMISS_DIALOG_PATTERN = exactPattern("Got it|Entendido|Aceptar|OK")
        private val EMPTY_LIBRARY_PATTERN = pattern(
            "No songs|No valid songs|Sin canciones|No se encontraron canciones|Empty"
        )

        private fun pattern(alternatives: String): Pattern =
            Pattern.compile(".*($alternatives).*", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)

        private fun exactPattern(alternatives: String): Pattern =
            Pattern.compile("^($alternatives)$", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
    }
}
