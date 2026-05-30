package com.lostf1sh.pixelplayeross.baselineprofile

import android.util.Log
import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateStartupProfile() {
        val packageName = benchmarkTargetPackageName()

        rule.collect(
            packageName = packageName,
            includeInStartupProfile = true,
            maxIterations = 5,
            stableIterations = 3
        ) {
            try {
                setupBenchmarkPermissions(packageName)
                runStep("Startup", requireAppBefore = false) { startBenchmarkActivity(packageName) }

                Log.d(TAG, "--- STARTUP FLOW FINISHED ---")
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error: ${e}")
                e.printStackTrace()
                throw e
            } finally {
                pressHome()
            }
        }
    }

    @Test
    fun generateBaselineProfile() {
        val packageName = benchmarkTargetPackageName()

        rule.collect(
            packageName = packageName,
            includeInStartupProfile = false,
            maxIterations = 5,
            stableIterations = 3
        ) {
            try {
                setupBenchmarkPermissions(packageName)
                runStep("Startup", requireAppBefore = false) { startBenchmarkActivity(packageName) }
                runStep("Library Refresh") { primeLibraryForPlayback() }
                runStep("Home, Daily Mix, Stats") { runHomeFlow() }
                runStep("Settings Surfaces") { runSettingsSurfacesFlow() }
                runStep("Library, Tabs, Playback") { runLibraryPlaybackFlow() }
                runStep("Search, Results, Genres") { runSearchFlow() }
                runStep("Player Sheet, Queue, Controls") { runPlayerSheetFlow() }

                Log.d(TAG, "--- FLOW FINISHED ---")
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error: ${e}")
                e.printStackTrace()
                throw e
            } finally {
                pressHome()
            }
        }
    }

    private fun MacrobenchmarkScope.runStep(
        name: String,
        requireAppBefore: Boolean = true,
        block: MacrobenchmarkScope.() -> Unit
    ) {
        try {
            Log.d(TAG, ">> STEP: $name")
            if (requireAppBefore) {
                assertAppForeground("Before Baseline Profile step '$name'")
            }
            block()
            assertAppForeground("After Baseline Profile step '$name'")
            Log.d(TAG, ">> OK")
        } catch (e: Exception) {
            Log.e(TAG, ">> FAILED $name: ${e}")
            e.printStackTrace()
            throw IllegalStateException("Baseline Profile step failed: $name", e)
        }
    }

    private fun MacrobenchmarkScope.startBenchmarkActivity(packageName: String) {
        pressHome()
        killProcess()
        startActivityAndWait { intent ->
            intent.putExtra(BENCHMARK_EXTRA, true)
        }
        waitForTargetPackageVisible(packageName, APP_START_TIMEOUT_MS)
        waitForAppForeground("After starting benchmark activity", packageName, APP_START_TIMEOUT_MS)
        waitForUi()
        handlePermissionDialogs()
        handleOnboarding()
    }

    private fun MacrobenchmarkScope.primeLibraryForPlayback() {
        if (openSettingsFromHome()) {
            if (clickOptional(pattern("Music Management|Administraci[oó]n de m[uú]sica|Library|Biblioteca"), timeoutMs = SHORT_WAIT_MS)) {
                waitForUi(EXTRA_UI_WAIT_MS)
            }
            if (!clickOptional(pattern("Full Rescan|Rescan|Re-scan|Reescan|Reescaneo|Escanear"), timeoutMs = SHORT_WAIT_MS)) {
                scrollToListBottom(repetitions = 1)
                clickOptional(pattern("Full Rescan|Rescan|Re-scan|Reescan|Reescaneo|Escanear"), timeoutMs = SHORT_WAIT_MS)
            }
            waitForLibraryRefresh()
            pressBackAndWait()
            tap(
                (device.displayWidth * 0.82).toInt(),
                (device.displayHeight * 0.94).toInt(),
                "open Library tab after refresh"
            )
        }

        clickTab("Library|Biblioteca")
        waitForLibraryContent()
    }

    private fun MacrobenchmarkScope.runHomeFlow() {
        clickTab("Home|Inicio")
        waitForUi()
        scrollToListBottom(repetitions = 3)
        openOptionalSurface("Daily Mix|Mix Diario") {
            scrollDownAndUp()
        }
        openOptionalSurface("Recently Played|Reci[eé]n reproducido|Reproducidos recientemente") {
            scrollDownAndUp()
        }
        openOptionalSurface("Stats|Estad[ií]sticas") {
            scrollDownAndUp()
        }
        scrollToTop(repetitions = 3)
    }

    private fun MacrobenchmarkScope.runSettingsSurfacesFlow() {
        val settingsDestinations = listOf(
            "Music Management|Administraci[oó]n de m[uú]sica|Biblioteca|Library",
            "Appearance|Apariencia",
            "Playback|Reproducci[oó]n",
            "Behavior|Comportamiento",
            "AI Integration|Integraci[oó]n",
            "Backup|Restore|Copia|Restaurar",
            "Developer Options|Opciones de desarrollador",
            "Device Capabilities|Capacidades",
            "Equalizer|Ecualizador",
            "Accounts|Cuentas",
            "About|Acerca"
        )

        if (!openSettingsFromHome()) return
        settingsDestinations.forEach { label ->
            if (!ensureSettingsScreen()) return@forEach
            if (!clickOptional(pattern(label), timeoutMs = SHORT_WAIT_MS)) {
                scrollToListBottom(repetitions = 1)
                clickOptional(pattern(label), timeoutMs = SHORT_WAIT_MS)
            }
            waitForUi()
            if (!isSettingsScreen()) {
                scrollDownAndUp()
                pressBackAndWait()
            }
        }
        pressBackAndWait()
    }

    private fun MacrobenchmarkScope.runLibraryPlaybackFlow() {
        clickTab("Library|Biblioteca")
        waitForUi(EXTRA_UI_WAIT_MS)

        if (clickOptional(pattern("Sort options|Ordenar|Opciones de orden"), timeoutMs = SHORT_WAIT_MS)) {
            waitForUi()
            scrollBottomSheetContent()
            pressBackAndWait()
        }

        if (clickOptional(pattern("More options|M[aá]s opciones"), timeoutMs = SHORT_WAIT_MS)) {
            waitForUi()
            scrollBottomSheetContent()
            pressBackAndWait()
        }

        clickFirstContentRow()
        waitForUi(EXTRA_UI_WAIT_MS)

        runLibraryPagerSwipeFlow()

        listOf(
            "Albums|[AÁ]lbumes",
            "Artists|Artistas",
            "Playlists|Listas",
            "Folders|Carpetas",
            "Liked|Favorit"
        ).forEach { tabPattern ->
            selectLibraryTab(tabPattern)
            scrollDownAndUp()
            openFirstDetailCardAndReturn()
        }

        clickTab("Library|Biblioteca")
        waitForUi()
    }

    private fun MacrobenchmarkScope.runSearchFlow() {
        clickTab("Search|Buscar")
        waitForUi()

        tap(device.displayWidth / 2, (device.displayHeight * 0.12).toInt(), "focus search field")
        waitForUi(TINY_WAIT_MS)
        enterText("love")
        waitForUi(EXTRA_UI_WAIT_MS)
        pressKey(KeyEvent.KEYCODE_ENTER, "submit search")
        waitForUi()
        scrollDownAndUp()

        listOf(
            "Songs|Canciones",
            "Albums|[AÁ]lbumes",
            "Artists|Artistas",
            "Playlists|Listas"
        ).forEach { filter ->
            clickOptional(pattern(filter), timeoutMs = TINY_WAIT_MS)
            waitForUi()
        }

        clickOptional(pattern("Clear search|Borrar b[uú]squeda|Limpiar"), timeoutMs = TINY_WAIT_MS)
        waitForUi()

        openOptionalSurface("Unknown|Desconocido|Rock|Pop|Metal") {
            scrollDownAndUp()
        }
        clickTab("Home|Inicio")
    }

    private fun MacrobenchmarkScope.runPlayerSheetFlow() {
        clickTab("Home|Inicio")
        waitForUi()

        if (!hasTextOrDescription(pattern("Player|Reproductor|Mini|Car[aá]tula|Cover|Album Art|Play|Pause|Reproducir|Pausar"))) {
            clickTab("Library|Biblioteca")
            waitForLibraryContent()
            clickFirstContentRow()
            waitForUi(EXTRA_UI_WAIT_MS)
            clickTab("Home|Inicio")
            waitForUi()
        }

        val playerPattern = pattern("Player|Reproductor|Mini|Car[aá]tula|Cover|Album Art")
        val miniPlayer = findByTextOrDescription(playerPattern, SHORT_WAIT_MS)
        if (miniPlayer != null) {
            click(miniPlayer)
        } else {
            Log.w(TAG, "Mini player not found by semantics; tapping the collapsed-player area.")
            tap(device.displayWidth / 2, (device.displayHeight * 0.86).toInt(), "open mini player fallback")
        }
        waitForUi(EXTRA_UI_WAIT_MS)

        val playPausePattern = pattern("Play|Pause|Reproducir|Pausar")
        val nextPattern = pattern("Next|Siguiente")
        val prevPattern = pattern("Previous|Anterior")
        val playerOpened = hasTextOrDescription(playPausePattern) ||
            hasTextOrDescription(nextPattern) ||
            hasTextOrDescription(prevPattern)

        val carouselY = (device.displayHeight * 0.45).toInt()
        val leftX = (device.displayWidth * 0.2).toInt()
        val rightX = (device.displayWidth * 0.8).toInt()
        repeat(2) {
            swipe(rightX, carouselY, leftX, carouselY, 30, "swipe player carousel left")
            waitForUi(TINY_WAIT_MS)
            swipe(leftX, carouselY, rightX, carouselY, 30, "swipe player carousel right")
            waitForUi(TINY_WAIT_MS)
        }

        findByDescription(playPausePattern, SHORT_WAIT_MS)?.let { playButton ->
            val sliderY = (playButton.visibleBounds.top - (device.displayHeight * 0.12).toInt())
                .coerceIn((device.displayHeight * 0.18).toInt(), (device.displayHeight * 0.8).toInt())
            swipe(leftX, sliderY, rightX, sliderY, 80, "seek forward")
            waitForUi()
            swipe(rightX, sliderY, leftX, sliderY, 80, "seek backward")
            waitForUi()
        }

        findByDescription(prevPattern, TINY_WAIT_MS)?.let { click(it) }
        waitForUi()
        findByDescription(nextPattern, TINY_WAIT_MS)?.let { click(it) }
        waitForUi()
        findByDescription(playPausePattern, TINY_WAIT_MS)?.let { click(it) }
        waitForUi()
        findByDescription(playPausePattern, TINY_WAIT_MS)?.let { click(it) }
        waitForUi()

        if (clickOptional(pattern("Queue|Cola"), timeoutMs = SHORT_WAIT_MS)) {
            waitForUi(EXTRA_UI_WAIT_MS)
        } else {
            val startX = device.displayWidth / 2
            val startY = (device.displayHeight * 0.92).toInt()
            val endY = (device.displayHeight * 0.5).toInt()
            swipe(startX, startY, startX, endY, 10, "open queue by swipe")
            waitForUi(EXTRA_UI_WAIT_MS)
        }

        if (hasTextOrDescription(pattern("Next Up|A continuaci[oó]n|Queue"))) {
            repeat(2) {
                scrollBottomSheetContent()
                waitForUi(TINY_WAIT_MS)
            }
            pressBackAndWait()
        }

        if (playerOpened) {
            pressBackAndWait()
        }
    }

    private fun MacrobenchmarkScope.handlePermissionDialogs() {
        val allowPattern = pattern("Allow", "Permitir", "Aceptar", "While using", "Mientras")
        var attemptsRemaining = 5
        while (attemptsRemaining > 0) {
            attemptsRemaining--
            val button = findPermissionAllowButton(allowPattern) ?: break
            clickSystemDialog(button)
            waitForUi(TINY_WAIT_MS, requireApp = false)
        }
        waitForAppForeground("After permission dialogs")
    }

    private fun MacrobenchmarkScope.handleOnboarding() {
        val continuePattern = pattern("Next", "Continue", "Skip", "Done", "Siguiente", "Omitir", "Empezar", "Finish")
        repeat(4) {
            val button = findByTextOrDescription(continuePattern, TINY_WAIT_MS) ?: return
            click(button)
            waitForUi()
        }
    }

    private fun MacrobenchmarkScope.clickTab(tabNamePattern: String) {
        val tab = findByTextOrDescription(pattern(tabNamePattern), SHORT_WAIT_MS)
        if (tab != null) {
            click(tab)
            waitForUi()
            return
        }

        when {
            tabNamePattern.contains("Home", ignoreCase = true) ->
                tap((device.displayWidth * 0.18).toInt(), (device.displayHeight * 0.94).toInt(), "open Home tab fallback")
            tabNamePattern.contains("Search", ignoreCase = true) ->
                tap((device.displayWidth * 0.5).toInt(), (device.displayHeight * 0.94).toInt(), "open Search tab fallback")
            tabNamePattern.contains("Library", ignoreCase = true) ->
                tap((device.displayWidth * 0.82).toInt(), (device.displayHeight * 0.94).toInt(), "open Library tab fallback")
        }
        waitForUi()
    }

    private fun MacrobenchmarkScope.openOptionalSurface(labelPattern: String, body: MacrobenchmarkScope.() -> Unit) {
        if (!clickOptional(pattern(labelPattern), timeoutMs = SHORT_WAIT_MS)) {
            Log.w(TAG, "Optional surface not found: $labelPattern")
            return
        }
        waitForUi(EXTRA_UI_WAIT_MS)
        val openedBackStackSurface = hasTextOrDescription(pattern(BACK_NAV_ALTERNATIVES))
        body()
        if (openedBackStackSurface || hasTextOrDescription(pattern(BACK_NAV_ALTERNATIVES))) {
            pressBackAndWait()
        } else {
            Log.d(TAG, "Optional surface '$labelPattern' did not navigate; staying on current app surface.")
        }
    }

    private fun MacrobenchmarkScope.openSettingsFromHome(): Boolean {
        clickTab("Home|Inicio")
        val opened = clickOptional(pattern("Settings|Configuraci[oó]n|Ajustes"), timeoutMs = SHORT_WAIT_MS)
        if (opened) waitForUi()
        return opened
    }

    private fun MacrobenchmarkScope.ensureSettingsScreen(): Boolean {
        if (isSettingsScreen()) return true
        return openSettingsFromHome() && isSettingsScreen()
    }

    private fun MacrobenchmarkScope.isSettingsScreen(): Boolean =
        hasTextOrDescription(pattern("Settings|Configuraci[oó]n|Ajustes"))

    private fun MacrobenchmarkScope.selectLibraryTab(tabNamePattern: String): Boolean {
        if (clickOptional(pattern(tabNamePattern), timeoutMs = SHORT_WAIT_MS)) {
            waitForUi()
            return true
        }
        if (clickOptional(pattern("Expand menu|Expandir men[uú]"), timeoutMs = SHORT_WAIT_MS)) {
            waitForUi()
            val selected = clickOptional(pattern(tabNamePattern), timeoutMs = SHORT_WAIT_MS)
            waitForUi()
            return selected
        }
        return false
    }

    private fun MacrobenchmarkScope.openFirstDetailCardAndReturn() {
        val beforeOpened = device.hasObject(By.pkg(benchmarkTargetPackageName()))
        tap(device.displayWidth / 2, (device.displayHeight * 0.38).toInt(), "open first detail card")
        waitForUi(EXTRA_UI_WAIT_MS)
        if (beforeOpened && !hasTextOrDescription(pattern("Library|Biblioteca"))) {
            pressBackAndWait()
        }
    }

    private fun MacrobenchmarkScope.clickFirstContentRow() {
        tap(device.displayWidth / 2, (device.displayHeight * 0.36).toInt(), "open first content row")
    }

    private fun MacrobenchmarkScope.scrollDownAndUp() {
        val midX = device.displayWidth / 2
        val bottomY = (device.displayHeight * 0.75).toInt()
        val topY = (device.displayHeight * 0.25).toInt()
        swipe(midX, bottomY, midX, topY, 45, "scroll down")
        waitForUi()
        swipe(midX, topY, midX, bottomY, 45, "scroll up")
        waitForUi()
    }

    private fun MacrobenchmarkScope.scrollBottomSheetContent() {
        val midX = device.displayWidth / 2
        val bottomY = (device.displayHeight * 0.75).toInt()
        val topY = (device.displayHeight * 0.25).toInt()
        swipe(midX, bottomY, midX, topY, 45, "scroll bottom sheet")
        waitForUi()
    }

    private fun MacrobenchmarkScope.scrollToListBottom(repetitions: Int = 4) {
        val midX = device.displayWidth / 2
        val bottomY = (device.displayHeight * 0.75).toInt()
        val topY = (device.displayHeight * 0.25).toInt()
        repeat(repetitions) {
            swipe(midX, bottomY, midX, topY, 45, "scroll list toward bottom")
            waitForUi(TINY_WAIT_MS)
        }
    }

    private fun MacrobenchmarkScope.scrollToTop(repetitions: Int = 4) {
        val midX = device.displayWidth / 2
        val bottomY = (device.displayHeight * 0.75).toInt()
        val topY = (device.displayHeight * 0.25).toInt()
        repeat(repetitions) {
            swipe(midX, topY, midX, bottomY, 45, "scroll list toward top")
            waitForUi(TINY_WAIT_MS)
        }
    }

    private fun MacrobenchmarkScope.runLibraryPagerSwipeFlow() {
        val startX = (device.displayWidth * 0.85).toInt()
        val endX = (device.displayWidth * 0.15).toInt()
        val centerY = (device.displayHeight * 0.5).toInt()
        repeat(5) {
            swipe(startX, centerY, endX, centerY, 35, "swipe library pager")
            waitForUi()
            scrollDownAndUp()
        }
    }

    private fun MacrobenchmarkScope.waitForLibraryRefresh() {
        val syncingPattern = pattern(
            "Running full rescan|Full rescan started|Reading MediaStore|Scanning music files|" +
                "Sincronizando|Escaneando|Leyendo MediaStore"
        )
        waitForUi(LIBRARY_SYNC_MIN_WAIT_MS)
        repeat(45) {
            waitForUi(1_000L)
            if (!hasTextOrDescription(syncingPattern)) return
        }
    }

    private fun MacrobenchmarkScope.waitForLibraryContent() {
        val emptyPattern = pattern("No songs|No valid songs|Sin canciones|No se encontraron canciones|Empty")
        repeat(12) {
            waitForUi(1_000L)
            if (!hasTextOrDescription(emptyPattern)) return
            scrollDownAndUp()
        }
    }

    private fun MacrobenchmarkScope.clickOptional(pattern: Pattern, timeoutMs: Long): Boolean {
        val target = findByTextOrDescription(pattern, timeoutMs) ?: return false
        click(target)
        return true
    }

    private fun MacrobenchmarkScope.findByTextOrDescription(pattern: Pattern, timeoutMs: Long): UiObject2? {
        assertAppForeground("Before finding UI object '$pattern'")
        val target = device.wait(Until.findObject(By.desc(pattern)), timeoutMs)
            ?: device.wait(Until.findObject(By.text(pattern)), timeoutMs)
        assertAppForeground("After finding UI object '$pattern'")
        return target
    }

    private fun MacrobenchmarkScope.findByDescription(pattern: Pattern, timeoutMs: Long): UiObject2? {
        assertAppForeground("Before finding description '$pattern'")
        val target = device.wait(Until.findObject(By.desc(pattern)), timeoutMs)
        assertAppForeground("After finding description '$pattern'")
        return target
    }

    private fun MacrobenchmarkScope.hasTextOrDescription(pattern: Pattern): Boolean {
        assertAppForeground("Before checking UI object '$pattern'")
        return device.hasObject(By.desc(pattern)) || device.hasObject(By.text(pattern))
    }

    private fun MacrobenchmarkScope.click(target: UiObject2) {
        assertAppForeground("Before clicking '${target.text ?: target.contentDescription ?: target.resourceName.orEmpty()}'")
        val objectPackage = target.applicationPackage.orEmpty()
        if (objectPackage.isNotBlank() && objectPackage != benchmarkTargetPackageName()) {
            throw IllegalStateException(
                "Refusing to click object from package $objectPackage while profiling ${benchmarkTargetPackageName()}"
            )
        }
        val center = target.visibleCenter
        tap(center.x, center.y, "click app UI object")
    }

    private fun MacrobenchmarkScope.tap(x: Int, y: Int, context: String) {
        assertAppForeground("Before $context")
        device.click(x, y)
        assertAppForeground("After $context")
    }

    private fun MacrobenchmarkScope.swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        steps: Int,
        context: String
    ) {
        assertAppForeground("Before $context")
        device.swipe(startX, startY, endX, endY, steps)
        assertAppForeground("After $context")
    }

    private fun MacrobenchmarkScope.enterText(text: String) {
        assertAppForeground("Before entering text")
        device.executeShellCommand("input text ${text.replace(" ", "%s")}")
        assertAppForeground("After entering text")
    }

    private fun MacrobenchmarkScope.pressKey(keyCode: Int, context: String) {
        assertAppForeground("Before $context")
        device.pressKeyCode(keyCode)
        assertAppForeground("After $context")
    }

    private fun MacrobenchmarkScope.pressBackAndWait() {
        assertAppForeground("Before pressing back")
        device.pressBack()
        waitForUi(requireApp = false)
        if (device.hasObject(By.pkg(benchmarkTargetPackageName()))) {
            return
        }

        Log.w(TAG, "Back left the target app; relaunching benchmark activity.")
        startActivityAndWait { intent ->
            intent.putExtra(BENCHMARK_EXTRA, true)
        }
        waitForTargetPackageVisible(benchmarkTargetPackageName(), APP_START_TIMEOUT_MS)
        waitForAppForeground("After relaunching from Back fallback")
        waitForUi()
    }

    private fun MacrobenchmarkScope.waitForUi(delayMs: Long = DEFAULT_UI_WAIT_MS, requireApp: Boolean = true) {
        device.waitForIdle(IDLE_WAIT_MS)
        Thread.sleep(delayMs)
        if (requireApp) {
            assertAppForeground("After waiting for app UI")
        }
    }

    private fun MacrobenchmarkScope.findPermissionAllowButton(pattern: Pattern): UiObject2? =
        device.findObject(By.res("com.android.permissioncontroller:id/permission_allow_button"))
            ?: device.findObject(By.res("com.android.permissioncontroller:id/permission_allow_foreground_only_button"))
            ?: device.findObject(By.res("com.google.android.permissioncontroller:id/permission_allow_button"))
            ?: device.findObject(By.res("com.google.android.permissioncontroller:id/permission_allow_foreground_only_button"))
            ?: device.wait(Until.findObject(By.text(pattern)), TINY_WAIT_MS)
            ?: device.wait(Until.findObject(By.desc(pattern)), TINY_WAIT_MS)

    private fun MacrobenchmarkScope.clickSystemDialog(target: UiObject2) {
        val center = target.visibleCenter
        device.click(center.x, center.y)
    }

    private fun pattern(vararg alternatives: String): Pattern =
        pattern(alternatives.joinToString("|"))

    private fun pattern(alternatives: String): Pattern =
        Pattern.compile(".*($alternatives).*", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)

    private companion object {
        private const val TAG = "BaselineProfileGenerator"
        private const val BACK_NAV_ALTERNATIVES = "Back|Navigate up|Atr[aá]s|Volver"
        private const val APP_START_TIMEOUT_MS = 15_000L
        private const val IDLE_WAIT_MS = 3_000L
        private const val TINY_WAIT_MS = 500L
        private const val SHORT_WAIT_MS = 1_500L
        private const val DEFAULT_UI_WAIT_MS = 900L
        private const val EXTRA_UI_WAIT_MS = 1_800L
        private const val LIBRARY_SYNC_MIN_WAIT_MS = 12_000L
    }
}
