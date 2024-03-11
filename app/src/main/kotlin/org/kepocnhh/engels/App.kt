package org.kepocnhh.engels

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.flowWithLifecycle
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.kepocnhh.engels.module.sync.SyncService
import org.kepocnhh.engels.util.compose.LocalOnBackPressedDispatcher
import org.kepocnhh.engels.util.compose.toPaddings

internal class App : Application() {
    object Theme {
        private val LocalInsets = staticCompositionLocalOf<PaddingValues> { error("No insets!") }

        val insets: PaddingValues
            @Composable
            @ReadOnlyComposable
            get() = LocalInsets.current

        @Composable
        fun Composition(
            onBackPressedDispatcher: OnBackPressedDispatcher,
            content: @Composable () -> Unit,
        ) {
            val insets = LocalView.current.rootWindowInsets.toPaddings()
            CompositionLocalProvider(
                LocalOnBackPressedDispatcher provides onBackPressedDispatcher,
                LocalInsets provides insets,
                content = content,
            )
        }
    }

    private fun onState(state: SyncService.State) {
        // todo
    }

    private fun onBroadcast(broadcast: SyncService.Broadcast) {
        when (broadcast) {
            is SyncService.Broadcast.OnError -> {
                // todo
            }
            is SyncService.Broadcast.OnState -> {
                when (broadcast.state) {
                    is SyncService.State.Started -> {
                        SyncService.startForeground(this, title = "started: ${broadcast.state.address}")
                    }
                    SyncService.State.Starting -> {
                        // todo
                    }
                    SyncService.State.Stopped -> {
                        SyncService.stopForeground(this)
                    }
                    SyncService.State.Stopping -> {
                        // todo
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        SyncService.state
            .flowWithLifecycle(lifecycle, minActiveState = Lifecycle.State.CREATED)
            .onEach(::onState)
            .launchIn(lifecycle.coroutineScope)
        SyncService.broadcast
            .flowWithLifecycle(lifecycle, minActiveState = Lifecycle.State.CREATED)
            .onEach(::onBroadcast)
            .launchIn(lifecycle.coroutineScope)
        // todo
    }
}
