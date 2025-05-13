package dev.dimension.flare

import dev.dimension.flare.server.Api
import dev.dimension.flare.server.ServerContext
import dev.dimension.flare.server.modules
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun testTranslation() = testApplication {
        application {
            val aiService = TestAiService(
                response = "你好，世界",
            )
            val config = MapApplicationConfig(
                listOf(

                )
            )
            val context = ServerContext(
                aiService = aiService,
                config = config,
            )
            modules(context)
        }
        val client = createClient {
            install(Resources)
            install(ContentNegotiation) {
                json()
            }
        }
        client.post(Api.V1.Translate()) {
            contentType(io.ktor.http.ContentType.Application.Json)
            setBody(Api.V1.Translate.Request(text = "Hello world", targetLanguage = "zh_CN"))
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(
                expected = "你好，世界",
                actual = body<Api.V1.Translate.Response>().result,
            )
        }
    }

    @Test
    fun testTLDR() = testApplication {
        application {
            val config = MapApplicationConfig(
                listOf(

                )
            )
            val context = ServerContext(
                aiService = TestAiService("Hello world"),
                config = config,
            )
            modules(context)
        }
        val client = createClient {
            install(Resources)
            install(ContentNegotiation) {
                json()
            }
        }
        client.post(Api.V1.Tldr()) {
            contentType(io.ktor.http.ContentType.Application.Json)
            setBody(
                Api.V1.Tldr.Request(
                    text = "Hello world",
                    targetLanguage = "zh_CN"
                )
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(
                expected = "Hello world",
                actual = body<Api.V1.Tldr.Response>().result,
            )
        }
    }

}
