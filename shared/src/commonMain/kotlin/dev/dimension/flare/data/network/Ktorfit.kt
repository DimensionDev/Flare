package dev.dimension.flare.data.network

import de.jensklingenberg.ktorfit.converter.CallConverterFactory
import de.jensklingenberg.ktorfit.converter.FlowConverterFactory
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.authorization.Authorization
import dev.dimension.flare.data.network.authorization.AuthorizationPlugin
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json

internal fun ktorfit(
    baseUrl: String,
    authorization: Authorization? = null,
    config: HttpClientConfig<*>.() -> Unit = {},
) = de.jensklingenberg.ktorfit.ktorfit {
    baseUrl(baseUrl)
    httpClient(
        ktorClient(authorization) {
            install(ContentNegotiation) {
                json(JSON)
            }
            config.invoke(this)
        },
    )
    converterFactories(
        FlowConverterFactory(),
        CallConverterFactory(),
    )
}

internal fun ktorClient(
    authorization: Authorization? = null,
    config: HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) {
            json(JSON)
        }
    },
) = HttpClient {
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

private data object NapierLogger : io.ktor.client.plugins.logging.Logger {
    init {
        Napier.base(DebugAntilog())
    }

    override fun log(message: String) {
        Napier.v(message, null, "HTTP Client")
    }
}
