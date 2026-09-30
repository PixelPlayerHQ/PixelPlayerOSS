package com.lostf1sh.pixelplayeross.presentation.navigation

import androidx.activity.OnBackPressedCallback
import androidx.annotation.MainThread

/** Updates underlying callbacks synchronously, without a Flow/recomposition handoff. */
@MainThread
internal class PlayerSheetBackCoordinator {
    private var isPlayerSheetHandlingBack = false
    private val callbacks = mutableMapOf<OnBackPressedCallback, Boolean>()

    fun updatePlayerSheetHandlingBack(handling: Boolean) {
        if (isPlayerSheetHandlingBack == handling) return
        isPlayerSheetHandlingBack = handling
        callbacks.forEach { (callback, enabled) ->
            callback.isEnabled = enabled && !handling
        }
    }

    fun updateCallback(callback: OnBackPressedCallback, enabled: Boolean) {
        callbacks[callback] = enabled
        callback.isEnabled = enabled && !isPlayerSheetHandlingBack
    }

    fun removeCallback(callback: OnBackPressedCallback) {
        callbacks.remove(callback)
    }
}
