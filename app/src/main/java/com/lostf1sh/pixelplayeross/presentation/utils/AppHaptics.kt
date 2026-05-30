package com.lostf1sh.pixelplayeross.presentation.utils

import android.view.View
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.core.view.ViewCompat

@Immutable
data class AppHapticsConfig(
    val enabled: Boolean = true,
    // Reserved for future per-category/intensity controls.
    val intensityScale: Float = 1f
)

val LocalAppHapticsConfig = staticCompositionLocalOf { AppHapticsConfig() }

val NoOpHapticFeedback: HapticFeedback = object : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

fun View.performAppHapticFeedback(
    appHapticsConfig: AppHapticsConfig,
    feedbackConstant: Int,
    flags: Int = 0
): Boolean {
    if (!appHapticsConfig.enabled) return false
    return performHapticFeedback(feedbackConstant, flags)
}

fun performAppCompatHapticFeedback(
    view: View,
    appHapticsConfig: AppHapticsConfig,
    feedbackConstant: Int,
    flags: Int = 0
): Boolean {
    if (!appHapticsConfig.enabled) return false
    return ViewCompat.performHapticFeedback(view, feedbackConstant, flags)
}
