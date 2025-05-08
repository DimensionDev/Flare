package dev.dimension.flare

import dev.dimension.flare.server.V1
import dev.dimension.flare.server.modules
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        application {
            modules()
        }
        val client = createClient {
            install(Resources)
            install(ContentNegotiation) {
                json()
            }
        }
        client.post(V1.Translate(text = "Hello World", targetLanguage = "en_US")).apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(
                expected = "Hello World",
                actual = bodyAsText(),
            )
        }
    }

}
