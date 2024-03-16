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
import org.kepocnhh.engels.util.http.HttpEnvironment
import org.kepocnhh.engels.util.http.HttpRequest
import org.kepocnhh.engels.util.http.HttpResponse
import org.kepocnhh.engels.util.http.HttpService
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

    override fun onSocketAccept(request: HttpRequest): HttpResponse {
        when (request.query) {
            "/sync" -> {
                when (request.method) {
                    "POST" -> {
                        val result = request.parseBodyJson {
                            it.toMeta()
                        }
                        val clientMeta = when (result) {
                            is ParseBodyResult.Failure -> return result.toResponse()
                            is ParseBodyResult.Success -> result.value
                        }
                        val serverMeta = App.ldp.metas.firstOrNull {
                            it.id == clientMeta.id
                        }
                        if (serverMeta == null) {
                            App.ldp.metas += clientMeta
                            return jsonResponse(
                                code = 200,
                                message = "Success",
                                json = JSONObject()
                                    .put("Status", "Created")
                                    .toString(),
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
                                json = JSONObject()
                                    .put("Status", "Updated")
                                    .toString(),
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
                                        json = JSONObject()
                                            .put("Status", "UpdateRequired")
                                            .toString(),
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

    companion object {
        private const val TAG = "[Sync]"
        private val _channel = NotificationChannel(
            "${SyncService::class.java.name}:CHANNEL",
            "Sync service",
            NotificationManager.IMPORTANCE_HIGH,
        )
        private val NOTIFICATION_ID = System.currentTimeMillis().toInt().absoluteValue
        private val _environment = HttpEnvironment(
            userAgent = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}",
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
