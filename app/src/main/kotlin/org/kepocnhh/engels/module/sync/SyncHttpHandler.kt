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

    private fun Meta.toJSONObject(): JSONObject {
        return JSONObject()
            .put("id", id.toString())
            .put("created", created.inWholeMilliseconds)
            .put("updated", updated.inWholeMilliseconds)
            .put("hash", hash)
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

    private fun addItemsUploadRequest(meta: Meta): HttpResponse {
        val exists = App.locals.requests.any { it.meta.id == meta.id }
        if (exists) return httpResponse(
            code = 403,
            message = "Forbidden",
        )
        val now = System.currentTimeMillis().milliseconds
        val session = Session(
            id = UUID.randomUUID(),
            expires = now + 1.hours,
        )
        App.locals.requests += ItemsUploadRequest(
            meta = meta,
            session = session,
        )
        return httpResponse(
            code = 201,
            message = "Created",
            headers = mapOf("Session-Id" to session.id.toString()),
        )
    }

    private fun onPostItemsSync(request: HttpRequest): HttpResponse {
        val clientMeta = when (val result = request.parseBodyJson { it.toMeta() }) {
            is ParseBodyResult.Failure -> return result.toResponse()
            is ParseBodyResult.Success -> result.value
        }
        val serverMeta = App.locals.metas.firstOrNull { it.id == clientMeta.id }
            ?: return addItemsUploadRequest(meta = clientMeta)
        if (serverMeta.created != clientMeta.created) {
            return jsonResponse(
                code = 409,
                message = "Conflict",
                json = JSONObject()
                    .put("message", "The creation time does not match!")
                    .toString(),
            )
        }
        if (serverMeta.hash == clientMeta.hash) {
            if (serverMeta.updated != clientMeta.updated) {
                return jsonResponse(
                    code = 409,
                    message = "Conflict",
                    json = JSONObject()
                        .put("message", "The update time does not match!")
                        .toString(),
                )
            }
            return httpResponse(
                code = 304,
                message = "Not Modified",
            )
        }
        if (serverMeta.updated == clientMeta.updated) {
            return jsonResponse(
                code = 409,
                message = "Conflict",
                json = JSONObject()
                    .put("message", "The update time is the same!")
                    .toString(),
            )
        }
        if (serverMeta.updated > clientMeta.updated) {
            val body = App.locals.items[serverMeta.id] ?: TODO()
            return httpResponse(
                code = 200,
                message = "OK",
                headers = mapOf("Meta-Updated" to serverMeta.updated.inWholeMilliseconds.toString()),
                body = body,
            )
        }
        return addItemsUploadRequest(meta = clientMeta)
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
        App.locals.metas = App.locals.metas
            .toMutableList()
            .withoutFirst { it.id == uploadRequest.meta.id }
            .with(uploadRequest.meta)
        App.locals.items += uploadRequest.meta.id to body
        return httpResponse(
            code = 200,
            message = "OK",
        )
    }

    private fun onGetItems(request: HttpRequest): HttpResponse {
        // todo get UUID
        val metaIdValue = request.headers["Meta-Id"]
        if (metaIdValue.isNullOrBlank()) {
            return jsonResponse(
                code = 400,
                message = "Bad Request",
                json = JSONObject()
                    .put("message", "No meta ID!")
                    .toString(),
            )
        }
        val metaId = try {
            UUID.fromString(metaIdValue)
        } catch (_: Throwable) {
            return jsonResponse(
                code = 400,
                message = "Bad Request",
                json = JSONObject()
                    .put("message", "Wrong meta ID!")
                    .toString(),
            )
        }
        val meta = App.locals.metas.firstOrNull { it.id == metaId }
        if (meta == null) {
            return httpResponse(
                code = 404,
                message = "Not Found",
            )
        }
        val body = App.locals.items[meta.id] ?: TODO()
        return httpResponse(
            code = 200,
            message = "OK",
            headers = mapOf(
                "Meta-Created" to meta.created.inWholeMilliseconds.toString(),
                "Meta-Updated" to meta.updated.inWholeMilliseconds.toString(),
            ),
            body = body,
        )
    }

    private val routing = mapOf(
        "/v1/items/sync" to mapOf(
            "POST" to ::onPostItemsSync,
        ),
        "/v1/items" to mapOf(
            "POST" to ::onPostItems,
            "GET" to ::onGetItems,
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
