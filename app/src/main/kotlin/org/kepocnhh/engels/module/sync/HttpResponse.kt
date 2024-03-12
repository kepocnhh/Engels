package org.kepocnhh.engels.module.sync

internal class HttpResponse(
    val version: String,
    val code: Int,
    val message: String,
    val headers: Map<String, String>,
    val body: ByteArray?,
)
