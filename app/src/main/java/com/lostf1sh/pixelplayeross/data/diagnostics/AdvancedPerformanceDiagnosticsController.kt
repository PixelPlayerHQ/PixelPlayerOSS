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

@Singleton
class AdvancedPerformanceDiagnosticsController @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val stallMonitor = MainThreadStallMonitor()
    private var observerJob: Job? = null
    private var expiryJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (observerJob != null) return
        observerJob = scope.launch {
            userPreferencesRepository.disableExpiredAdvancedPerformanceDiagnostics()
            userPreferencesRepository.advancedPerformanceDiagnosticsSettingsFlow.collectLatest { settings ->
                val active = AdvancedPerformanceDiagnostics.configureSession(
                    enabled = settings.enabled,
                    startedAtEpochMs = settings.sessionStartedEpochMs,
                    expiresAtEpochMs = settings.expiresAtEpochMs
                )
                expiryJob?.cancel()
                if (active && settings.expiresAtEpochMs != null) {
                    expiryJob = scope.launch {
                        val delayMs = settings.expiresAtEpochMs - System.currentTimeMillis()
                        if (delayMs > 0L) delay(delayMs)
                        userPreferencesRepository.disableExpiredAdvancedPerformanceDiagnostics()
                    }
                }
                withContext(Dispatchers.Main.immediate) {
                    if (active) {
                        stallMonitor.start()
                    } else {
                        stallMonitor.stop()
                    }
                }
            }
        }
    }
}
