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
import org.kepocnhh.engels.entity.Session
import org.kepocnhh.engels.util.http.HttpEnvironment
import org.kepocnhh.engels.util.http.HttpHandler
import org.kepocnhh.engels.util.http.HttpRequest
import org.kepocnhh.engels.util.http.HttpResponse
import org.kepocnhh.engels.util.http.HttpService
import java.util.Date
import java.util.UUID
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

internal class SyncService : HttpService(_environment) {
    private val handler: HttpHandler = SyncHttpHandler()

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

    override fun onSocketAccept(request: HttpRequest): HttpResponse {
        return handler.onSocketAccept(request)
    }

    override fun onInternalErrorIntercept(error: Throwable): HttpResponse? {
        return handler.onInternalErrorIntercept(error)
    }

    companion object {
        private const val TAG = "[Sync]"
        private val _channel = NotificationChannel(
            "${SyncService::class.java.name}:CHANNEL",
            "Sync service",
            NotificationManager.IMPORTANCE_HIGH,
        )
        private val NOTIFICATION_ID = System.currentTimeMillis().toInt().absoluteValue
        private val _environment = HttpEnvironment(State.Stopped)

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
