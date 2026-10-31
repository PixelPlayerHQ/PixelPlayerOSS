package com.lostf1sh.pixelplayeross.data.service

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.jupiter.api.Test

/**
 * The temporary foreground decisions live inside Android service/receiver callbacks
 * that cannot run on the JVM, so these checks pin the wiring at the source level.
 */
class MusicServiceTemporaryForegroundSourceTest {

    @Test
    fun `temporary foreground on create is limited to marked foreground starts`() {
        val source = sourceFile("data/service/MusicService.kt").readText()
        val onCreateBody = kotlinFunction(source, "override fun onCreate()")

        assertThat(onCreateBody)
            .contains("temporaryForegroundStartedInOnCreate = consumePendingTemporaryForegroundStart()")
        assertThat(onCreateBody)
            .doesNotContain("|| Build.VERSION.SDK_INT >= Build.VERSION_CODES.O")
    }

    @Test
    fun `widget actions do not force temporary foreground unless foreground start was requested`() {
        val source = sourceFile("data/service/MusicService.kt").readText()
        val onStartCommandBody = kotlinFunction(source, "override fun onStartCommand(")
        val startPredicate = onStartCommandBody.substring(
            onStartCommandBody.indexOf("val needsTemporaryForeground ="),
            onStartCommandBody.indexOf("if (needsTemporaryForeground"),
        )
        val policyBody = kotlinFunction(source, "internal fun requiresTemporaryForegroundForStart(")

        assertThat(startPredicate).contains("requiresTemporaryForegroundForStart(")
        assertThat(startPredicate)
            .contains("pendingTemporaryForegroundStart = pendingTemporaryForegroundStart")
        assertThat(policyBody).contains("forcedForegroundStart ||")
        assertThat(policyBody).contains("pendingTemporaryForegroundStart ||")
        for (predicate in listOf(startPredicate, policyBody)) {
            assertThat(predicate).doesNotContain("PlayerActions.PLAY_PAUSE")
            assertThat(predicate).doesNotContain("PlayerActions.NEXT")
            assertThat(predicate).doesNotContain("PlayerActions.PREVIOUS")
        }
    }

    @Test
    fun `start command still clears the temporary placeholder notification`() {
        val source = sourceFile("data/service/MusicService.kt").readText()
        val onStartCommandBody = kotlinFunction(source, "override fun onStartCommand(")

        assertThat(onStartCommandBody).contains("clearTemporaryForegroundNotification()")
    }

    @Test
    fun `media3 start command foreground refusal stops the service instead of crashing`() {
        val source = sourceFile("data/service/MusicService.kt").readText()
        val onStartCommandBody = kotlinFunction(source, "override fun onStartCommand(")
        val superCall = onStartCommandBody.substring(
            onStartCommandBody.indexOf("val startCommandResult = try {"),
            onStartCommandBody.indexOf("return START_NOT_STICKY"),
        )

        assertThat(superCall).contains("super.onStartCommand(intent, flags, startId)")
        assertThat(superCall).contains("e !is ForegroundServiceStartNotAllowedException")
        assertThat(superCall).contains("stopSelf()")
    }

    @Test
    fun `temporary foreground notification does not show processing playback action copy`() {
        val source = sourceFile("data/service/MusicService.kt").readText()
        val notificationBody = kotlinFunction(source, "private fun startTemporaryForegroundForCommand()")

        assertThat(notificationBody).doesNotContain("setContentText")
        assertThat(notificationBody).doesNotContain("service_processing_action")

        val processingCopyResources = resourceDirectory()
            .walkTopDown()
            .filter { it.isFile && it.extension == "xml" }
            .filter { it.readText().contains("service_processing_action") }
            .toList()

        assertThat(processingCopyResources).isEmpty()
    }

    @Test
    fun `widget foreground fallback marks the start and unmarks it when the start fails`() {
        val source = sourceFile("ui/glancewidget/PlayerControlActionCallback.kt").readText()
        val markIndex = source.indexOf("MusicService.markPendingTemporaryForegroundStart()")
        val startIndex = source.indexOf("context.startForegroundService(")
        val unmarkIndex = source.indexOf("MusicService.unmarkPendingTemporaryForegroundStart()")

        assertThat(markIndex).isAtLeast(0)
        assertThat(startIndex).isGreaterThan(markIndex)
        assertThat(unmarkIndex).isGreaterThan(startIndex)
        assertThat(source.substring(startIndex, unmarkIndex)).contains("catch (")
    }

    private fun sourceFile(relativePath: String): File {
        val workingDirectory = File(System.getProperty("user.dir") ?: ".")
        return sequenceOf(
            File(workingDirectory, "src/main/java/com/lostf1sh/pixelplayeross/$relativePath"),
            File(workingDirectory, "app/src/main/java/com/lostf1sh/pixelplayeross/$relativePath"),
        ).firstOrNull(File::isFile)
            ?: error("Could not locate source $relativePath from $workingDirectory")
    }

    private fun resourceDirectory(): File {
        val workingDirectory = File(System.getProperty("user.dir") ?: ".")
        return sequenceOf(
            File(workingDirectory, "src/main/res"),
            File(workingDirectory, "app/src/main/res"),
        ).firstOrNull(File::isDirectory)
            ?: error("Could not locate Android resources from $workingDirectory")
    }

    private fun kotlinFunction(source: String, signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) { "Could not locate function $signature" }
        val bodyStart = source.indexOf('{', start)
        require(bodyStart >= 0) { "Could not locate body for $signature" }

        var depth = 0
        for (index in bodyStart until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(start, index + 1)
                }
            }
        }
        error("Unbalanced function body for $signature")
    }
}
