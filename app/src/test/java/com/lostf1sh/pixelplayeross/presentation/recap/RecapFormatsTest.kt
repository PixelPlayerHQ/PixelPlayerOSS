package com.lostf1sh.pixelplayeross.presentation.recap

import android.content.res.Resources
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.utils.formatListeningDurationCompact
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test

class RecapFormatsTest {

    private val strings = mapOf(
        R.string.duration_hours_minutes_medium to "%1\$d h %2\$02d m",
        R.string.duration_hours_medium to "%1\$d h",
        R.string.duration_minutes_medium to "%1\$d m",
        R.string.duration_seconds_medium to "%1\$d s",
        R.string.duration_hours_minutes_short to "%1\$dh %2\$02dm",
        R.string.duration_hours_short to "%1\$dh",
        R.string.duration_minutes_short to "%1\$dm",
        R.string.duration_seconds_short to "%1\$ds",
        R.string.recap_cd_chart_entry to "%1\$s: %2\$s",
    )

    /** Every string id a format asked for. */
    private val requestedIds = mutableListOf<Int>()

    private val resources = mockk<Resources> {
        every { getString(any(), *anyVararg()) } answers {
            val id = firstArg<Int>()
            requestedIds += id
            String.format(Locale.US, strings.getValue(id), *formatArgs(args.drop(1)))
        }
    }

    /** MockK may hand a vararg over either as one array argument or already spread. */
    private fun formatArgs(rest: List<Any?>): Array<Any?> {
        val single = rest.singleOrNull()
        return if (single is Array<*>) arrayOf(*single) else rest.toTypedArray()
    }

    private fun minutes(value: Long): Long = TimeUnit.MINUTES.toMillis(value)

    @Test
    fun `the biggest month reads like the top lists rather than in its own style`() {
        // QA saw "1 h 20 m of listening" next to album and artist chips reading "2h 19m".
        assertThat(formatRecapDuration(resources, minutes(80))).isEqualTo("1h 20m")
        assertThat(formatRecapDuration(resources, minutes(139))).isEqualTo("2h 19m")
    }

    @Test
    fun `recap durations match the Stats screen's compact style for every magnitude`() {
        val samples = listOf(
            TimeUnit.SECONDS.toMillis(59),
            minutes(1),
            minutes(59) + TimeUnit.SECONDS.toMillis(59),
            minutes(60),
            minutes(61),
            minutes(80),
            TimeUnit.HOURS.toMillis(250) + minutes(5)
        )

        samples.forEach { ms ->
            assertThat(formatRecapDuration(resources, ms)).isEqualTo(formatListeningDurationCompact(resources, ms))
        }
        assertThat(requestedIds).containsNoneOf(
            R.string.duration_hours_minutes_medium,
            R.string.duration_hours_medium,
            R.string.duration_minutes_medium,
            R.string.duration_seconds_medium
        )
    }

    @Test
    fun `listened minutes round down like the formatted duration`() {
        val ms = minutes(80) + TimeUnit.SECONDS.toMillis(59)

        assertThat(recapListenedMinutes(ms)).isEqualTo(80L)
        assertThat(formatRecapDuration(resources, ms)).isEqualTo("1h 20m")
        assertThat(recapListenedMinutes(TimeUnit.SECONDS.toMillis(59))).isEqualTo(0L)
    }

    @Test
    fun `chart entries read out every amount with listening in chart order`() {
        val months = listOf("January", "February", "March", "April")
        val listened = listOf(minutes(200), 0L, minutes(65), TimeUnit.SECONDS.toMillis(40))

        assertThat(recapChartEntries(resources, months, listened)).containsExactly(
            "January: 3h 20m",
            "March: 1h 05m",
            "April: 40s"
        ).inOrder()
    }

    @Test
    fun `chart entries are empty without listening`() {
        assertThat(recapChartEntries(resources, listOf("12 AM", "1 AM"), listOf(0L, 0L))).isEmpty()
    }

    @Test
    fun `recap screens format listening time only through the recap helpers`() {
        val directFormatting = Regex(
            """\bformat(ListeningDurationLong|ListeningDurationCompact|TotalDuration|Duration)\(|\.toMinutes\("""
        )
        val offenders = recapSources()
            .filter { it.name != "RecapFormats.kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    "${file.name}:${index + 1}: ${line.trim()}".takeIf { directFormatting.containsMatchIn(line) }
                }
            }

        assertThat(offenders).isEmpty()
    }

    private fun recapSources(): List<File> {
        val workingDirectory = File(System.getProperty("user.dir") ?: ".")
        val directory = sequenceOf(
            File(workingDirectory, "src/main/java/com/lostf1sh/pixelplayeross/presentation/recap"),
            File(workingDirectory, "app/src/main/java/com/lostf1sh/pixelplayeross/presentation/recap"),
        ).firstOrNull(File::isDirectory)
            ?: error("Could not locate the recap sources from $workingDirectory")
        return directory.listFiles { file -> file.extension == "kt" }.orEmpty().sortedBy { it.name }.also {
            check(it.any { file -> file.name == "RecapSlides.kt" }) { "Recap slides missing from $directory" }
        }
    }
}
