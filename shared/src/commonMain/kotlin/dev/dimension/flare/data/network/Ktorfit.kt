package dev.dimension.flare.data.network

import de.jensklingenberg.ktorfit.converter.CallConverterFactory
import de.jensklingenberg.ktorfit.converter.FlowConverterFactory
import de.jensklingenberg.ktorfit.converter.ResponseConverterFactory
import dev.dimension.flare.common.BuildConfig
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.mastodon.api.model.MastodonPagingConverterFactory
import dev.dimension.flare.data.repository.DebugRepository
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
    json: Json = JSON,
    config: HttpClientConfig<*>.() -> Unit = {},
) = de.jensklingenberg.ktorfit.ktorfit {
    baseUrl(baseUrl)
    httpClient(
        ktorClient {
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
        MastodonPagingConverterFactory(),
    )
}

internal fun ktorClient(
    config: HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) {
            json(JSON)
        }
    },
) = HttpClient(httpClientEngine) {
    config.invoke(this)
    install(Logging) {
        logger = NapierLogger
        level =
            if (BuildConfig.debug) {
                LogLevel.ALL
            } else {
                LogLevel.BODY
            }
    }
}

internal expect val httpClientEngine: HttpClientEngine

private data object NapierLogger : io.ktor.client.plugins.logging.Logger {
    override fun log(message: String) {
        if (BuildConfig.debug) {
            println(message)
        }
        DebugRepository.log(message)
    }
}
