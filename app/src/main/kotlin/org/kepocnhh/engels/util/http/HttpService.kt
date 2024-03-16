package org.kepocnhh.engels.util.http

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.SocketException

abstract class HttpService(
    private val environment: HttpEnvironment,
) : Service() {
    sealed interface Broadcast {
        data class OnError(val error: Throwable) : Broadcast
        data class OnState(val state: State) : Broadcast
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

    protected abstract fun onSocketAccept(request: HttpRequest): HttpResponse

    private fun ByteArray.toPrintAsJson(): String? {
        val jsonObject = try {
            JSONObject(String(this))
        } catch (_: Throwable) {
            return null
        }
        val json = jsonObject.toString(4)
        if (json.isBlank()) return null
        val maxLength = 256
        if (json.length > maxLength) {
            return "\n---\n${json.take(maxLength)}\n...\n---"
        }
        return "\n---\n$json\n---"
    }

    private fun log(request: HttpRequest) {
        val headers = request.headers
            .toList()
            .joinToString(separator = "") { (k, v) -> "\n$k: $v" }
        val body = request.body?.toPrintAsJson().orEmpty()
        val message = """
            ${request.method} ${request.query}
        """.trimIndent() + headers + body
        Log.d(TAG, message)
    }

    private fun log(response: HttpResponse) {
        val headers = response.headers
            .toList()
            .joinToString(separator = "") { (k, v) -> "\n$k: $v" }
        val body = response.body?.toPrintAsJson().orEmpty()
        val message = """
            ${response.code} ${response.message}
        """.trimIndent() + headers + body
        Log.d(TAG, message)
    }

    private suspend fun onStarting(serverSocket: ServerSocket) {
        val address = try {
            getInetAddress().hostAddress ?: error("No address!")
        } catch (e: Throwable) {
            environment.broadcast.emit(Broadcast.OnError(e))
            Log.w(TAG, "No address: $e")
            environment.state.value = State.Stopped
            return
        }
        if (this.serverSocket != null) TODO("State: ${environment.state.value}")
        this.serverSocket = serverSocket
        Log.d(TAG, "on starting:$address:${serverSocket.localPort}")
        environment.state.value = State.Started("$address:${serverSocket.localPort}")
        while (environment.state.value is State.Started) {
            try {
                serverSocket.accept().use { socket ->
                    val request = HttpRequest.read(socket.getInputStream())
                    log(request) // todo
                    val response = try {
                        onSocketAccept(request)
                    } catch (e: Throwable) {
                        Log.w(TAG, "On socket accept error: $e")
                        onInternalErrorIntercept(e) ?: HttpResponse(
                            version = "1.1",
                            code = 500,
                            message = "Internal Server Error",
                            headers = mapOf(
                                "User-Agent" to environment.userAgent,
                            ),
                            body = null,
                        ) // todo
                    }
                    HttpResponse.write(response, socket.getOutputStream())
                    log(response) // todo
                }
            } catch (e: SocketException) {
                if (environment.state.value is State.Stopping) break
                TODO("error: $e")
            } catch (e: Throwable) {
                TODO("error: $e")
            }
        }
        environment.state.value = State.Stopped
    }

    protected open fun onInternalErrorIntercept(error: Throwable): HttpResponse? {
        return null
    }

    private fun onStarting() {
        Log.d(TAG, "on starting...")
        checkState { it == State.Starting }
        scope.launch {
            withContext(Dispatchers.IO) {
//                val portNumber = 0 // todo
                val portNumber = 40631
                onStarting(ServerSocket(portNumber))
            }
        }
    }

    private suspend fun onState(newState: State) {
        val oldState = oldState
        this.oldState = newState
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
        environment.broadcast.emit(Broadcast.OnState(newState))
        if (newState == State.Stopped) {
            stopSelf()
        }
    }

    private fun onStopping() {
        Log.d(TAG, "on stopping...")
        checkState { it == State.Stopping }
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

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "on create[${hashCode()}]...") // todo
        environment.state.onEach(::onState).launchIn(scope)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "on destroy...")
        job.cancel()
    }

    private fun onStartServer() {
        Log.d(TAG, "on start server...")
        checkState { it == State.Stopped }
        environment.state.value = State.Starting
    }

    private fun onStopServer() {
        Log.d(TAG, "on stop server...")
        checkState { it is State.Started }
        environment.state.value = State.Stopping
    }

    private fun checkState(predicate: (State) -> Boolean) {
        val state = environment.state.value
        if (!predicate(state)) error("State: $state")
    }

    protected fun onAction(intent: Intent) {
        val intentAction = intent.action ?: error("No intent action!")
        if (intentAction.isEmpty()) error("Intent action is empty!")
        val action = Action.entries.firstOrNull { it.name == intentAction } ?: error("No action!")
        when (action) {
            Action.StartServer -> onStartServer()
            Action.StopServer -> onStopServer()
            Action.StopForeground -> stopForeground(STOP_FOREGROUND_REMOVE)
            Action.StartForeground -> {
                val notificationId: Int = intent.getIntExtra(Notification.EXTRA_NOTIFICATION_ID, -1)
                val notification: Notification = intent.getParcelableExtra("notification") ?: TODO()
                startForeground(notificationId, notification)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) error("No intent!")
        onAction(intent)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    protected fun noBodyResponse(
        code: Int,
        message: String,
    ): HttpResponse {
        return HttpResponse(
            version = "1.1",
            code = code,
            message = message,
            headers = mapOf(
                "User-Agent" to environment.userAgent,
            ),
            body = null,
        )
    }

    protected fun jsonResponse(
        code: Int,
        message: String,
        json: String,
    ): HttpResponse {
        val body = json.toByteArray()
        return HttpResponse(
            version = "1.1",
            code = code,
            message = message,
            headers = mapOf(
                "User-Agent" to environment.userAgent,
                "Content-Type" to "application/json",
                "Content-Length" to body.size.toString(),
            ),
            body = body,
        )
    }

    protected sealed interface ParseBodyResult<out T : Any> {
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

    protected fun ParseBodyResult.Failure.toResponse(): HttpResponse {
        return jsonResponse(
            code = 400,
            message = "Bad Request",
            json = JSONObject()
                .put("Type", type.name)
                .toString(),
        )
    }

    protected fun <T : Any> HttpRequest.parseBodyJson(
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

    protected fun <B : Any, R : Any> HttpRequest.parseBody(
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

    companion object {
        private const val TAG = "[HttpService]"

        private fun getInetAddress(): InetAddress {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (!interfaces.hasMoreElements()) error("No interfaces!")
            return interfaces
                .asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?: error("No addresses!")
        }

        inline fun <reified T : HttpService> startService(context: Context, action: Action) {
            val type = T::class.java
            val intent = Intent(context, type)
            intent.action = action.name
            context.startService(intent)
        }
    }
}
