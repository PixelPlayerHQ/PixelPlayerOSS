package com.lostf1sh.pixelplayeross.data.diagnostics

import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

@Singleton
class AdvancedPerformanceDiagnosticsController @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val stallMonitor = MainThreadStallMonitor()
    private var observerJob: Job? = null
    private var expiryJob: Job? = null
    private var controllerScope: CoroutineScope? = null
    private val monitorUpdateGeneration = AtomicLong(0L)
    @Volatile private var sessionActive = false
    @Volatile private var appForeground = false

    fun start(scope: CoroutineScope) {
        if (observerJob != null) return
        controllerScope = scope
        observerJob = scope.launch {
            userPreferencesRepository.disableExpiredAdvancedPerformanceDiagnostics()
            userPreferencesRepository.advancedPerformanceDiagnosticsSettingsFlow.collectLatest { settings ->
                val active = AdvancedPerformanceDiagnostics.configureSession(
                    enabled = settings.enabled,
                    startedAtEpochMs = settings.sessionStartedEpochMs,
                    expiresAtEpochMs = settings.expiresAtEpochMs
                )
                sessionActive = active
                expiryJob?.cancel()
                if (active && settings.expiresAtEpochMs != null) {
                    expiryJob = scope.launch {
                        val delayMs = settings.expiresAtEpochMs - System.currentTimeMillis()
                        if (delayMs > 0L) delay(delayMs)
                        userPreferencesRepository.disableExpiredAdvancedPerformanceDiagnostics()
                    }
                }
                updateStallMonitor()
            }
        }
    }

    fun onAppForeground() {
        appForeground = true
        updateStallMonitor()
    }

    fun onAppBackground() {
        appForeground = false
        updateStallMonitor()
    }

    private fun updateStallMonitor() {
        val scope = controllerScope ?: return
        val generation = monitorUpdateGeneration.incrementAndGet()
        scope.launch {
            withContext(Dispatchers.Main.immediate) {
                if (generation != monitorUpdateGeneration.get()) return@withContext
                val shouldRun = shouldRunMainThreadStallMonitor(sessionActive, appForeground)
                if (shouldRun) stallMonitor.start() else stallMonitor.stop()
            }
        }
    }
}

internal fun shouldRunMainThreadStallMonitor(
    sessionActive: Boolean,
    appForeground: Boolean,
): Boolean = sessionActive && appForeground
