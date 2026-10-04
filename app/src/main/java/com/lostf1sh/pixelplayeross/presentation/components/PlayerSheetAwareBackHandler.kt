package com.lostf1sh.pixelplayeross.presentation.components

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lostf1sh.pixelplayeross.presentation.navigation.PlayerSheetBackCoordinator

@Composable
internal fun PlayerSheetAwareBackHandler(
    coordinator: PlayerSheetBackCoordinator,
    enabled: Boolean,
    onBack: () -> Unit
) {
    val currentOnBack by rememberUpdatedState(onBack)
    val callback = remember {
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = currentOnBack()
        }
    }
    val dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
    val lifecycleOwner = LocalLifecycleOwner.current

    SideEffect {
        coordinator.updateCallback(callback, enabled)
    }
    DisposableEffect(dispatcher, lifecycleOwner, coordinator) {
        dispatcher.addCallback(lifecycleOwner, callback)
        onDispose {
            coordinator.removeCallback(callback)
            callback.remove()
        }
    }
}
