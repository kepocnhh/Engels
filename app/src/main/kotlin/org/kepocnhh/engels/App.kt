package org.kepocnhh.engels

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.kepocnhh.engels.util.compose.LocalOnBackPressedDispatcher

internal class App : Application() {
    object Theme {
        @Composable
        fun Composition(
            onBackPressedDispatcher: OnBackPressedDispatcher,
            content: @Composable () -> Unit,
        ) {
            CompositionLocalProvider(
                LocalOnBackPressedDispatcher provides onBackPressedDispatcher,
                content = content,
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        // todo
    }
}
