package dev.dimension.flare

import dev.dimension.flare.server.V1
import dev.dimension.flare.server.modules
import dev.dimension.flare.server.service.ai.LocalOllamaAIService
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.post
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun testTranslation() = testApplication {
        application {
            with(LocalOllamaAIService()) {
                modules()
            }
        }
        val client = createClient {
            install(Resources)
            install(ContentNegotiation) {
                json()
            }
        }
        client.post(V1.Translate(text = "Hello world", targetLanguage = "zh_CN")).apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(
                expected = "你好，世界",
                actual = body<V1.Translate.Response>().result,
            )
        }
    }

}
