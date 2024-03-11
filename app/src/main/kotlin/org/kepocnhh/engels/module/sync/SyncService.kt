package org.kepocnhh.engels.module.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import kotlin.math.absoluteValue

internal class SyncService : Service() {
    sealed interface Broadcast {
        data class OnError(val e: Throwable) : Broadcast
    }

    sealed interface State {
        data object Stopped : State
        data object Stopping : State
        data class Started(val address: String) : State
        data object Starting : State
    }

    enum class Action {
        StartForeground,
        StopForeground,
        StartServer,
        StopServer,
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var oldState: State = State.Stopped
    private var serverSocket: ServerSocket? = null

    private fun onSocketAccept(socket: Socket) {
        Log.d(TAG, "on socket accept(${socket.remoteSocketAddress})...")
        val reader = socket.getInputStream().bufferedReader()
        val headers = mutableListOf<String>()
//        val lines = reader.readLines()
        var contentLength: Int? = null
        while (true) {
            val header = reader.readLine()
            if (header.isNullOrEmpty()) break
            headers.add(header)
            val index = header.indexOf(':')
            if (index < 1) continue
            if (index > header.length - 3) continue
            val key = header.substring(0, index)
            val value = header.substring(index + 2, header.length)
            if (key.equals("Content-Length", true)) {
                contentLength = value.toIntOrNull()
            }
        }
        val request = headers.joinToString(separator = "\n")
        Log.d(TAG, "request:\n\t---\n$request\n\t---")
        val body = contentLength?.takeIf { it > 0 }?.let {
            ByteArray(it) {
                reader.read().toByte()
            }
        }
        if (body != null) {
            Log.d(TAG, "request:body:\n\t---\n${String(body)}\n\t---")
        }
        val response = StringBuilder()
            .append("HTTP/1.1 200 Success")
            .append("\r\n")
            .append("\r\n")
            .toString()
        val stream = socket.getOutputStream()
        stream.write(response.toByteArray())
        stream.flush()
    }

    private suspend fun onStarting(serverSocket: ServerSocket) {
        val address = try {
            getInetAddress().hostAddress ?: error("No address!")
        } catch (e: Throwable) {
            _broadcast.emit(Broadcast.OnError(e))
            Log.w(TAG, "No address: $e")
            _state.value = State.Stopped
            return
        }
        if (this.serverSocket != null) TODO("State: ${state.value}")
        this.serverSocket = serverSocket
        Log.d(TAG, "on starting:$address:${serverSocket.localPort}")
        _state.value = State.Started("$address:${serverSocket.localPort}")
        while (state.value is State.Started) {
            try {
                serverSocket.accept().use(::onSocketAccept)
            } catch (e: SocketException) {
                if (state.value is State.Stopping) break
                TODO("error: $e")
            } catch (e: Throwable) {
                TODO("error: $e")
            }
        }
        _state.value = State.Stopped
    }

    private fun onStarting() {
        Log.d(TAG, "on starting...")
        if (state.value != State.Starting) error("connect state: ${state.value}")
        scope.launch {
            withContext(Dispatchers.IO) {
//                val portNumber = 0 // todo
                val portNumber = 40631
                onStarting(ServerSocket(portNumber))
            }
        }
    }

    private fun onStartServer() {
        Log.d(TAG, "on start server...")
        if (state.value != State.Stopped) error("connect state: ${state.value}")
        _state.value = State.Starting
    }

    private fun onStopping() {
        Log.d(TAG, "on stopping...")
        if (state.value != State.Stopping) error("connect state: ${state.value}")
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    checkNotNull(serverSocket).close()
                } finally {
                    serverSocket = null
                }
            }
        }
    }

    private fun onStopServer() {
        Log.d(TAG, "on stop server...")
        if (state.value !is State.Started) error("connect state: ${state.value}")
        _state.value = State.Stopping
    }

    private fun onStartCommand(intent: Intent) {
        val intentAction = intent.action ?: error("No intent action!")
        if (intentAction.isEmpty()) error("Intent action is empty!")
        val action = Action.entries.firstOrNull { it.name == intentAction } ?: error("No action!")
        when (action) {
            Action.StartServer -> onStartServer()
            Action.StopServer -> onStopServer()
            Action.StopForeground -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            Action.StartForeground -> {
                val notificationId: Int = intent.getIntExtra("notificationId", -1)
                val notification: Notification = intent.getParcelableExtra("notification") ?: TODO()
                startForeground(notificationId, notification)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) error("No intent!")
        onStartCommand(intent)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun onState(newState: State) {
        val oldState = oldState
        this.oldState = newState
//        Log.d(TAG, "old $oldState -> new $newState")
        when (oldState) {
            State.Stopped -> {
                when (newState) {
                    State.Stopped -> TODO("old $oldState -> new $newState")
                    State.Starting -> {
                        val message = """
                            * $oldState
                             \
                              * $newState
                        """.trimIndent()
                        Log.d(TAG, message)
                        onStarting()
                    }
                    is State.Started -> TODO("old $oldState -> new $newState")
                    State.Stopping -> TODO("old $oldState -> new $newState")
                }
            }
            State.Starting -> {
                when (newState) {
                    State.Stopped -> {
                        val message = """
                              * $oldState
                             /
                            * $newState
                        """.trimIndent()
                        Log.d(TAG, message)
                    }
                    State.Starting -> TODO("old $oldState -> new $newState")
                    is State.Started -> {
                        val message = """
                            * $oldState
                             \
                              * $newState
                        """.trimIndent()
                        Log.d(TAG, message)
                    }
                    State.Stopping -> TODO("old $oldState -> new $newState")
                }
            }
            is State.Started -> {
                when (newState) {
                    State.Stopped -> TODO("old $oldState -> new $newState")
                    State.Starting -> TODO("old $oldState -> new $newState")
                    is State.Started -> TODO("old $oldState -> new $newState")
                    State.Stopping -> {
                        val message = """
                              * $oldState
                             /
                            * $newState
                        """.trimIndent()
                        Log.d(TAG, message)
                        onStopping()
                    }
                }
            }
            State.Stopping -> {
                when (newState) {
                    State.Stopped -> {
                        val message = """
                              * $oldState
                             /
                            * $newState
                        """.trimIndent()
                        Log.d(TAG, message)
                    }
                    State.Starting -> TODO("old $oldState -> new $newState")
                    is State.Started -> TODO("old $oldState -> new $newState")
                    State.Stopping -> TODO("old $oldState -> new $newState")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "on create[${hashCode()}]...") // todo
        state.drop(1).onEach(::onState).launchIn(scope)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "on destroy...")
        job.cancel()
    }

    companion object {
        private const val TAG = "[Sync]"
        private val CHANNEL_ID = "${SyncService::class.java.name}:CHANNEL"
        private const val CHANNEL_NAME = "Sync service"
        private val NOTIFICATION_ID = System.currentTimeMillis().toInt().absoluteValue

        private val _broadcast = MutableSharedFlow<Broadcast>()
        val broadcast = _broadcast.asSharedFlow()

        private val _state = MutableStateFlow<State>(State.Stopped)
        val state = _state.asStateFlow()

        private fun getInetAddress(): InetAddress {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (!interfaces.hasMoreElements()) error("No interfaces!")
            val addresses = interfaces
                .asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .toList()
            if (addresses.isEmpty()) error("No addresses!")
            Log.d(TAG, "addresses: $addresses")
            return addresses
                .filterIsInstance<Inet4Address>()
                .single { !it.isLoopbackAddress }
        }

        private fun intent(context: Context, action: Action): Intent {
            val intent = Intent(context, SyncService::class.java)
            intent.action = action.name
            return intent
        }

        fun startServer(context: Context) {
            val intent = intent(context, Action.StartServer)
            context.startService(intent)
        }

        fun stopServer(context: Context) {
            val intent = intent(context, Action.StopServer)
            context.startService(intent)
        }

        private fun Context.notify(notification: Notification) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.checkChannel()
            notificationManager.notify(NOTIFICATION_ID, notification)
        }

        private fun NotificationManager.checkChannel() {
            val channel: NotificationChannel? = getNotificationChannel(CHANNEL_ID)
            if (channel == null) {
                createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH,
                    ),
                )
            }
        }

        private fun Context.buildNotification(title: String): Notification {
            return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
        }

        fun startForeground(context: Context, title: String) {
            val intent = intent(context, Action.StartForeground)
            val notification = context.buildNotification(title = title)
            context.notify(notification)
            intent.putExtra("notification", notification)
            context.startService(intent)
        }

        fun stopForeground(context: Context) {
            val intent = intent(context, Action.StopForeground)
            context.startService(intent)
        }
    }
}
