package dev.dimension.flare.data.translation.image

import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import dev.dimension.flare.data.network.ai.OpenAIService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ImageTranslationModelDiscoveryTest {
    private fun config(provider: ImageTranslationProvider) =
        ImageTranslationConfig(provider)
            .withValue("baseUrl", "https://service.example/custom/v1/")
            .withValue("apiKey", "test-key")

    @Test
    fun openAIReusesExistingModelEndpointAndDoesNotRequireAModel() =
        runTest {
            val discovery =
                discovery { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("https://service.example/custom/v1/models", request.url.toString())
                    assertEquals("Bearer test-key", request.headers[HttpHeaders.Authorization])
                    respond("""{"data":[{"id":"z-image"},{"id":"a-image"},{"id":"a-image"},{"id":""}]}""")
                }
            assertEquals(listOf("a-image", "z-image"), discovery.models(config(ImageTranslationProvider.OpenAIImages)))
        }

    @Test
    fun geminiFollowsPagesAndKeepsVersionedModelIds() =
        runTest {
            var requests = 0
            val discovery =
                discovery { request ->
                    requests++
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("/custom/v1/models", request.url.encodedPath)
                    assertEquals("test-key", request.headers["x-goog-api-key"])
                    assertEquals(null, request.url.parameters["key"])
                    assertEquals("1000", request.url.parameters["pageSize"])
                    if (requests == 1) {
                        assertEquals(null, request.url.parameters["pageToken"])
                        respond("""{"models":[{"name":"models/z-image-001"},{"name":""}],"nextPageToken":"page two"}""")
                    } else {
                        assertEquals("page two", request.url.parameters["pageToken"])
                        respond("""{"models":[{"name":"models/a-image"},{"name":"models/z-image-001"}]}""")
                    }
                }
            assertEquals(listOf("a-image", "z-image-001"), discovery.models(config(ImageTranslationProvider.Gemini)))
            assertEquals(2, requests)
        }

    @Test
    fun geminiRejectsAuthenticationErrorsMalformedResponsesAndRepeatedPages() =
        runTest {
            for ((body, status) in listOf(
                "{}" to HttpStatusCode.Unauthorized,
                "not-json" to HttpStatusCode.OK,
                """{"models":[],"nextPageToken":"same"}""" to HttpStatusCode.OK,
            )) {
                val discovery = discovery { respond(body, status) }
                assertFailsWith<Exception> { discovery.models(config(ImageTranslationProvider.Gemini)) }
            }
            assertEquals(emptyList(), discovery { respond("""{"models":[]}""") }.models(config(ImageTranslationProvider.Gemini)))
        }

    @Test
    fun discoveryTimesOutAndPreservesCallerCancellation() =
        runTest {
            assertFailsWith<TimeoutCancellationException> {
                discovery { awaitCancellation() }.models(config(ImageTranslationProvider.Gemini))
            }
            assertFailsWith<CancellationException> {
                discovery { throw CancellationException("configuration changed") }.models(config(ImageTranslationProvider.OpenAIImages))
            }
        }

    @Test
    fun rejectsOversizedCatalogueBeforeParsing() =
        runTest {
            val large = """{"models":[{"name":"models/""" + "a".repeat(4 * 1024 * 1024) + "\"}]}"
            val error =
                assertFailsWith<IllegalStateException> {
                    discovery { respond(large) }.models(config(ImageTranslationProvider.Gemini))
                }
            assertEquals("Model catalogue is too large", error.message)
        }

    private fun TestScope.discovery(handler: MockRequestHandler): ImageTranslationModelDiscovery {
        fun engine() =
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler(handler)
                },
            )
        return ImageTranslationModelDiscovery(
            OpenAIService { config -> HttpClient(engine(), config) },
            clientFactory = { HttpClient(engine()) },
        )
    }
}
