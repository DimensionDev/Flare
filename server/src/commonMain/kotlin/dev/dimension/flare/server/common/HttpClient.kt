package dev.dimension.flare.server.common

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Duration.Companion.minutes


internal fun ktorClient(
    config: HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) {
            json(JSON)
        }
    },
) = HttpClient {
    config.invoke(this)
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) {
                Log.d("KtorClient", message)
            }
        }
        level = LogLevel.ALL
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 2.minutes.inWholeMilliseconds
        requestTimeoutMillis = 2.minutes.inWholeMilliseconds
        socketTimeoutMillis = 2.minutes.inWholeMilliseconds
    }
}
