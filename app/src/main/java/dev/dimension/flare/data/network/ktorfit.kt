package dev.dimension.flare.data.network

import de.jensklingenberg.ktorfit.converter.builtin.CallConverterFactory
import de.jensklingenberg.ktorfit.converter.builtin.FlowConverterFactory
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.authorization.Authorization
import dev.dimension.flare.data.network.authorization.AuthorizationPlugin
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json

internal fun ktorfit(
    baseUrl: String,
    authorization: Authorization? = null,
) = de.jensklingenberg.ktorfit.ktorfit {
    baseUrl(baseUrl)
    httpClient(HttpClient {
        install(ContentNegotiation) {
            json(JSON)
        }
        if (authorization != null) {
            install(AuthorizationPlugin) {
                this.authorization = authorization
            }
        }
        install(Logging) {
            logger = Logger.ANDROID
            level = LogLevel.ALL
        }
    })
    converterFactories(
        FlowConverterFactory(),
        CallConverterFactory(),
    )
}