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
import org.kepocnhh.engels.entity.Meta
import org.kepocnhh.engels.module.sync.SyncService
import org.kepocnhh.engels.provider.LocalDataProvider
import org.kepocnhh.engels.util.compose.LocalOnBackPressedDispatcher
import org.kepocnhh.engels.util.compose.toPaddings
import org.kepocnhh.engels.util.http.HttpService

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

    private fun onState(state: HttpService.State) {
        // todo
    }

    private fun onBroadcast(broadcast: HttpService.Broadcast) {
        when (broadcast) {
            is HttpService.Broadcast.OnError -> {
                // todo
            }
            is HttpService.Broadcast.OnState -> {
                when (broadcast.state) {
                    is HttpService.State.Started -> {
                        SyncService.startForeground(this, title = "started: ${broadcast.state.address}")
                    }
                    HttpService.State.Starting -> {
                        // todo
                    }
                    HttpService.State.Stopped -> {
                        HttpService.startService<SyncService>(this, HttpService.Action.StopForeground)
                    }
                    HttpService.State.Stopping -> {
                        // todo
                    }
                }
            }
        }
    }

    private class MockLocalDataProvider : LocalDataProvider {
        override var metas: List<Meta> = emptyList()
    }

    override fun onCreate() {
        super.onCreate()
        _ldp = MockLocalDataProvider()
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

    companion object {
        private var _ldp: LocalDataProvider? = null
        val ldp: LocalDataProvider get() = checkNotNull(_ldp)
    }
}
