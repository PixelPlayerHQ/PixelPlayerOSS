package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.update.AppUpdateManager
import com.lostf1sh.pixelplayeross.data.update.UpdateChannel
import com.lostf1sh.pixelplayeross.data.update.UpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class UpdatesUiState(
    val installedVersion: String,
    val installedChannel: UpdateChannel,
    val selectedChannel: UpdateChannel,
    val autoCheckEnabled: Boolean,
    val status: UpdateStatus,
)

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val updateManager: AppUpdateManager,
) : ViewModel() {

    private val initialState = UpdatesUiState(
        installedVersion = updateManager.installedVersionName,
        installedChannel = updateManager.installedChannel,
        selectedChannel = updateManager.installedChannel,
        autoCheckEnabled = false,
        status = updateManager.status.value,
    )

    val uiState: StateFlow<UpdatesUiState> = combine(
        updateManager.selectedChannel,
        updateManager.autoCheckEnabled,
        updateManager.status,
    ) { channel, autoCheck, status ->
        initialState.copy(selectedChannel = channel, autoCheckEnabled = autoCheck, status = status)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState)

    init {
        // Opening the screen is the user asking for an answer; don't make them tap again.
        if (updateManager.status.value == UpdateStatus.Idle) updateManager.checkNow()
    }

    fun checkNow() = updateManager.checkNow()

    fun setChannel(channel: UpdateChannel) = updateManager.setChannel(channel)

    fun setAutoCheck(enabled: Boolean) = updateManager.setAutoCheck(enabled)

    fun downloadAndInstall() = updateManager.downloadAndInstall()

    fun cancel() = updateManager.cancel()

    fun dismissError() = updateManager.dismiss()

    /** Called when the screen resumes, e.g. after the user granted "install unknown apps". */
    fun onResume() {
        if (updateManager.status.value is UpdateStatus.AwaitingInstallPermission &&
            updateManager.canRequestPackageInstalls()
        ) {
            updateManager.resumeInstall()
        }
    }
}
