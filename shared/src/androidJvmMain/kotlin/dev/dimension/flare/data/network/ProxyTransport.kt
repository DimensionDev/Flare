package dev.dimension.flare.data.network

import dev.dimension.flare.data.model.ProxyConnection
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyProtocol
import dev.dimension.flare.data.model.ProxyRef
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.sse.SSECapability
import io.ktor.client.plugins.websocket.WebSocketCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.SocketFactory
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

private data class MediaOrigin(
    val url: okhttp3.HttpUrl,
)

private val mediaClientsMutex = Mutex()
private val mediaClients = mutableMapOf<ProxyRef, Pair<ProxySnapshot, OkHttpClient>>()

// Media3 asks for a new call for every segment, so an existing player also reads the latest policy.
public fun proxyCallFactory(media: dev.dimension.flare.ui.model.UiMedia): Call.Factory =
    Call.Factory { request ->
        val proxy = media.proxy
        runBlocking(Dispatchers.IO) {
            mediaClientsMutex.withLock {
                val snapshot = ProxyNetwork.snapshot(proxy)
                val previous = mediaClients[proxy]
                val client =
                    if (previous?.first == snapshot) {
                        previous.second
                    } else {
                        previous?.second?.dispatcher?.cancelAll()
                        previous?.second?.connectionPool?.evictAll()
                        OkHttpClient
                            .Builder()
                            .configureProxy(snapshot.connection)
                            .eventListenerFactory {
                                object : EventListener() {
                                    private var unsubscribe: (() -> Unit)? = null

                                    override fun callStart(call: Call) {
                                        unsubscribe = snapshot.onInvalidated(call::cancel)
                                    }

                                    override fun callEnd(call: Call) {
                                        unsubscribe?.invoke()
                                    }

                                    override fun callFailed(
                                        call: Call,
                                        ioe: IOException,
                                    ) {
                                        unsubscribe?.invoke()
                                    }
                                }
                            }.addNetworkInterceptor { chain ->
                                val current = chain.request()
                                val origin = current.tag(MediaOrigin::class.java)?.url
                                val sameOrigin =
                                    origin != null && origin.scheme == current.url.scheme && origin.host == current.url.host &&
                                        origin.port == current.url.port
                                chain.proceed(
                                    if (sameOrigin) {
                                        current
                                    } else {
                                        current
                                            .newBuilder()
                                            .removeHeader("Authorization")
                                            .removeHeader("Cookie")
                                            .build()
                                    },
                                )
                            }.build()
                            .also {
                                mediaClients[proxy] = snapshot to it
                            }
                    }
                client.newCall(request.newBuilder().tag(MediaOrigin::class.java, MediaOrigin(media.url.toHttpUrl())).build())
            }
        }
    }

internal actual val platformEngineCapabilities: Set<HttpClientEngineCapability<*>> =
    setOf(HttpTimeoutCapability, WebSocketCapability, SSECapability)

internal actual fun createPlatformHttpClientEngine(connection: ProxyConnection): HttpClientEngine =
    OkHttp.create { config { configureProxy(connection) } }

public fun okhttp3.OkHttpClient.Builder.configureProxy(connection: ProxyConnection): okhttp3.OkHttpClient.Builder =
    apply {
        when (connection.mode) {
            ProxyMode.SYSTEM -> {
                proxySelector(ProxySelector.getDefault())
            }

            ProxyMode.DIRECT -> {
                proxy(Proxy.NO_PROXY)
            }

            ProxyMode.PROXY -> {
                proxyAuthenticator(Authenticator.NONE)
                if (connection.protocol == ProxyProtocol.HTTPS) {
                    proxy(
                        Proxy(
                            Proxy.Type.HTTP,
                            InetSocketAddress.createUnresolved(requireNotNull(connection.host), requireNotNull(connection.port)),
                        ),
                    )
                    dns { hostname ->
                        if (!hostname.equals(connection.host, ignoreCase = true)) {
                            throw UnknownHostException("Local target DNS is disabled while using a proxy")
                        }
                        Dns.SYSTEM.lookup(hostname)
                    }
                    socketFactory(HttpsProxySocketFactory())
                } else {
                    proxy(Proxy.NO_PROXY)
                    dns { hostname -> listOf(InetAddress.getByAddress(hostname, byteArrayOf(127, 0, 0, 1))) }
                    socketFactory(Socks5SocketFactory(connection))
                    // Placeholder addresses cannot prove that two HTTP/2 origins share a remote IP.
                    protocols(listOf(Protocol.HTTP_1_1))
                }
                retryOnConnectionFailure(false)
            }

            ProxyMode.INHERIT -> {
                error("Resolve the proxy policy before creating a connection")
            }
        }
    }

private class Socks5SocketFactory(
    private val proxy: ProxyConnection,
) : SocketFactory() {
    override fun createSocket(): Socket = Socks5Socket(proxy)

    override fun createSocket(
        host: String,
        port: Int,
    ): Socket = createSocket().apply { connect(InetSocketAddress.createUnresolved(host, port)) }

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket =
        createSocket().apply {
            bind(InetSocketAddress(localHost, localPort))
            connect(InetSocketAddress.createUnresolved(host, port))
        }

    override fun createSocket(
        host: InetAddress,
        port: Int,
    ): Socket = createSocket(InetSocketAddress(host, port).hostString, port)

    override fun createSocket(
        host: InetAddress,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = createSocket(InetSocketAddress(host, port).hostString, port, localHost, localPort)
}

private class Socks5Socket(
    private val proxy: ProxyConnection,
) : Socket(Proxy.NO_PROXY) {
    override fun connect(
        endpoint: SocketAddress,
        timeout: Int,
    ) {
        val target = endpoint as InetSocketAddress
        val hostname = target.hostString.toByteArray(Charsets.US_ASCII)
        require(hostname.size in 1..255) { "SOCKS5 target hostname is too long" }
        val readTimeout = soTimeout
        try {
            // Only the proxy address is resolved locally. The target's labelled address is never used to connect.
            val endpoint = InetSocketAddress(requireNotNull(proxy.host), requireNotNull(proxy.port))
            super.connect(endpoint, timeout)
            soTimeout = if (timeout > 0) timeout else 30_000
            val input = getInputStream()
            val output = getOutputStream()
            output.write(byteArrayOf(5, 1, 0))
            output.flush()
            if (input.read() != 5) throw SocketException("Invalid SOCKS5 proxy response")
            if (input.read() != 0) throw SocketException("Proxy authentication is required or unsupported")
            if (':' in target.hostString) {
                // A colon identifies an IPv6 literal; this API does not query DNS for literals.
                val address = InetAddress.getByName(target.hostString.removeSurrounding("[", "]")).address
                output.write(byteArrayOf(5, 1, 0, if (address.size == 16) 4 else 1))
                output.write(address)
            } else {
                output.write(byteArrayOf(5, 1, 0, 3, hostname.size.toByte()))
                output.write(hostname)
            }
            output.write(byteArrayOf((target.port shr 8).toByte(), target.port.toByte()))
            output.flush()
            if (input.read() != 5) throw SocketException("Invalid SOCKS5 proxy response")
            val reply = input.read()
            if (reply != 0) throw SocketException("SOCKS5 proxy could not reach target (reply $reply)")
            if (input.read() != 0) throw SocketException("Invalid SOCKS5 reserved byte")
            val addressSize =
                when (input.read()) {
                    1 -> 4
                    3 -> input.read().also { if (it < 0) throw SocketException("Truncated SOCKS5 response") }
                    4 -> 16
                    else -> throw SocketException("Invalid SOCKS5 address type")
                }
            repeat(addressSize + 2) {
                if (input.read() < 0) throw SocketException("Truncated SOCKS5 response")
            }
            soTimeout = readTimeout
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    override fun connect(endpoint: SocketAddress) = connect(endpoint, 0)
}

// OkHttp supplies CONNECT and target TLS; this supplies TLS on the connection to the proxy itself.
private class HttpsProxySocketFactory : SocketFactory() {
    private val factory = SSLSocketFactory.getDefault() as SSLSocketFactory

    private fun checked(socket: Socket): Socket =
        (socket as SSLSocket).apply {
            sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
        }

    override fun createSocket(): Socket = checked(factory.createSocket())

    override fun createSocket(
        host: String,
        port: Int,
    ): Socket = checked(factory.createSocket(host, port))

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = checked(factory.createSocket(host, port, localHost, localPort))

    override fun createSocket(
        host: InetAddress,
        port: Int,
    ): Socket = checked(factory.createSocket(host, port))

    override fun createSocket(
        host: InetAddress,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = checked(factory.createSocket(host, port, localHost, localPort))
}
