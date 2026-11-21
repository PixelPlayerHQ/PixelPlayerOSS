package com.lostf1sh.pixelplayeross.presentation.components

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import com.lostf1sh.pixelplayeross.ui.theme.LocalPixelPlayerDarkTheme

@Composable
internal fun MetadataEditorDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible
    if (!transitionState.currentState && !transitionState.targetState) return

    val isDarkTheme = LocalPixelPlayerDarkTheme.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.let { dialogWindow ->
                dialogWindow.setLayout(MATCH_PARENT, MATCH_PARENT)
                // Compose handles IME insets via WindowInsets.safeDrawing; ADJUST_NOTHING prevents
                // Android 15-17 WindowManager from shrinking the dialog frame and leaving a black
                // rectangle behind when third-party keyboards (e.g. HeliBoard) switch fields or hide.
                dialogWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                dialogWindow.setDimAmount(0f)
                dialogWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
                ViewCompat.requestApplyInsets(dialogWindow.decorView)
            }
            onDispose {}
        }
        SideEffect {
            window?.let { dialogWindow ->
                val controller = WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
                controller.isAppearanceLightStatusBars = !isDarkTheme
                controller.isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
        AnimatedVisibility(
            visibleState = transitionState,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(animationSpec = tween(220)),
            exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(animationSpec = tween(200)),
        ) { content() }
    }
}
