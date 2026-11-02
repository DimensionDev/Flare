package dev.dimension.flare.data.translation.image

import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.shared.image.JvmImageCompressor
import dev.dimension.flare.shared.image.imageMetadata
import dev.dimension.flare.ui.model.UiMedia
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path.Companion.toPath
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ImageTranslationRecoveryTest {
    @Test
    fun localPixelLimitIsReportedAsUnsupportedInputBeforeCallingProvider() =
        runTest {
            val fs = FileSystem.SYSTEM
            val root = Files.createTempDirectory("image-translation-pixel-limit").toString().toPath()
            val file = root / "source.png"
            val image = BufferedImage(5000, 13000, BufferedImage.TYPE_BYTE_BINARY)
            try {
                ImageIO.write(image, "png", file.toFile())
            } finally {
                image.flush()
            }
            // This decodable PNG passes the repository inspector but exceeds the shared preparation pixel budget.
            assertEquals(5000, inspectTranslationImage(file.toString()).width)
            var calls = 0
            val client =
                HttpClient(
                    MockEngine {
                        calls++
                        error("Provider must not receive a rejected local image")
                    },
                )
            val repository =
                ImageTranslationRepository(
                    OkioFileStorage(fs, root, cacheRoot = root),
                    client,
                    imageCompressor = JvmImageCompressor(),
                )
            val session = repository.newSession()
            try {
                val config =
                    ImageTranslationConfig(ImageTranslationProvider.OpenAIImages)
                        .withValue("apiKey", "key")
                        .withValue("model", "image")
                val failure =
                    assertFailsWith<ImageTranslationException> {
                        repository.translate(UiMedia.Image(imageTranslationFileUrl(file.toString())), null, config, "en", session, false)
                    }
                assertEquals(ImageTranslationError.UnsupportedInput, failure.reason)
                assertEquals(0, calls)
            } finally {
                repository.closeSession(session)
                client.close()
                fs.deleteRecursively(root)
            }
        }

    @Test
    fun translationRecoversWithExistingDownloadAndSharedCompressor() =
        runTest {
            for (provider in listOf(
                ImageTranslationProvider.OpenAIImages,
                ImageTranslationProvider.Gemini,
                ImageTranslationProvider.Tencent,
            )) {
                for (privateImage in listOf(false, true)) {
                    val usesUrl = !privateImage && provider != ImageTranslationProvider.Tencent
                    val root = Files.createTempDirectory("image-translation-recovery").toString().toPath()
                    val original = png(5000, 2)
                    val output = png(10, 10).toByteString().base64()
                    val media =
                        UiMedia.Image(
                            "https://source.example/image.png",
                            proxy = ProxyRef.forAccount(MicroBlogKey("user", "source.example")),
                            customHeaders = if (privateImage) persistentMapOf("Cookie" to "source-cookie") else null,
                        )
                    var downloads = 0
                    var calls = 0
                    val client =
                        HttpClient(
                            MockEngine { request ->
                                calls++
                                assertEquals(null, request.headers["Cookie"])
                                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                                if (usesUrl && calls == 1) {
                                    assertTrue(body.toString().contains(media.url))
                                    respond(
                                        """{"error":{"message":"Failed to download image","code":"invalid_request_error"}}""",
                                        HttpStatusCode.BadRequest,
                                    )
                                } else {
                                    val encoded =
                                        when (provider) {
                                            ImageTranslationProvider.OpenAIImages -> {
                                                body["images"]!!
                                                    .jsonArray[0]
                                                    .jsonObject["image_url"]!!
                                                    .jsonPrimitive.content
                                                    .substringAfter(
                                                        "base64,",
                                                    )
                                            }

                                            ImageTranslationProvider.Gemini -> {
                                                body["input"]!!
                                                    .jsonArray[1]
                                                    .jsonObject["data"]!!
                                                    .jsonPrimitive.content
                                            }

                                            else -> {
                                                body["Data"]!!.jsonPrimitive.content
                                            }
                                        }
                                    val prepared = requireNotNull(encoded.decodeBase64()).toByteArray()
                                    val metadata = requireNotNull(imageMetadata(prepared))
                                    assertTrue(metadata.width in 1..4096)
                                    assertTrue(metadata.height in 1..4096)
                                    assertTrue(prepared.size <= 4 * 1024 * 1024)
                                    respond(
                                        when (provider) {
                                            ImageTranslationProvider.OpenAIImages -> {
                                                """{"data":[{"b64_json":"$output"}]}"""
                                            }

                                            ImageTranslationProvider.Gemini -> {
                                                """
                                                {"steps":[{"type":"model_output","content":[
                                                  {"type":"image","data":"$output"}
                                                ]}]}
                                                """.trimIndent()
                                            }

                                            else -> {
                                                """{"Response":{"Data":"$output"}}"""
                                            }
                                        },
                                    )
                                }
                            },
                        )
                    val repository =
                        ImageTranslationRepository(
                            OkioFileStorage(FileSystem.SYSTEM, root, cacheRoot = root),
                            client,
                            sourceClient = { proxy ->
                                assertEquals(media.proxy, proxy)
                                HttpClient(
                                    MockEngine { request ->
                                        downloads++
                                        assertEquals(if (privateImage) "source-cookie" else null, request.headers["Cookie"])
                                        respond(original)
                                    },
                                )
                            },
                            imageCompressor = JvmImageCompressor(),
                        )
                    val session = repository.newSession()
                    try {
                        val config =
                            ImageTranslationConfig(provider)
                                .withValue("apiKey", "key")
                                .withValue("model", "image")
                                .withValue("secretId", "id")
                                .withValue("secretKey", "secret")
                        val result = repository.translate(media, null, config, "en", session, false)
                        assertNotEquals(media.url, result.url)
                        assertEquals(10f, result.width)
                        assertEquals(if (usesUrl) 2 else 1, calls)
                        assertEquals(1, downloads)
                    } finally {
                        repository.closeSession(session)
                        client.close()
                        FileSystem.SYSTEM.deleteRecursively(root)
                    }
                }
            }
        }

    private fun png(
        width: Int,
        height: Int,
    ): ByteArray =
        ByteArrayOutputStream()
            .also {
                ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it)
            }.toByteArray()
}
