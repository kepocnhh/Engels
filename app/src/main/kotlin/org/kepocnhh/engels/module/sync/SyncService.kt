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
import org.kepocnhh.engels.BuildConfig
import kotlin.math.absoluteValue

internal class SyncService : HttpService(_environment) {
    override fun onSocketAccept(request: HttpRequest): HttpResponse {
        Log.d(TAG, "request:${request.method}:${request.query}\n\t---\n${request.headers.toList().joinToString(separator = "\n")}\n\t---")
        if (request.body != null) {
            Log.d(TAG, "request:body:${request.body.size}\n\t---\n${String(request.body)}\n\t---")
        }
//        val responseBody: ByteArray? = null
        val responseBody: ByteArray? = """
            {"time": ${System.currentTimeMillis()}}
        """.trimIndent().toByteArray()
        return HttpResponse(
            version = "1.1",
            code = 200,
            message = "Success",
            headers = mapOf(
                "User-Agent" to "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}",
            ) + responseBody?.getContentHeaders("application/json").orEmpty(),
            body = responseBody,
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
