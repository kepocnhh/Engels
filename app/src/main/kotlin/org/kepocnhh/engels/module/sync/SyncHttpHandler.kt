package org.kepocnhh.engels.module.sync

import org.json.JSONObject
import org.kepocnhh.engels.App
import org.kepocnhh.engels.BuildConfig
import org.kepocnhh.engels.entity.ItemsUploadRequest
import org.kepocnhh.engels.entity.Meta
import org.kepocnhh.engels.entity.Session
import org.kepocnhh.engels.util.http.HttpHandler
import org.kepocnhh.engels.util.http.HttpRequest
import org.kepocnhh.engels.util.http.HttpResponse
import org.kepocnhh.engels.util.http.ParseBodyResult
import org.kepocnhh.engels.util.http.parseBody
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

internal class SyncHttpHandler : HttpHandler {
    private val commonHeaders = mapOf(
        "User-Agent" to "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}",
    )

    private fun JSONObject.toMeta(): Meta {
        return Meta(
            id = UUID.fromString(getString("id")),
            created = getLong("created").milliseconds,
            updated = getLong("updated").milliseconds,
            hash = getString("hash"),
        )
    }

    private fun <T : Any> MutableList<T>.with(item: T): MutableList<T> {
        add(item)
        return this
    }

    private fun <T : Any> MutableList<T>.withoutFirst(predicate: (T) -> Boolean): MutableList<T> {
        for (i in indices) {
            val it = this[i]
            if (predicate(it)) {
                removeAt(i)
                return this
            }
        }
        return this
    }

    private fun onPostMetaSync(request: HttpRequest): HttpResponse {
        val result = request.parseBodyJson {
            it.toMeta()
        }
        val clientMeta = when (result) {
            is ParseBodyResult.Failure -> return result.toResponse()
            is ParseBodyResult.Success -> result.value
        }
        val serverMeta = App.locals.metas.firstOrNull { it.id == clientMeta.id }
        if (serverMeta == null) {
            val now = System.currentTimeMillis().milliseconds
            val session = Session(
                id = UUID.randomUUID(),
                expires = now + 1.hours,
            )
            App.locals.requests += ItemsUploadRequest(
                meta = clientMeta,
                session = session,
            )
            return httpResponse(
                code = 201,
                message = "Created",
                headers = mapOf("Session-Id" to session.id.toString()),
            )
        }
        return httpResponse(
            code = 501,
            message = "Not Implemented",
        )
    }

    private fun onPostItems(request: HttpRequest): HttpResponse {
        val sessionId = request.headers["Session-Id"]?.let {
            UUID.fromString(it)
        } ?: TODO()
        val uploadRequest = App.locals.requests.firstOrNull {
            it.session.id == sessionId
        } ?: TODO()
        check(uploadRequest.session.expires.inWholeMilliseconds > System.currentTimeMillis())
        val body = request.body
        checkNotNull(body)
        check(body.isNotEmpty())
        App.locals.requests -= uploadRequest
        App.locals.metas += uploadRequest.meta
        App.locals.items += uploadRequest.meta.id to body
        return httpResponse(
            code = 200,
            message = "OK",
        )
    }

    private val routing = mapOf(
        "/v1/meta/sync" to mapOf(
            "POST" to ::onPostMetaSync,
        ),
        "/v1/items" to mapOf(
            "POST" to ::onPostItems,
        ),
    )

    override fun onSocketAccept(request: HttpRequest): HttpResponse {
        val route = routing[request.query] ?: return httpResponse(
            code = 404,
            message = "No Found",
        )
        val transform = route[request.method] ?: return httpResponse(
            code = 405,
            message = "Method Not Allowed",
        )
        return transform(request)
    }

    override fun onInternalErrorIntercept(error: Throwable): HttpResponse? {
        return httpResponse(
            code = 500,
            message = "Internal Server Error",
        )
    }

    private fun httpResponse(
        code: Int,
        message: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
    ): HttpResponse {
        return HttpResponse(
            version = "1.1",
            code = code,
            message = message,
            headers = headers + commonHeaders,
            body = body,
        )
    }

    private fun jsonResponse(
        code: Int,
        message: String,
        headers: Map<String, String> = emptyMap(),
        json: String,
    ): HttpResponse {
        val body = json.toByteArray()
        return httpResponse(
            code = code,
            message = message,
            headers = headers + mapOf(
                "Content-Type" to "application/json",
                "Content-Length" to body.size.toString(),
            ),
            body = body,
        )
    }

    private fun ParseBodyResult.Failure.toResponse(): HttpResponse {
        return jsonResponse(
            code = 400,
            message = "Bad Request",
            json = JSONObject()
                .put("Type", type.name)
                .toString(),
        )
    }

    private fun <T : Any> HttpRequest.parseBodyJson(
        transform: (JSONObject) -> T,
    ): ParseBodyResult<T> {
        return parseBody(
            supported = {
                setOf("application/json")
            },
            processing = {
                JSONObject(String(it))
            },
            transform = transform,
        )
    }
}
