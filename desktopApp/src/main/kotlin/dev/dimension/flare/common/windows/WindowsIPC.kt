package dev.dimension.flare.common.windows

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

internal class WindowsIPC(
    private val ports: Ports,
    private val onDeeplink: (String) -> Unit,
) {
    @Serializable
    data class IPCEvent<T>(
        @SerialName("Type")
        val type: String,
        @SerialName("Data")
        val data: T?,
    ) {
        @Serializable
        data class DeeplinkData(
            @SerialName("Deeplink")
            val deeplink: String,
        )
    }

    companion object {
        fun parsePorts(args: Array<String>): Ports {
            var kRecv = -1
            var cRecv = -1
            val it = args.iterator()
            while (it.hasNext()) {
                when (it.next()) {
                    "--kotlin-recv" -> kRecv = it.next().toInt()
                    "--csharp-recv" -> cRecv = it.next().toInt()
                }
            }
            require(kRecv > 0 && cRecv > 0) { "Missing --kotlin-recv/--csharp-recv" }
            return Ports(kRecv, cRecv)
        }
    }

    data class Ports(
        val kotlinRecv: Int,
        val csharpRecv: Int,
    )

    private val sender by lazy {
        IpcSender("127.0.0.1", ports.csharpRecv)
    }

    init {
        thread(isDaemon = true, name = "kotlin-ipc-server") {
            runKotlinServer(ports.kotlinRecv)
        }
        sender.start()
    }

    fun runKotlinServer(port: Int) {
        ServerSocket(port, 50, InetAddress.getByName("127.0.0.1")).use { server ->
            while (true) {
                val socket = server.accept()
                thread(isDaemon = true) { handleClient(socket) }
            }
        }
    }

    fun handleClient(socket: Socket) {
        socket.use { s ->
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                val jsonObject =
                    kotlinx.serialization.json.Json
                        .parseToJsonElement(line)
                val type = jsonObject.jsonObject["Type"]?.jsonPrimitive?.content
                when (type) {
                    "deeplink" -> {
                        val data = jsonObject.jsonObject["Data"] ?: continue
                        val dataObj =
                            line
                                .decodeJson(
                                    IPCEvent.serializer(IPCEvent.DeeplinkData.serializer()),
                                ).data ?: continue
                        onDeeplink(dataObj.deeplink)
                    }

                    else -> {
                    }
                }
            }
        }
    }

    fun <T> send(
        event: IPCEvent<T>,
        serializer: KSerializer<T>,
    ) {
        val json = event.encodeJson(IPCEvent.serializer(serializer))
        sender.send(json)
    }

    fun sendShutdown() {
        send(IPCEvent<Unit>("shutdown", null), Unit.serializer())
    }

    private class IpcSender(
        private val host: String,
        private val port: Int,
    ) {
        @Volatile private var writer: OutputStreamWriter? = null

        fun start() {
            thread(isDaemon = true, name = "kotlin-ipc-client") {
                while (true) {
                    try {
                        val sock = Socket(host, port)
                        writer = OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8)
                        BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8)).readLine()
                    } catch (e: Exception) {
                        Thread.sleep(250)
                    } finally {
                        writer = null
                    }
                }
            }
        }

        @Synchronized
        fun send(line: String) {
            try {
                writer?.apply {
                    write(line)
                    write("\n")
                    flush()
                }
            } catch (_: Exception) {
                writer = null
            }
        }
    }
}
