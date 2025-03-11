package dev.dimension.flare.data.network

import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import de.jensklingenberg.ktorfit.converter.CallConverterFactory
import de.jensklingenberg.ktorfit.converter.FlowConverterFactory
import de.jensklingenberg.ktorfit.converter.ResponseConverterFactory
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.authorization.Authorization
import dev.dimension.flare.data.network.authorization.AuthorizationPlugin
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal fun ktorfit(
    baseUrl: String,
    authorization: Authorization? = null,
    json: Json = JSON,
    config: HttpClientConfig<*>.() -> Unit = {},
) = de.jensklingenberg.ktorfit.ktorfit {
    baseUrl(baseUrl)
    httpClient(
        ktorClient(authorization) {
            install(ContentNegotiation) {
                json(json)
            }
            config.invoke(this)
        },
    )
    converterFactories(
        FlowConverterFactory(),
        CallConverterFactory(),
        ResponseConverterFactory(),
    )
}

internal fun ktorClient(
    authorization: Authorization? = null,
    config: HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) {
            json(JSON)
        }
    },
) = HttpClient(httpClientEngine) {
    if (authorization != null) {
        install(AuthorizationPlugin) {
            this.authorization = authorization
        }
    }
    config.invoke(this)
    install(Logging) {
        logger = NapierLogger
        level = LogLevel.ALL
    }
}

internal expect val httpClientEngine: HttpClientEngine

private data object NapierLogger : io.ktor.client.plugins.logging.Logger {
    private val log =
        Logger(
            loggerConfigInit(platformLogWriter(co.touchlab.kermit.DefaultFormatter)),
            "HTTP Client",
        )

    override fun log(message: String) {
        log.d(message)
    }
}
