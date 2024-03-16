package org.kepocnhh.engels.util.http

import java.io.InputStream

class HttpRequest(
    val version: String,
    val method: String,
    val query: String,
    val headers: Map<String, String>,
    val body: ByteArray?,
) {
    companion object {
        fun read(stream: InputStream): HttpRequest {
            val reader = stream.bufferedReader()
            val firstHeader = reader.readLine()
            check(!firstHeader.isNullOrBlank())
            val split = firstHeader.split(" ")
            check(split.size == 3)
            val protocol = split[2].split("/")
            check(protocol.size == 2)
            check(protocol[0] == "HTTP")
            val version = protocol[1]
            check(version == "1.1") // todo 505 HTTP Version Not Supported
            val method = split[0]
            val query = split[1]
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine()
                if (line.isNullOrEmpty()) break
                val index = line.indexOf(':')
                if (index < 1) continue
                if (index > line.length - 3) continue
                val key = line.substring(0, index)
                val value = line.substring(index + 2, line.length)
                headers[key] = value
            }
            val body = headers["Content-Length"]
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?.let {
                    ByteArray(it) {
                        reader.read().toByte()
                    }
                }
            return HttpRequest(
                version = version,
                method = method,
                query = query,
                headers = headers,
                body = body,
            )
        }
    }
}
