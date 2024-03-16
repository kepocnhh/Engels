package org.kepocnhh.engels.util.http

import java.io.OutputStream

class HttpResponse(
    val version: String,
    val code: Int,
    val message: String,
    val headers: Map<String, String>,
    val body: ByteArray?,
) {
    companion object {
        fun write(response: HttpResponse, stream: OutputStream) {
            val builder = StringBuilder()
                .append("HTTP/${response.version}")
                .append(" ")
                .append(response.code.toString())
                .append(" ")
                .append(response.message)
                .append("\r\n")
            response.headers.forEach { (key, value) ->
                builder.append("$key: $value")
                    .append("\r\n")
            }
            val bytes = builder
                .append("\r\n")
                .toString()
                .toByteArray()
            stream.write(bytes)
            if (response.body != null) {
                stream.write(response.body)
            }
            stream.flush()
        }
    }
}
