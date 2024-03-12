package org.kepocnhh.engels.module.sync


internal fun ByteArray.getContentHeaders(type: String): Map<String, String> {
    return mapOf(
        "Content-Type" to type,
        "Content-Length" to "$size",
    )
}
