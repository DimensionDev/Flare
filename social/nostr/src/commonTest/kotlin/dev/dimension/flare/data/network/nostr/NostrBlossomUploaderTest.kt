package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.crypto.verify
import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.UploadMedia
import dev.dimension.flare.data.network.nullableFallbackJson
import dev.dimension.flare.data.platform.NostrCredential
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import com.vitorpamplona.quartz.nip01Core.core.Event as QuartzEvent

class NostrBlossomUploaderTest {
    private val bytes = "abc".encodeToByteArray()
    private val hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun uploadSignsCorrectHashAndStreamsIdenticalBytesOnEveryRetry() =
        runTest {
            QuartzTestRelays(this).use { network ->
                network.service().use { service ->
                    var requests = 0
                    val engine =
                        MockEngine { request ->
                            requests++
                            assertEquals(HttpMethod.Put, request.method)
                            assertEquals("https://blossom.example/api/upload", request.url.toString())
                            assertEquals("image/png", request.body.contentType.toString())
                            assertContentEquals(bytes, request.body.toByteArray())
                            val header = assertNotNull(request.headers[HttpHeaders.Authorization])
                            assertTrue(header.startsWith("Nostr "))
                            val event = QuartzEvent.fromJson(Base64.decode(header.removePrefix("Nostr ")).decodeToString())
                            assertTrue(event.verify())
                            assertEquals(nostrTestSigner().pubKey, event.pubKey)
                            assertEquals(24242, event.kind)
                            assertEquals("", event.content)
                            val tags = event.tags.associate { it[0] to it[1] }
                            assertEquals("upload", tags["t"])
                            assertEquals(hash, tags["x"])
                            assertTrue(tags.getValue("expiration").toLong() - event.createdAt in 299L..301L)
                            assertTrue(event.createdAt <= Clock.System.now().epochSeconds)
                            respond(
                                """{"url":"https://cdn.example/blob.png","sha256":"$hash","size":3,"type":"image/png"}""",
                                HttpStatusCode.Created,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                    client(engine).use { http ->
                        val uploader =
                            NostrBlossomUploader({ service.buildBlossomUploadAuthEvent(it).let(::buildBlossomAuthorizationHeader) }, http)
                        val media = UploadMedia.fromBytes("a.png", bytes, "image/png")
                        repeat(2) {
                            assertEquals(
                                UploadedMedia("https://cdn.example/blob.png", "image/png", hash, 3, "alt text"),
                                uploader.upload("https://blossom.example/api/", media, "  alt text  "),
                            )
                        }
                        assertEquals(2, requests)
                    }
                }
            }
        }

    @Test
    fun missingDescriptorMetadataFallsBackToOriginalMedia() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        """{"url":"https://cdn.example/blob.png"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            client(engine).use { http ->
                val uploader =
                    NostrBlossomUploader({
                        assertEquals(hash, it)
                        "authorization"
                    }, http)
                assertEquals(
                    UploadedMedia("https://cdn.example/blob.png", "image/png", hash, 3, null),
                    uploader.upload("https://blossom.example", UploadMedia.fromBytes("a.png", bytes, "image/png"), " "),
                )
            }
        }

    @Test
    fun failedOrMalformedUploadsNeverReturnSuccessfulMedia() =
        runTest {
            val media = UploadMedia.fromBytes("a.png", bytes, "image/png")
            for ((status, body) in listOf(
                HttpStatusCode.Forbidden to "denied",
                HttpStatusCode.InternalServerError to "",
                HttpStatusCode.OK to "{}",
            )) {
                val engine = MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }
                client(engine).use { http ->
                    val uploader = NostrBlossomUploader({ "authorization" }, http)
                    val error = assertFailsWith<Exception> { uploader.upload("https://blossom.example", media, null) }
                    if (status != HttpStatusCode.OK) assertTrue(error.message.orEmpty().contains(status.value.toString()))
                    if (status == HttpStatusCode.Forbidden) assertTrue(error.message.orEmpty().contains("denied"))
                }
            }
        }

    @Test
    fun readOnlyAccountCannotAuthorizeOrSendAnUpload() =
        runTest {
            QuartzTestRelays(this).use { network ->
                network.service(NostrCredential(pubkeyHex = nostrTestSigner().pubKey)).use { service ->
                    var requests = 0
                    client(
                        MockEngine {
                            requests++
                            error("Read-only upload must never reach HTTP")
                        },
                    ).use { http ->
                        val uploader = NostrBlossomUploader({ service.buildBlossomUploadAuthEvent(it) }, http)
                        assertFailsWith<IllegalStateException> {
                            uploader.upload("https://blossom.example", UploadMedia.fromBytes("a.png", bytes, "image/png"), null)
                        }
                        assertEquals(0, requests)
                    }
                }
            }
        }

    private fun client(engine: MockEngine) =
        HttpClient(engine) {
            install(ContentNegotiation) { nullableFallbackJson(JSON) }
            expectSuccess = false
        }
}
