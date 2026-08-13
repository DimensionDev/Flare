package dev.dimension.flare.feature.plugin.host

import dev.dimension.flare.feature.plugin.wire.HttpAuthorizationV1
import dev.dimension.flare.feature.plugin.wire.HttpBodyV1
import dev.dimension.flare.feature.plugin.wire.HttpMultipartPartV1
import dev.dimension.flare.feature.plugin.wire.HttpRequestV1
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PluginHttpHostTest {
    @Test
    fun rejectsCrossOriginAndDangerousRawHeaders() =
        runBlocking {
            val transport = RecordingTransport()
            val host = PluginHttpHost(transport)
            val context = context(authOrigins = setOf("https://auth.example"))

            assertFails {
                host.execute(HttpRequestV1(url = "https://other.example/api"), context, 30_000)
            }
            assertFails {
                host.execute(
                    HttpRequestV1(url = "https://instance.example/api", headers = mapOf("Authorization" to "secret")),
                    context,
                    30_000,
                )
            }
            assertFails {
                host.execute(HttpRequestV1(url = "http://instance.example/api"), context, 30_000)
            }
            assertTrue(transport.requests.isEmpty())
        }

    @Test
    fun usesStructuredAuthorizationCookiesAndHidesResponseCookies() =
        runBlocking {
            val transport =
                RecordingTransport {
                    PluginTransportResponseV1(
                        status = 200,
                        headers = mapOf("Link" to listOf("<next>"), "Set-Cookie" to listOf("session=secret")),
                        body = "ok".encodeToByteArray(),
                    )
                }
            val response =
                PluginHttpHost(transport).execute(
                    request =
                        HttpRequestV1(
                            url = "https://instance.example/api",
                            authorization = HttpAuthorizationV1.Bearer("token"),
                            cookies =
                                listOf(
                                    dev.dimension.flare.feature.plugin.wire
                                        .HttpCookieV1("session", "cookie"),
                                ),
                        ),
                    context = context(),
                    callTimeoutMillis = 30_000,
                )

            assertEquals("Bearer token", transport.requests.single().headers["Authorization"])
            assertEquals("session=cookie", transport.requests.single().headers["Cookie"])
            assertEquals("ok", response.body)
            assertEquals(listOf("<next>"), response.headers["link"])
            assertTrue("set-cookie" !in response.headers)
        }

    @Test
    fun onlyFollowsSameOriginGetRedirects() =
        runBlocking {
            val sameOrigin =
                RecordingTransport { request ->
                    if (request.url.endsWith("/start")) {
                        PluginTransportResponseV1(302, mapOf("Location" to listOf("/done")), byteArrayOf())
                    } else {
                        PluginTransportResponseV1(200, emptyMap(), "done".encodeToByteArray())
                    }
                }
            val response =
                PluginHttpHost(sameOrigin).execute(
                    HttpRequestV1(url = "https://instance.example/start"),
                    context(),
                    30_000,
                )
            assertEquals("done", response.body)
            assertEquals(2, sameOrigin.requests.size)

            val crossOrigin =
                RecordingTransport {
                    PluginTransportResponseV1(302, mapOf("Location" to listOf("https://auth.example/done")), byteArrayOf())
                }
            assertFails {
                PluginHttpHost(crossOrigin).execute(
                    HttpRequestV1(url = "https://instance.example/start"),
                    context(authOrigins = setOf("https://auth.example")),
                    30_000,
                )
            }
            Unit
        }

    @Test
    fun resolvesOnlyInvocationAssetsWithoutReadingThem() =
        runBlocking {
            val asset = BufferAsset("image".encodeToByteArray())
            val transport =
                RecordingTransport { request ->
                    val part =
                        (request.body as PluginTransportBodyV1.Multipart)
                            .parts
                            .single() as PluginTransportMultipartPartV1.Asset
                    assertSame(asset, part.value)
                    assertEquals(0, asset.openCount)
                    PluginTransportResponseV1(200, emptyMap(), "ok".encodeToByteArray())
                }
            val request =
                HttpRequestV1(
                    method = "POST",
                    url = "https://instance.example/upload",
                    body = HttpBodyV1.Multipart(listOf(HttpMultipartPartV1.Asset("file", "asset-1"))),
                )

            PluginHttpHost(transport).execute(request, context(assets = mapOf("asset-1" to asset)), 120_000)
            assertEquals(0, asset.openCount)
            assertFails { PluginHttpHost(transport).execute(request, context(), 120_000) }
            Unit
        }

    @Test
    fun rejectsOversizedAndNonUtf8Responses() =
        runBlocking {
            val oversized = RecordingTransport { PluginTransportResponseV1(200, emptyMap(), ByteArray(MAX_RESPONSE_BYTES + 1)) }
            assertFails {
                PluginHttpHost(oversized).execute(HttpRequestV1(url = "https://instance.example/api"), context(), 30_000)
            }
            val invalidUtf8 = RecordingTransport { PluginTransportResponseV1(200, emptyMap(), byteArrayOf(0xc3.toByte())) }
            assertFails {
                PluginHttpHost(invalidUtf8).execute(HttpRequestV1(url = "https://instance.example/api"), context(), 30_000)
            }
            Unit
        }

    private fun context(
        authOrigins: Set<String> = emptySet(),
        assets: Map<String, PluginAsset> = emptyMap(),
    ): PluginInvocationContextV1 =
        if (assets.isEmpty()) {
            PluginInvocationContextV1.login(
                pluginId = PLUGIN_ID,
                platformId = PLATFORM_ID,
                packageHash = HASH,
                candidateOrigin = "https://instance.example",
                authOrigins = authOrigins,
                locale = "en",
            )
        } else {
            PluginInvocationContextV1.account(
                pluginId = PLUGIN_ID,
                platformId = PLATFORM_ID,
                packageHash = HASH,
                origin = "https://instance.example",
                accountId = "1",
                locale = "en",
                credential = MemoryCredential(),
                assets = assets,
            )
        }
}

private class RecordingTransport(
    private val response: suspend (PluginTransportRequestV1) -> PluginTransportResponseV1 = {
        PluginTransportResponseV1(200, emptyMap(), "ok".encodeToByteArray())
    },
) : PluginHttpTransport {
    val requests = mutableListOf<PluginTransportRequestV1>()

    override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 {
        requests += request
        return response(request)
    }
}

internal class BufferAsset(
    private val bytes: ByteArray,
) : PluginAsset {
    var openCount = 0
    override val size: Long = bytes.size.toLong()
    override val fileName: String = "image.png"
    override val mimeType: String = "image/png"

    override fun openSource(): Source {
        openCount++
        return Buffer().write(bytes)
    }
}

private class MemoryCredential : PluginCredentialAccess {
    private var value: kotlinx.serialization.json.JsonElement = kotlinx.serialization.json.JsonObject(emptyMap())

    override suspend fun read(): kotlinx.serialization.json.JsonElement = value

    override suspend fun replace(value: kotlinx.serialization.json.JsonElement) {
        this.value = value
    }
}

private const val PLUGIN_ID = "dev.dimension.flare.test.plugin"
private const val PLATFORM_ID = "TestPlugin"
private val HASH = "a".repeat(64)
