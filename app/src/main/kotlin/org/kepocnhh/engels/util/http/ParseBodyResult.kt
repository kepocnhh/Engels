package org.kepocnhh.engels.util.http

internal sealed interface ParseBodyResult<out T : Any> {
    class Success<T : Any>(val value: T) : ParseBodyResult<T>
    class Failure(val type: Type) : ParseBodyResult<Nothing> {
        enum class Type {
            NoBody,
            EmptyBody,
            WrongContentType,
            WrongBody,
            UnexpectedBody,
        }
    }
}

internal fun <B : Any, R : Any> HttpRequest.parseBody(
    supported: () -> Set<String>? = { null },
    processing: (ByteArray) -> B,
    transform: (B) -> R,
): ParseBodyResult<R> {
    if (body == null) {
        return ParseBodyResult.Failure(ParseBodyResult.Failure.Type.NoBody)
    }
    if (body.isEmpty()) {
        return ParseBodyResult.Failure(ParseBodyResult.Failure.Type.EmptyBody)
    }
    val types = supported()
    if (types != null) {
        try {
            check(types.contains(headers["Content-Type"]))
        } catch (e: Throwable) {
            return ParseBodyResult.Failure(ParseBodyResult.Failure.Type.WrongContentType)
        }
    }
    val data = try {
        processing(body)
    } catch (e: Throwable) {
        return ParseBodyResult.Failure(ParseBodyResult.Failure.Type.WrongBody)
    }
    val result = try {
        transform(data)
    } catch (e: Throwable) {
        return ParseBodyResult.Failure(ParseBodyResult.Failure.Type.UnexpectedBody)
    }
    return ParseBodyResult.Success(result)
}
