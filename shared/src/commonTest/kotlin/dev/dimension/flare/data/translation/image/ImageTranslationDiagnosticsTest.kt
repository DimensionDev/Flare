package dev.dimension.flare.data.translation.image

import dev.dimension.flare.common.UploadMedia
import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import dev.dimension.flare.ui.model.UiMedia
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageTranslationDiagnosticsTest {
    private val media = UiMedia.Image("https://images.example/private.png?signature=private-signature")
    private val config =
        ImageTranslationConfig(ImageTranslationProvider.OpenAIImages)
            .withValue("apiKey", "configured-private-key")
            .withValue("model", "image-model")

    @Test
    fun httpFailureRetainsOriginalProviderMessageAndUrl() =
        runTest {
            val client =
                HttpClient(
                    MockEngine {
                        respond(
                            """
                            {"error":{"code":"invalid_api_key","message":"Invalid configured-private-key at ${media.url}"},
                             "debug":"private-body"}
                            """.trimIndent(),
                            HttpStatusCode.Unauthorized,
                            headersOf("x-request-id", "request-123"),
                        )
                    },
                )
            try {
                val error = assertFailsWith<ImageTranslationException> { client.imagePost("https://service.example/", buildJsonObject {}) }
                assertEquals(ImageTranslationError.Authentication, error.reason)
                assertEquals("Authentication", error.message)
                val details = error.imageTranslationDetails(config, "zh")
                assertContains(details, "HTTP status: 401")
                assertContains(details, "Provider code: invalid_api_key")
                assertContains(details, "Request ID: request-123")
                assertContains(details, "Model: image-model")
                assertContains(details, "Invalid configured-private-key at " + media.url)
                assertFalse(details.contains("private-body"))
            } finally {
                client.close()
            }
        }

    @Test
    fun businessFailuresWithSuccessfulHttpStatusRetainProviderMessageAndRequestId() =
        runTest {
            for ((provider, payload, code) in listOf(
                Triple(
                    ImageTranslationProvider.Tencent,
                    """
                    {"Response":{"Error":{"Code":"AuthFailure.InvalidSecretId","Message":"Access denied"},
                     "RequestId":"business-request"}}
                    """.trimIndent(),
                    "AuthFailure.InvalidSecretId",
                ),
                Triple(
                    ImageTranslationProvider.Qwen,
                    """{"code":"InvalidParameter","message":"Access denied","request_id":"business-request"}""",
                    "InvalidParameter",
                ),
                Triple(
                    ImageTranslationProvider.Gemini,
                    """{"error":{"code":403,"message":"Access denied"},"request_id":"business-request"}""",
                    "403",
                ),
            )) {
                val client = HttpClient(MockEngine { respond(payload) })
                try {
                    val adapter =
                        when (provider) {
                            ImageTranslationProvider.Tencent -> TencentImageTranslationAdapter(client)
                            ImageTranslationProvider.Qwen -> QwenImageTranslationAdapter(client)
                            else -> GeminiImageTranslationAdapter(client)
                        }
                    val settings =
                        config
                            .copy(
                                provider = provider,
                            ).withValue("baseUrl", "https://service.example")
                            .withValue("model", "qwen-mt-image-2.0")
                    val error = assertFailsWith<ImageTranslationException> { adapter.translate(input(), settings, "zh") }
                    assertEquals(200, error.diagnostics.httpStatus)
                    assertEquals(code, error.diagnostics.code)
                    assertEquals("Access denied", error.diagnostics.message)
                    assertEquals("business-request", error.diagnostics.requestId)
                } finally {
                    client.close()
                }
            }
        }

    @Test
    fun missingImageRetainsRequestContextWithoutDisplayingModelOutput() =
        runTest {
            val client =
                HttpClient(
                    MockEngine {
                        respond(
                            """{"data":[],"message":"private translated content"}""",
                            headers = headersOf("request-id", "empty-image-request"),
                        )
                    },
                )
            try {
                val error =
                    assertFailsWith<ImageTranslationException> { OpenAIImageTranslationAdapter(client).translate(input(), config, "zh") }
                assertEquals(ImageTranslationError.NoImage, error.reason)
                assertEquals("empty-image-request", error.diagnostics.requestId)
                assertFalse(error.imageTranslationDetails(config, "zh").contains("private translated content"))
            } finally {
                client.close()
            }
        }

    @Test
    fun successfulImageWithNullErrorIsStillAccepted() =
        runTest {
            val client = HttpClient(MockEngine { respond("""{"error":null,"data":[{"b64_json":"fixture"}]}""") })
            try {
                assertEquals(
                    ImageTranslationOutput.Encoded("fixture"),
                    OpenAIImageTranslationAdapter(client).translate(input(), config, "zh"),
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun fallbackKeepsTheFinalFailureAndNotesTheEarlierAttempt() =
        runTest {
            var calls = 0
            val client =
                HttpClient(
                    MockEngine {
                        calls++
                        if (calls == 1) {
                            respond("""{"error":{"code":"image_download_failed"}}""", HttpStatusCode.BadRequest)
                        } else {
                            respond(
                                """{"error":{"code":"service_unavailable","message":"Service is busy"}}""",
                                HttpStatusCode.ServiceUnavailable,
                                headersOf("x-request-id", "final-attempt"),
                            )
                        }
                    },
                )
            try {
                val error =
                    assertFailsWith<ImageTranslationException> { OpenAIImageTranslationAdapter(client).translate(input(), config, "zh") }
                val details = error.imageTranslationDetails(config, "zh")
                assertEquals(2, calls)
                assertContains(details, "HTTP status: 503")
                assertContains(details, "Provider code: service_unavailable")
                assertContains(details, "Request ID: final-attempt")
                assertContains(details, "An earlier image input attempt also failed.")
            } finally {
                client.close()
            }
        }

    @Test
    fun nonJsonGatewayFailureRetainsStatusAndPlainTextReason() =
        runTest {
            val client =
                HttpClient(
                    MockEngine {
                        respond(
                            "<html>Gateway unavailable</html>\nAuthorization: Bearer private-token",
                            HttpStatusCode.BadGateway,
                        )
                    },
                )
            try {
                val error = assertFailsWith<ImageTranslationException> { client.imagePost("https://service.example/", buildJsonObject {}) }
                val details = error.imageTranslationDetails(config, "zh")
                assertContains(details, "HTTP status: 502")
                assertContains(details, "Gateway unavailable")
                assertContains(details, "Authorization: Bearer private-token")
            } finally {
                client.close()
            }
        }

    @Test
    fun malformedJsonErrorRetainsTheOriginalReasonWithoutImageData() =
        runTest {
            val body =
                "{\"message\":\"Invalid input\",\"prompt\":\"request content\",\"image\":\"data:image/png;base64," +
                    "a".repeat(200) + "\",\"broken"
            val client = HttpClient(MockEngine { respond(body, HttpStatusCode.BadRequest) })
            try {
                val error = assertFailsWith<ImageTranslationException> { client.imagePost("https://service.example/", buildJsonObject {}) }
                val details = error.imageTranslationDetails(config, "zh")
                assertContains(details, "HTTP status: 400")
                assertContains(details, "Invalid input")
                assertContains(details, "request content")
                assertFalse(details.contains("a".repeat(200)))
            } finally {
                client.close()
            }
        }

    @Test
    fun unrecognizedJsonErrorShapeStillShowsItsOriginalReasonWithoutBase64() =
        runTest {
            val body =
                "{\"detail\":[{\"msg\":\"Model is unavailable\"}],\"api_key\":\"original-key\",\"data\":\"" +
                    "a".repeat(200) + "\"}"
            val client = HttpClient(MockEngine { respond(body, HttpStatusCode.UnprocessableEntity) })
            try {
                val error = assertFailsWith<ImageTranslationException> { client.imagePost("https://service.example/", buildJsonObject {}) }
                val details = error.imageTranslationDetails(config, "zh")
                assertContains(details, "Model is unavailable")
                assertContains(details, "original-key")
                assertFalse(details.contains("a".repeat(200)))
            } finally {
                client.close()
            }
        }

    @Test
    fun ordinaryDiagnosticsAreNotRedactedEvenWhenTheyLookLikeLongTokens() {
        val raw =
            listOf(
                "configured-private-key request prompt",
                "Authorization: Bearer original-auth-value",
                "Cookie: first=original-cookie; second=another-cookie",
                "api_key=\"original api key\" secretKey='original secret' access_token=original-token",
                "https://user:password@host.example/private/path?Signature=original-signature",
                "file:///private/account/cache.png",
                "/Users/private-user/cache/source.png",
                "Request ID: " + "a".repeat(200),
                "api_key=sk-" + "b".repeat(200),
                """{"code":"invalid_base64","invalid_base64":"request failed"}""",
            ).joinToString("\n")
        assertEquals(raw, raw.withoutImageData())
    }

    @Test
    fun imageAndBase64DataAreOmittedWithoutConsumingTheFollowingErrorText() {
        val encoded = "a".repeat(200)
        val raw =
            listOf(
                "data:image/png;base64," + encoded,
                "data:image/png;base64," + "b".repeat(76),
                "c".repeat(76),
                "Next reason: the provider rejected the image.",
                "{\"b64_json\":\"" + encoded + "\",\"message\":\"Invalid input\"}",
                "base64='" + encoded + "'",
                "image_base64=" + encoded,
                "{\"data\":\"" + encoded + "\",\"message\":\"Invalid image\"}",
                "Raw images:",
                "/9j/" + encoded,
                "iVBORw0KGgo" + encoded,
                "UklGR" + encoded,
                "R0lGOD" + encoded,
                "\"data:image/png;base64," + encoded + "\\n" + encoded + "\"",
            ).joinToString("\n")
        val details = raw.withoutImageData()
        for (payload in listOf(encoded, "b".repeat(76), "c".repeat(76), "data:image", "/9j/", "iVBORw0KGgo", "UklGR", "R0lGOD")) {
            assertFalse(details.contains(payload), "Must omit image or Base64 data")
        }
        assertContains(details, "Next reason: the provider rejected the image.")
        assertContains(details, "\"message\":\"Invalid input\"")
        assertContains(details, "[Base64 data omitted]")
        assertContains(details, "[image data omitted]")
    }

    @Test
    fun largeQuotedBase64FieldsAreOmittedWithoutOverflow() {
        for (payload in listOf(
            "a".repeat(1_000_000),
            ("a".repeat(76) + "\\n\\/").repeat(10_000),
            ("a".repeat(76) + "\n").repeat(10_000),
        )) {
            for (field in listOf("\"b64_json\":\"$payload\"", "base64='$payload'")) {
                val details = (field + "\nReason: Forbidden").withoutImageData()
                assertFalse(details.contains("a".repeat(76)))
                assertContains(details, "[Base64 data omitted]")
                assertContains(details, "Reason: Forbidden")
            }
        }
    }

    @Test
    fun wrappedImageDataKeepsAdjacentSingleWordReasons() {
        for ((prefix, omitted) in listOf(
            "data:image/png;base64," to "[Base64 data omitted]",
            "/9j/" to "[image data omitted]",
            "image_base64=" to "image_base64=[Base64 data omitted]",
        )) {
            for (reason in listOf("Forbidden", "Unauthorized", "InvalidModel", "Error")) {
                val details =
                    ("Input: " + prefix + ("a".repeat(76) + "\n").repeat(4_000) + "YQ==\n$reason\nRequest ID: original-id")
                        .withoutImageData()
                assertEquals("Input: $omitted\n$reason\nRequest ID: original-id", details)
            }
        }
    }

    @Test
    fun completedImageValuesKeepTheFollowingLongRequestId() {
        val requestId = "a".repeat(200)
        for (image in listOf(
            "\"data:image/png;base64," + "b".repeat(76) + "\"",
            "{\"b64_json\":\"" + "b".repeat(76) + "\"}",
            "data:image/png;base64," + "b".repeat(76) + " (invalid)",
        )) {
            assertContains((image + "\n" + requestId).withoutImageData(), requestId)
        }
    }

    @Test
    fun displayFormattingRemovesControlCharactersAndBoundsLongErrors() {
        assertEquals("message\nnext\tvalue", "\u0000message\nnext\tvalue\u0001".withoutImageData())
        val long = "Useful error. ".repeat(1_000).withoutImageData()
        assertTrue(long.length <= 8_012)
        assertTrue(long.endsWith("[truncated]"))
    }

    @Test
    fun localValidationKeepsItsCauseStageAndOriginalPath() {
        val error = ImageTranslationException(ImageTranslationError.UnsupportedInput, stage = "Prepare input image")
        error.addSuppressed(IllegalArgumentException("Image has 65000000 pixels; limit is 64000000. file:///private/source.png"))
        val details = error.imageTranslationDetails(config, "zh")
        assertContains(details, "Stage: Prepare input image")
        assertContains(details, "65000000 pixels; limit is 64000000")
        assertContains(details, "file:///private/source.png")
    }

    private suspend fun input() = ImageTranslationInput(media.url, UploadMedia.fromBytes("source", byteArrayOf(1), "image/png")) { it }
}
