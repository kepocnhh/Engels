package org.kepocnhh.engels.module.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class HttpEnvironment(initialState: HttpService.State) {
    val broadcast = MutableSharedFlow<HttpService.Broadcast>()
    val state = MutableStateFlow(initialState)
}
