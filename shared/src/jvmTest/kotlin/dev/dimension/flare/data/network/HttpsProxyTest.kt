package dev.dimension.flare.data.network

import dev.dimension.flare.data.model.ProxyConnection
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyProtocol
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.net.InetAddress
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class HttpsProxyTest {
    @Test
    fun proxyUsesVerifiedTlsAndConnectKeepsTargetDnsRemote() {
        val (context, trust) = fixtureTls()
        val original = SSLContext.getDefault()
        SSLContext.setDefault(context)
        try {
            for (secureTarget in listOf(false, true)) {
                context.serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { proxy ->
                    val request = CompletableFuture<String>()
                    thread(isDaemon = true) {
                        runCatching {
                            (proxy.accept() as SSLSocket).use { outer ->
                                outer.soTimeout = 5000
                                outer.startHandshake()
                                val first = readHeaders(outer.inputStream)
                                if (secureTarget) {
                                    assertTrue(first.startsWith("CONNECT target-must-not-resolve.invalid:443 HTTP/1.1\r\n"))
                                    outer.outputStream.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                                    (
                                        context.socketFactory.createSocket(
                                            outer,
                                            "target-must-not-resolve.invalid",
                                            443,
                                            false,
                                        ) as SSLSocket
                                    ).use { inner ->
                                        inner.useClientMode = false
                                        inner.startHandshake()
                                        assertTrue(readHeaders(inner.inputStream).startsWith("GET /media HTTP/1.1\r\n"))
                                        inner.outputStream.write(RESPONSE.toByteArray())
                                    }
                                } else {
                                    assertTrue(first.startsWith("GET http://target-must-not-resolve.invalid/media HTTP/1.1\r\n"))
                                    outer.outputStream.write(RESPONSE.toByteArray())
                                }
                                request.complete(first)
                            }
                        }.onFailure { request.completeExceptionally(it) }
                    }
                    val client =
                        OkHttpClient
                            .Builder()
                            .configureProxy(ProxyConnection(ProxyMode.PROXY, ProxyProtocol.HTTPS, "localhost", proxy.localPort))
                            .sslSocketFactory(context.socketFactory, trust)
                            .callTimeout(5, TimeUnit.SECONDS)
                            .build()
                    try {
                        val scheme = if (secureTarget) "https" else "http"
                        client.newCall(Request.Builder().url("$scheme://target-must-not-resolve.invalid/media").build()).execute().use {
                            assertEquals("OK", it.body.string())
                        }
                        assertTrue(!request.get(5, TimeUnit.SECONDS).contains("Proxy-Authorization", true))
                    } finally {
                        client.connectionPool.evictAll()
                        client.dispatcher.executorService.shutdown()
                    }
                }
            }
            context.serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { proxy ->
                val accepted = CompletableFuture<Unit>()
                thread(isDaemon = true) {
                    runCatching {
                        (proxy.accept() as SSLSocket).use {
                            it.soTimeout = 5000
                            it.startHandshake()
                        }
                    }
                    accepted.complete(Unit)
                }
                val client =
                    OkHttpClient
                        .Builder()
                        .configureProxy(ProxyConnection(ProxyMode.PROXY, ProxyProtocol.HTTPS, "127.0.0.1", proxy.localPort))
                        .callTimeout(5, TimeUnit.SECONDS)
                        .build()
                try {
                    assertFails {
                        client
                            .newCall(
                                Request.Builder().url("http://target-must-not-resolve.invalid/").build(),
                            ).execute()
                            .close()
                    }
                    accepted.get(5, TimeUnit.SECONDS)
                } finally {
                    client.connectionPool.evictAll()
                    client.dispatcher.executorService.shutdown()
                }
            }
        } finally {
            SSLContext.setDefault(original)
        }
    }

    private fun fixtureTls(): Pair<SSLContext, X509TrustManager> {
        // Test-only certificate/key; the trust store is installed only for this test and restored above.
        val cert =
            javaClass.getResourceAsStream("/proxy/localhost-cert.pem").use {
                CertificateFactory.getInstance("X.509").generateCertificate(it)
            }
        val key =
            requireNotNull(javaClass.getResourceAsStream("/proxy/localhost-key.pem"))
                .bufferedReader()
                .use { it.readText() }
                .lineSequence()
                .filterNot { it.startsWith("---") }
                .joinToString("")
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(key)))
        val store =
            KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null)
                setKeyEntry("test", privateKey, charArrayOf(), arrayOf(cert))
            }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, charArrayOf()) }
        val trusts = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
        val context = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, trusts.trustManagers, null) }
        return context to trusts.trustManagers.filterIsInstance<X509TrustManager>().single()
    }

    private fun readHeaders(input: InputStream): String {
        val result = StringBuilder()
        while (!result.endsWith("\r\n\r\n")) {
            val value = input.read()
            check(value >= 0 && result.length < 16384)
            result.append(value.toChar())
        }
        return result.toString()
    }

    private companion object {
        const val RESPONSE = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK"
    }
}
