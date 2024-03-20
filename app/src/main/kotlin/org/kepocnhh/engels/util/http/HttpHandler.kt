package org.kepocnhh.engels.util.http

internal interface HttpHandler {
    fun onSocketAccept(request: HttpRequest): HttpResponse
    fun onInternalErrorIntercept(error: Throwable): HttpResponse?
}
