package dev.dimension.flare.data.translation.image

import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageTranslationAdaptersTest {
    private val original = "original bytes".encodeUtf8()

    private suspend fun input(public: Boolean = false) =
        ImageTranslationInput(
            if (public) "https://source.example/image.png" else null,
            dev.dimension.flare.common.UploadMedia
                .fromBytes("original.png", original.toByteArray(), "image/png"),
            prepareImage = { it },
        )

    private fun config(provider: ImageTranslationProvider) =
        ImageTranslationConfig(provider)
            .withValue("apiKey", "test-key")
            .withValue("model", "image-model")
            .withValue("secretId", "test-id")
            .withValue("secretKey", "test-secret")

    @Test
    fun preparationValidationHasInputErrorAndDoesNotRetryProvider() =
        runTest {
            for (failure in listOf(IllegalArgumentException("invalid image"), IllegalStateException("image limit"))) {
                var calls = 0
                val client =
                    HttpClient(
                        MockEngine {
                            calls++
                            respond("""{"error":{"code":"image_download_failed"}}""", HttpStatusCode.BadRequest)
                        },
                    )
                try {
                    val input = input(public = true).let { ImageTranslationInput(it.publicUrl, it.media) { throw failure } }
                    val error =
                        assertFailsWith<ImageTranslationException> {
                            OpenAIImageTranslationAdapter(client).translate(input, config(ImageTranslationProvider.OpenAIImages), "en")
                        }
                    assertEquals(ImageTranslationError.UnsupportedInput, error.reason)
                    assertTrue(error.suppressedExceptions.any { it === failure })
                    assertEquals(1, calls)
                } finally {
                    client.close()
                }
            }
        }

    @Test
    fun preparationCancellationAndIoFailureRemainUnchanged() =
        runTest {
            for (failure in listOf(CancellationException("cancelled"), okio.IOException("file read failed"))) {
                var calls = 0
                val client =
                    HttpClient(
                        MockEngine {
                            calls++
                            respond("""{"error":{"code":"image_download_failed"}}""", HttpStatusCode.BadRequest)
                        },
                    )
                try {
                    val input = input(public = true).let { ImageTranslationInput(it.publicUrl, it.media) { throw failure } }
                    val error =
                        assertFailsWith<Exception> {
                            OpenAIImageTranslationAdapter(client).translate(input, config(ImageTranslationProvider.OpenAIImages), "en")
                        }
                    assertEquals(failure::class, error::class)
                    assertEquals(failure.message, error.message)
                    assertEquals(1, calls)
                } finally {
                    client.close()
                }
            }
        }

    @Test
    fun requestUrlValidationMatchesLocalReadiness() {
        listOf(
            "missing-scheme.example/v1",
            "ftp://service.example/v1",
            "https://user:password@service.example/v1",
            "https://service.example/v1?key=secret",
            "https://service.example/v1#fragment",
        ).forEach { url ->
            val config = config(ImageTranslationProvider.OpenAIImages).withValue("baseUrl", url)
            assertFalse(config.configured)
            assertEquals(
                ImageTranslationError.NotConfigured,
                assertFailsWith<ImageTranslationException> { checkedImageServiceUrl(url) }.reason,
            )
        }
        val localUrl = "http://127.0.0.1:18473/v1/"
        assertTrue(config(ImageTranslationProvider.OpenAIImages).withValue("baseUrl", localUrl).configured)
        assertEquals(localUrl.dropLast(1), checkedImageServiceUrl(localUrl))
    }

    @Test
    fun openAIUsesImageEditsAndOriginalBytesForPrivateImage() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { request ->
                        assertEquals("/v1/images/edits", request.url.encodedPath)
                        assertEquals("Bearer test-key", request.headers[HttpHeaders.Authorization])
                        assertEquals(null, request.headers[HttpHeaders.Cookie])
                        val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                        assertTrue(
                            body["images"]!!
                                .jsonArray[0]
                                .jsonObject["image_url"]
                                .toString()
                                .contains(original.base64()),
                        )
                        assertFalse(body.containsKey("messages"))
                        respond("""{"data":[{"b64_json":"cmVzdWx0"}]}""")
                    },
                )
            try {
                assertEquals(
                    ImageTranslationOutput.Encoded("cmVzdWx0"),
                    OpenAIImageTranslationAdapter(client).translate(input(), config(ImageTranslationProvider.OpenAIImages), "ja"),
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun geminiExtractsImageFromModelOutputAndDisablesServerStorage() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { request ->
                        assertEquals("/v1beta/interactions", request.url.encodedPath)
                        assertEquals("test-key", request.headers["x-goog-api-key"])
                        val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                        assertEquals("false", body["store"].toString())
                        assertEquals("image", body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
                        assertTrue(body["input"].toString().contains(original.base64()))
                        respond(
                            """
{
  "status": "completed",
  "steps": [
    {
      "type": "model_output",
      "content": [
        {
          "type": "text",
          "text": "done"
        },
        {
          "type": "image",
          "mime_type": "image/png",
          "data": "cmVzdWx0"
        }
      ]
    }
  ]
}
                            """.trimIndent(),
                        )
                    },
                )
            try {
                assertEquals(
                    ImageTranslationOutput.Encoded("cmVzdWx0"),
                    GeminiImageTranslationAdapter(client).translate(input(), config(ImageTranslationProvider.Gemini), "en"),
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun qwenUsesSynchronousContractAndRejectsPrivateInputBeforeCallingProvider() =
        runTest {
            var calls = 0
            val client =
                HttpClient(
                    MockEngine { request ->
                        calls++
                        assertEquals(null, request.headers["X-DashScope-Async"])
                        assertTrue((request.body as TextContent).text.contains("source_lang"))
                        respond("""{"output":{"image_url":"https://result.example/final.jpg"}}""")
                    },
                )
            try {
                val adapter = QwenImageTranslationAdapter(client)
                val config =
                    config(
                        ImageTranslationProvider.Qwen,
                    ).withValue("baseUrl", "https://workspace.example/api/v1").withValue("model", "qwen-mt-image-2.0")
                assertEquals(
                    ImageTranslationOutput.Remote("https://result.example/final.jpg"),
                    adapter.translate(input(public = true), config, "zh"),
                )
                assertEquals(
                    ImageTranslationError.UnsupportedInput,
                    assertFailsWith<ImageTranslationException> {
                        adapter.translate(input(), config, "zh")
                    }.reason,
                )
                assertEquals(1, calls)
            } finally {
                client.close()
            }
        }

    @Test
    fun tencentSignsRequestAndUsesBytesWithoutSourceHeaders() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { request ->
                        assertEquals("ImageTranslateLLM", request.headers["X-TC-Action"])
                        assertTrue(request.headers[HttpHeaders.Authorization]!!.startsWith("TC3-HMAC-SHA256 Credential=test-id/"))
                        assertFalse(request.headers[HttpHeaders.Authorization]!!.contains("test-secret"))
                        val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                        assertTrue(body["Data"].toString().contains(original.base64()))
                        respond("""{"Response":{"Data":"cmVzdWx0","RequestId":"request"}}""")
                    },
                )
            try {
                assertEquals(
                    ImageTranslationOutput.Encoded("cmVzdWx0"),
                    TencentImageTranslationAdapter(client).translate(input(), config(ImageTranslationProvider.Tencent), "zh"),
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun authenticationFailureIsNotRetriedAndTextOnlyResponseIsNotSuccess() =
        runTest {
            var calls = 0
            val client =
                HttpClient(
                    MockEngine {
                        calls++
                        respond("""{"error":{"message":"private details"}}""", HttpStatusCode.Unauthorized)
                    },
                )
            try {
                val error =
                    assertFailsWith<ImageTranslationException> {
                        OpenAIImageTranslationAdapter(
                            client,
                        ).translate(input(public = true), config(ImageTranslationProvider.OpenAIImages), "en")
                    }
                assertEquals(ImageTranslationError.Authentication, error.reason)
                assertFalse(error.message.orEmpty().contains("private details"))
                assertEquals(1, calls)
            } finally {
                client.close()
            }
            val textClient =
                HttpClient(
                    MockEngine { respond("""{"steps":[{"type":"model_output","content":[{"type":"text","text":"translation"}]}]}""") },
                )
            try {
                assertEquals(
                    ImageTranslationError.NoImage,
                    assertFailsWith<ImageTranslationException> {
                        GeminiImageTranslationAdapter(textClient).translate(input(), config(ImageTranslationProvider.Gemini), "en")
                    }.reason,
                )
            } finally {
                textClient.close()
            }
        }

    @Test
    fun tc3MatchesIndependentHmacFixtureAndLocaleCodesAreNormalized() {
        val authorization = tencentImageAuthorization("test-id", "test-secret", 1551113065, """{"Data":"abc","Target":"en"}""")
        assertTrue(authorization.endsWith("Signature=0dac9cd6dc60ecdc5d39e51fd4d472c9676dda0403aff404aac5b9bbad9542ca"))
        assertEquals("en", imageTranslationLanguageCode("en-US"))
        assertEquals("zh-TW", imageTranslationLanguageCode("zh-Hant-TW"))
        assertEquals("zh-HK", imageTranslationLanguageCode("zh_HK"))
    }

    @Test
    fun explicitUrlDownloadFailureFallsBackToOriginalBytesOnce() =
        runTest {
            var calls = 0
            val client =
                HttpClient(
                    MockEngine { request ->
                        calls++
                        if (calls == 1) {
                            respond("""{"error":{"code":"invalid_image_url"}}""", HttpStatusCode.BadRequest)
                        } else {
                            assertTrue((request.body as TextContent).text.contains(original.base64()))
                            respond("""{"data":[{"b64_json":"cmVzdWx0"}]}""")
                        }
                    },
                )
            try {
                assertEquals(
                    ImageTranslationOutput.Encoded("cmVzdWx0"),
                    OpenAIImageTranslationAdapter(
                        client,
                    ).translate(input(public = true), config(ImageTranslationProvider.OpenAIImages), "en"),
                )
                assertEquals(2, calls)
            } finally {
                client.close()
            }
        }

    @Test
    fun imageFailuresSharePreparedBytesAcrossProviders() =
        runTest {
            val errors =
                listOf(
                    """
                    {"error":{
                      "message":".messages[1].image[0]: Failed to download image from https://source.example/unsupported.jpg",
                      "code":"invalid_request_error"
                    }}
                    """.trimIndent(),
                    """{"error":{"message":"Image dimensions exceed the limit"}}""",
                    """{"error":{"details":[{"fieldViolations":[{"field":"input[1].uri"}]}]}}""",
                )
            for (provider in listOf(
                ImageTranslationProvider.OpenAIImages,
                ImageTranslationProvider.Gemini,
            )) {
                for (error in errors) {
                    var calls = 0
                    var preparations = 0
                    val resized = "prepared bytes".encodeUtf8()
                    val preparedMedia =
                        dev.dimension.flare.common.UploadMedia
                            .fromBytes("prepared.png", resized.toByteArray(), "image/png")
                    val input =
                        ImageTranslationInput(
                            "https://source.example/image.png",
                            dev.dimension.flare.common.UploadMedia
                                .fromBytes("original.png", original.toByteArray(), "image/png"),
                            prepareImage = {
                                preparations++
                                preparedMedia
                            },
                        )
                    val client =
                        HttpClient(
                            MockEngine { request ->
                                calls++
                                val body = (request.body as TextContent).text
                                if (calls == 1) {
                                    assertTrue(body.contains("https://source.example/image.png"))
                                    respond(error, HttpStatusCode.BadRequest)
                                } else {
                                    assertTrue(body.contains(resized.base64()))
                                    assertFalse(body.contains(original.base64()))
                                    assertFalse(body.contains("https://source.example/image.png"))
                                    respond(
                                        when (provider) {
                                            ImageTranslationProvider.OpenAIImages -> {
                                                """{"data":[{"b64_json":"cmVzdWx0"}]}"""
                                            }

                                            else -> {
                                                """
                                                {"steps":[{"type":"model_output","content":[
                                                  {"type":"image","data":"cmVzdWx0"}
                                                ]}]}
                                                """.trimIndent()
                                            }
                                        },
                                    )
                                }
                            },
                        )
                    try {
                        val adapter =
                            when (provider) {
                                ImageTranslationProvider.OpenAIImages -> OpenAIImageTranslationAdapter(client)
                                else -> GeminiImageTranslationAdapter(client)
                            }
                        assertEquals(ImageTranslationOutput.Encoded("cmVzdWx0"), adapter.translate(input, config(provider), "en"))
                        assertEquals(2, calls)
                        assertEquals(1, preparations)
                    } finally {
                        client.close()
                    }
                }
            }
        }

    @Test
    fun failedInlineRetryStopsWithoutSendingTextOnlyImageTranslation() =
        runTest {
            var calls = 0
            val client =
                HttpClient(
                    MockEngine {
                        calls++
                        respond(
                            """{"error":{"message":"Failed to download image","code":"invalid_request_error"}}""",
                            HttpStatusCode.BadRequest,
                        )
                    },
                )
            try {
                assertFailsWith<ImageTranslationException> {
                    OpenAIImageTranslationAdapter(
                        client,
                    ).translate(input(public = true), config(ImageTranslationProvider.OpenAIImages), "en")
                }
                assertEquals(2, calls)
            } finally {
                client.close()
            }
        }

    @Test
    fun multipartRetryReusesPreparedImage() =
        runTest {
            var calls = 0
            var preparations = 0
            val prepared =
                dev.dimension.flare.common.UploadMedia
                    .fromBytes("prepared.png", "prepared-file".encodeToByteArray(), "image/png")
            val input =
                ImageTranslationInput(input(public = true).publicUrl, input().media) {
                    preparations++
                    prepared
                }
            val client =
                HttpClient(
                    MockEngine { request ->
                        calls++
                        when (calls) {
                            1 -> {
                                respond("""{"error":{"code":"image_download_failed"}}""", HttpStatusCode.BadRequest)
                            }

                            2 -> {
                                respond("""{"error":{"code":"unsupported_parameter","param":"images"}}""", HttpStatusCode.BadRequest)
                            }

                            else -> {
                                val body = (request.body as OutgoingContent.WriteChannelContent).readBody().decodeToString()
                                assertTrue(body.contains("prepared-file"))
                                respond("""{"data":[{"b64_json":"cmVzdWx0"}]}""")
                            }
                        }
                    },
                )
            try {
                assertEquals(
                    ImageTranslationOutput.Encoded("cmVzdWx0"),
                    OpenAIImageTranslationAdapter(client).translate(input, config(ImageTranslationProvider.OpenAIImages), "en"),
                )
                assertEquals(3, calls)
                assertEquals(1, preparations)
            } finally {
                client.close()
            }
        }

    @Test
    fun tencentPublicImagesUsePreparedBytesInBothModes() =
        runTest {
            for (mode in listOf("0", "1")) {
                var calls = 0
                var preparations = 0
                val prepared = "prepared bytes".encodeUtf8()
                val preparedMedia =
                    dev.dimension.flare.common.UploadMedia
                        .fromBytes("prepared.png", prepared.toByteArray(), "image/png")
                val source = input(public = true)
                val input =
                    ImageTranslationInput(source.publicUrl, source.media) {
                        preparations++
                        preparedMedia
                    }
                val client =
                    HttpClient(
                        MockEngine { request ->
                            calls++
                            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                            assertEquals(prepared.base64(), body["Data"]!!.jsonPrimitive.content)
                            assertFalse("Url" in body)
                            assertEquals(mode, body["Mode"]!!.jsonPrimitive.content)
                            assertEquals("zh", body["Target"]!!.jsonPrimitive.content)
                            respond("""{"Response":{"Data":"cmVzdWx0"}}""")
                        },
                    )
                try {
                    assertEquals(
                        ImageTranslationOutput.Encoded("cmVzdWx0"),
                        TencentImageTranslationAdapter(client).translate(
                            input,
                            config(ImageTranslationProvider.Tencent).withValue("mode", mode),
                            "zh-CN",
                        ),
                    )
                    assertEquals(1, calls)
                    assertEquals(1, preparations)
                } finally {
                    client.close()
                }
            }
        }

    @Test
    fun streamedBase64PreservesEveryByteAcrossChunkBoundaries() =
        runTest {
            val bytes = ByteArray(1024 * 1024 + 7) { (it % 251).toByte() }
            for (provider in listOf(ImageTranslationProvider.OpenAIImages, ImageTranslationProvider.Gemini)) {
                val client =
                    HttpClient(
                        MockEngine { request ->
                            val content = request.body as OutgoingContent.WriteChannelContent
                            val bodyBytes = content.readBody()
                            assertEquals(bodyBytes.size.toLong(), content.contentLength)
                            val body = Json.parseToJsonElement(bodyBytes.decodeToString()).jsonObject
                            val base64 =
                                if (provider == ImageTranslationProvider.OpenAIImages) {
                                    body["images"]!!
                                        .jsonArray[0]
                                        .jsonObject["image_url"]!!
                                        .jsonPrimitive.content
                                        .substringAfter("base64,")
                                } else {
                                    body["input"]!!
                                        .jsonArray[1]
                                        .jsonObject["data"]!!
                                        .jsonPrimitive.content
                                }
                            assertEquals(bytes.toByteString().base64(), base64)
                            if (provider == ImageTranslationProvider.OpenAIImages) {
                                respond("""{"data":[{"b64_json":"cmVzdWx0"}]}""")
                            } else {
                                respond("""{"steps":[{"type":"model_output","content":[{"type":"image","data":"cmVzdWx0"}]}]}""")
                            }
                        },
                    )
                try {
                    val input =
                        ImageTranslationInput(
                            null,
                            dev.dimension.flare.common.UploadMedia
                                .fromBytes("source.png", bytes, "image/png"),
                            prepareImage = { it },
                        )
                    val adapter =
                        if (provider == ImageTranslationProvider.OpenAIImages) {
                            OpenAIImageTranslationAdapter(client)
                        } else {
                            GeminiImageTranslationAdapter(client)
                        }
                    assertEquals(ImageTranslationOutput.Encoded("cmVzdWx0"), adapter.translate(input, config(provider), "en"))
                } finally {
                    client.close()
                }
            }
        }

    @Test
    fun automaticOpenAIUsesOriginalFileOnlyAfterExplicitInputContractRejection() =
        runTest {
            var calls = 0
            val client =
                HttpClient(
                    MockEngine { request ->
                        calls++
                        if (calls == 1) {
                            assertTrue((request.body as TextContent).text.contains("https://source.example/image.png"))
                            respond("""{"error":{"code":"unknown_parameter","param":"images"}}""", HttpStatusCode.BadRequest)
                        } else {
                            val body = (request.body as OutgoingContent.WriteChannelContent).readBody().decodeToString()
                            assertTrue(body.contains("name=image[]") || body.contains("name=\"image[]\""))
                            assertTrue(body.contains(original.utf8()))
                            assertFalse(body.contains(original.base64()))
                            respond("""{"data":[{"b64_json":"cmVzdWx0"}]}""")
                        }
                    },
                )
            try {
                OpenAIImageTranslationAdapter(client).translate(input(public = true), config(ImageTranslationProvider.OpenAIImages), "en")
                assertEquals(2, calls)
            } finally {
                client.close()
            }
        }

    private suspend fun OutgoingContent.WriteChannelContent.readBody(): ByteArray =
        coroutineScope {
            val channel = ByteChannel(autoFlush = true)
            launch {
                try {
                    writeTo(channel)
                } finally {
                    channel.flushAndClose()
                }
            }
            channel.readBuffer().readByteArray()
        }

    @Test
    fun unusedProviderEditsDoNotInvalidateSelectedProviderFingerprint() {
        val before = config(ImageTranslationProvider.OpenAIImages)
        val other = before.copy(provider = ImageTranslationProvider.Gemini).withValue("apiKey", "different")
        assertEquals(before.fingerprint(), other.copy(provider = before.provider).fingerprint())
        assertTrue(before.fingerprint() != before.withValue("model", "different").fingerprint())
    }
}
