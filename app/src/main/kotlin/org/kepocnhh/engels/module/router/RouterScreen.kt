package org.kepocnhh.engels.module.router

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import org.kepocnhh.engels.App
import org.kepocnhh.engels.BuildConfig
import org.kepocnhh.engels.module.sync.SyncService
import org.kepocnhh.engels.util.showToast

@Composable
internal fun RouterScreen() {
    val TAG = "[Router]"
    val context = LocalContext.current
    val insets = App.Theme.insets
    LaunchedEffect(Unit) {
        SyncService.broadcast.collect {
            when (it) {
                is SyncService.Broadcast.OnError -> {
                    context.showToast("Error: ${it.e}")
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding(),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val text = """
                appId: ${BuildConfig.APPLICATION_ID}
                version: ${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}
            """.trimIndent()
            BasicText(text = text)
            Spacer(modifier = Modifier.weight(1f))
            val syncState = SyncService.state.collectAsState().value
            when (syncState) {
                SyncService.State.Stopped -> {
                    BasicText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clickable {
                                Log.d(TAG, "start server...")
                                SyncService.startServer(context)
                            }
                            .wrapContentSize(),
                        text = "start server",
                    )
                }
                is SyncService.State.Started -> {
                    BasicText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .wrapContentSize(),
                        text = syncState.address,
                    )
                    BasicText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clickable {
                                Log.d(TAG, "stop server...")
                                SyncService.stopServer(context)
                            }
                            .wrapContentSize(),
                        text = "stop server",
                    )
                }
                SyncService.State.Starting -> {
                    // todo
                }
                SyncService.State.Stopping -> {
                    // todo
                }
            }
        }
    }
}
