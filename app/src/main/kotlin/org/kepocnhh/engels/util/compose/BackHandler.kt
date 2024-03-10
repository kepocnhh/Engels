package org.kepocnhh.engels.util.compose

import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner

val LocalOnBackPressedDispatcher = staticCompositionLocalOf<OnBackPressedDispatcher> {
    error("No OnBackPressedDispatcher!")
}

@Composable
fun BackHandler(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onBackPressedDispatcher: OnBackPressedDispatcher = LocalOnBackPressedDispatcher.current,
    enabled: Boolean = true,
    block: () -> Unit,
) {
    val onBack = rememberUpdatedState(block).value
    val callback = remember {
        object : OnBackPressedCallback(enabled) {
            override fun handleOnBackPressed() {
                onBack()
            }
        }
    }
    SideEffect {
        callback.isEnabled = enabled
    }
    DisposableEffect(lifecycleOwner, onBackPressedDispatcher) {
        onBackPressedDispatcher.addCallback(lifecycleOwner, callback)
        onDispose {
            callback.remove()
        }
    }
}
