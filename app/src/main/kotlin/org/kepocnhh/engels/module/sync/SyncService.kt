package org.kepocnhh.engels.module.sync

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.skip
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

internal class SyncService : Service() {
    sealed interface State {
        data object Stopped : State
        data object Stopping : State
        class Started(val address: InetAddress) : State {
            override fun toString(): String {
                return "Started($address)"
            }
        }
        data object Starting : State
    }

    enum class Action {
        StartServer,
        StopServer,
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var oldState: State = State.Stopped
    private var serverSocket: ServerSocket? = null

    private fun onSocketAccept(socket: Socket) {
        Log.d(TAG, "on socket accept(${socket.remoteSocketAddress})...")
        val inputStream = socket.getInputStream()
        Log.d(TAG, "on socket input stream $inputStream...")
        val reader = BufferedReader(InputStreamReader(inputStream))
        Log.d(TAG, "on socket reader $reader...")
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

    private fun onStarted(serverSocket: ServerSocket) {
        while (state.value is State.Started) {
            try {
                serverSocket.accept()
            } catch (e: SocketException) {
                if (state.value !is State.Started) break
                TODO("error: $e")
            } catch (e: Throwable) {
                TODO("error: $e")
            }.use(::onSocketAccept)
        }
        Log.d(TAG, "on finish socket...")
    }

    private fun onStarting() {
        Log.d(TAG, "on starting...")
        if (state.value != State.Starting) error("connect state: $state")
        val serverSocket = checkNotNull(serverSocket)
        Log.d(TAG, "on starting: " + serverSocket.localSocketAddress + ":" + serverSocket.localPort)
        scope.launch {
            _state.value = State.Started(serverSocket.inetAddress)
            withContext(Dispatchers.IO) {
                onStarted(serverSocket)
            }
        }
    }

    private fun onStartServer() {
        Log.d(TAG, "on start server...")
        if (state.value != State.Stopped) error("connect state: $state")
        scope.launch {
            withContext(Dispatchers.IO) {
                if (serverSocket != null) TODO("State: $state")
                serverSocket = ServerSocket(0)
            }
            _state.value = State.Starting
        }
    }

    private fun onStopServer() {
        Log.d(TAG, "on stop server...")
        if (state.value !is State.Started) error("connect state: $state")
        scope.launch {
            _state.value = State.Stopping
        }
    }

    private fun onStopping() {
        Log.d(TAG, "on stopping...")
        if (state.value != State.Stopping) error("connect state: $state")
        scope.launch {
            withContext(Dispatchers.IO) {
                checkNotNull(serverSocket).close()
                serverSocket = null
            }
            _state.value = State.Stopped
        }
    }

    private fun onStartCommand(intent: Intent) {
        val intentAction = intent.action ?: error("No intent action!")
        if (intentAction.isEmpty()) error("Intent action is empty!")
        val action = Action.entries.firstOrNull { it.name == intentAction } ?: error("No action!")
        when (action) {
            Action.StartServer -> {
                onStartServer()
            }
            Action.StopServer -> {
                onStopServer()
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
                    State.Stopped -> TODO("old $oldState -> new $newState")
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

        private val _state = MutableStateFlow<State>(State.Stopped)
        val state = _state.asStateFlow()

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
    }
}
