package dev.dimension.flare.data.network

import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.decodeJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NullableFallbackJsonContentConverterTest {
    @Test
    fun nullableFieldTypeMismatchFallsBackToNullInKtorClient() =
        runTest {
            val payload =
                """
                [
                    {"id":"first","invitation":"room-id"},
                    {"id":"second","invitation":{"id":"room-id","createdAt":"2026-06-10T00:00:00Z"}}
                ]
                """.trimIndent()

            val notifications = client(payload).get("https://example.com").body<List<TestNotification>>()

            assertEquals("room-id", notifications[0].invitation)
            assertNull(notifications[1].invitation)
        }

    @Test
    fun directJsonDecodeRemainsStrict() {
        val payload =
            """
            [
                {"id":"second","invitation":{"id":"room-id","createdAt":"2026-06-10T00:00:00Z"}}
            ]
            """.trimIndent()

        assertFailsWith<Exception> {
            payload.decodeJson(ListSerializer(TestNotification.serializer()))
        }
    }

    @Test
    fun nonNullableFieldTypeMismatchStillFailsInKtorClient() =
        runTest {
            val payload =
                """
                {"id":{"value":"not-a-string"}}
                """.trimIndent()

            assertFailsWith<Exception> {
                client(payload).get("https://example.com").body<TestRequiredNotification>()
            }
        }

    private fun client(payload: String): HttpClient =
        HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = payload,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
            install(ContentNegotiation) {
                nullableFallbackJson(JSON)
            }
        }

    @Serializable
    private data class TestNotification(
        val id: String,
        val invitation: String? = null,
    )

    @Serializable
    private data class TestRequiredNotification(
        val id: String,
    )
}
