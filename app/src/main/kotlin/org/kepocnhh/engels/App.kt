package org.kepocnhh.engels

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
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

    override fun onCreate() {
        super.onCreate()
        // todo
    }
}
