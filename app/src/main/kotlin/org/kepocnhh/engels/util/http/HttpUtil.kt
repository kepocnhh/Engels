package org.kepocnhh.engels.util.http


internal fun ByteArray.getContentHeaders(type: String): Map<String, String> {
    return mapOf(
        "Content-Type" to type,
        "Content-Length" to "$size",
    )
}
