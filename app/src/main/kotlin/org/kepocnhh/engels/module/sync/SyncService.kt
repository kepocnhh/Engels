package org.kepocnhh.engels.module.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.kepocnhh.engels.App
import org.kepocnhh.engels.BuildConfig
import org.kepocnhh.engels.entity.Meta
import java.util.Date
import java.util.UUID
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.milliseconds

internal class SyncService : HttpService(_environment) {
    private fun JSONObject.toMeta(): Meta {
        return Meta(
            id = UUID.fromString(getString("id")),
            created = getLong("created").milliseconds,
            updated = getLong("updated").milliseconds,
            hash = getString("hash"),
        )
    }

    private fun noBodyResponse(
        code: Int,
        message: String,
    ): HttpResponse {
        return HttpResponse(
            version = "1.1",
            code = code,
            message = message,
            headers = mapOf(
                "User-Agent" to _userAgent,
            ),
            body = null,
        )
    }

    private fun jsonResponse(
        code: Int,
        message: String,
        jsonObject: JSONObject,
    ): HttpResponse {
        val bytes = jsonObject
            .toString()
            .toByteArray()
        return HttpResponse(
            version = "1.1",
            code = code,
            message = message,
            headers = mapOf(
                "User-Agent" to _userAgent,
                "Content-Type" to "application/json",
                "Content-Length" to bytes.size.toString(),
            ),
            body = bytes,
        )
    }

    override fun onSocketAccept(request: HttpRequest): HttpResponse {
        when (request.query) {
            "/sync" -> {
                when (request.method) {
                    "POST" -> {
                        if (request.body == null) {
                            return jsonResponse(
                                code = 400,
                                message = "Bad Request",
                                jsonObject = JSONObject()
                                    .put("message", "No body!"),
                            )
                        }
                        if (request.body.isEmpty()) {
                            return jsonResponse(
                                code = 400,
                                message = "Bad Request",
                                jsonObject = JSONObject()
                                    .put("message", "Body is empty!"),
                            )
                        }
                        val clientMetaJson = try {
                            JSONObject(String(request.body))
                        } catch (e: Throwable) {
                            return jsonResponse(
                                code = 400,
                                message = "Bad Request",
                                jsonObject = JSONObject()
                                    .put("message", "Wrong json!"),
                            )
                        }
                        val clientMeta = try {
                            clientMetaJson.toMeta()
                        } catch (e: Throwable) {
                            return jsonResponse(
                                code = 400,
                                message = "Bad Request",
                                jsonObject = JSONObject()
                                    .put("message", "Wrong body!"),
                            )
                        }
                        val serverMeta = App.ldp.metas.firstOrNull {
                            it.id == clientMeta.id
                        }
                        if (serverMeta == null) {
                            App.ldp.metas += clientMeta
                            return jsonResponse(
                                code = 200,
                                message = "Success",
                                jsonObject = JSONObject()
                                    .put("Status", "Created"),
                            )
                        }
                        if (clientMeta.created != serverMeta.created) {
                            TODO("Server created: ${Date(serverMeta.created.inWholeMilliseconds)}, but client created: ${Date(clientMeta.created.inWholeMilliseconds)}!")
                        }
                        if (clientMeta.hash == serverMeta.hash) {
                            if (clientMeta.updated != serverMeta.updated) {
                                TODO("Server updated: ${Date(serverMeta.updated.inWholeMilliseconds)}, but client updated: ${Date(clientMeta.updated.inWholeMilliseconds)}!")
                            }
                            return jsonResponse(
                                code = 200,
                                message = "Success",
                                jsonObject = JSONObject()
                                    .put("Status", "Updated"),
                            )
                        } else {
                            when {
                                clientMeta.updated == serverMeta.updated -> {
                                    TODO()
                                }
                                clientMeta.updated < serverMeta.updated -> {
                                    TODO()
                                }
                                clientMeta.updated > serverMeta.updated -> {
                                    return jsonResponse(
                                        code = 200,
                                        message = "Success",
                                        jsonObject = JSONObject()
                                            .put("Status", "UpdateRequired"),
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        return noBodyResponse(
                            code = 405,
                            message = "Method Not Allowed",
                        )
                    }
                }
            }
        }
        return noBodyResponse(
            code = 404,
            message = "No Found",
        )
    }

    override fun onInternalErrorIntercept(error: Throwable): HttpResponse? {
        return HttpResponse(
            version = "1.1",
            code = 500,
            message = "Internal Server Error",
            headers = mapOf(
                "User-Agent" to _userAgent,
            ),
            body = null,
        )
    }

    companion object {
        private const val TAG = "[Sync]"
        private const val _userAgent = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}"
        private val _channel = NotificationChannel(
            "${SyncService::class.java.name}:CHANNEL",
            "Sync service",
            NotificationManager.IMPORTANCE_HIGH,
        )
        private val NOTIFICATION_ID = System.currentTimeMillis().toInt().absoluteValue
        private val _environment = HttpEnvironment(
            initialState = State.Stopped,
        )

        val broadcast = _environment.broadcast.asSharedFlow()
        val state = _environment.state.asStateFlow()

        private fun Context.notify(notification: Notification) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.checkChannel()
            notificationManager.notify(NOTIFICATION_ID, notification)
        }

        private fun NotificationManager.checkChannel() {
            if (getNotificationChannel(_channel.id) != null) return
            createNotificationChannel(_channel)
        }

        private fun Context.buildNotification(title: String): Notification {
            return NotificationCompat.Builder(this, _channel.id)
                .setContentTitle(title)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
        }

        fun startForeground(context: Context, title: String) {
            Log.d(TAG, "start foreground...")
            val intent = Intent(context, SyncService::class.java)
            intent.action = Action.StartForeground.name
            val notification = context.buildNotification(title = title)
            context.notify(notification)
            intent.putExtra(Notification.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
            intent.putExtra("notification", notification)
            context.startService(intent)
        }
    }
}
