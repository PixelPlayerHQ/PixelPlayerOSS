package com.lostf1sh.pixelplayeross.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Clase de prueba para medir el rendimiento del inicio de la aplicación.
 * Compara el inicio sin optimizaciones ([CompilationMode.None]) frente al
 * inicio optimizado con Baseline Profiles ([CompilationMode.Partial]).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmarks {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCompilationNone() = startup(CompilationMode.None())

    @Test
    fun startupCompilationBaselineProfiles() = startup(
        // Usamos UseIfAvailable en lugar de Require para evitar errores de
        // asunción fallida en dispositivos Samsung/OneUI.
        CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.UseIfAvailable
        )
    )

    private fun startup(compilationMode: CompilationMode) {
        val packageName = benchmarkTargetPackageName()

        benchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            iterations = 7,
            startupMode = StartupMode.COLD,
            setupBlock = {
                setupBenchmarkPermissions(packageName)
                pressHome()
            }
        ) {
            startActivityAndWait { intent ->
                intent.putExtra(BENCHMARK_EXTRA, true)
            }
            waitForAppForeground("Startup benchmark measure block", packageName)
        }
    }
}
