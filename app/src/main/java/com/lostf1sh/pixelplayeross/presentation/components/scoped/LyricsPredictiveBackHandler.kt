package com.lostf1sh.pixelplayeross.presentation.components.scoped

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun LyricsPredictiveBackHandler(
    enabled: Boolean,
    onProgressChanged: (Float) -> Unit,
    animationDurationMs: Int = 350,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val progressAnim = remember { Animatable(0f) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PredictiveBackHandler(enabled = enabled) { progressFlow ->
            try {
                progressFlow.collect { backEvent ->
                    progressAnim.snapTo(backEvent.progress)
                    onProgressChanged(backEvent.progress)
                }
                
                scope.launch {
                    progressAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(animationDurationMs / 2)
                    ) { onProgressChanged(value) }
                    onBack()
                }
            } catch (_: CancellationException) {
                scope.launch {
                    progressAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(animationDurationMs)
                    ) { onProgressChanged(value) }
                }
            }
        }
    } else {
        BackHandler(enabled = enabled, onBack = onBack)
    }
}