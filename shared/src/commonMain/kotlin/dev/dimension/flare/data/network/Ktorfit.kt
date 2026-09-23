package dev.dimension.flare.data.network

import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.converter.Converter
import de.jensklingenberg.ktorfit.converter.ResponseConverterFactory
import dev.dimension.flare.common.BuildConfig
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.repository.DebugRepository
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import kotlinx.serialization.json.Json
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public fun ktorfit(
    baseUrl: String,
    json: Json = JSON,
    extraConverterFactories: List<Converter.Factory> = emptyList(),
    config: HttpClientConfig<*>.() -> Unit = {},
): Ktorfit =
    de.jensklingenberg.ktorfit.ktorfit {
        baseUrl(baseUrl)
        httpClient(
            ktorClient {
                install(ContentNegotiation) {
                    nullableFallbackJson(json)
                }
                config.invoke(this)
            },
        )
        converterFactories(
            ResponseConverterFactory(),
            *extraConverterFactories.toTypedArray(),
        )
    }

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public fun ktorClient(
    config: HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) {
            nullableFallbackJson(JSON)
        }
    },
): HttpClient =
    HttpClient(httpClientEngine) {
        config.invoke(this)
        install(Logging) {
            logger = FlareLogger
            filter {
                (BuildConfig.debug || DebugRepository.isEnabled) && !it.isMediaUpload()
            }
            level =
                if (BuildConfig.debug) {
                    LogLevel.ALL
                } else {
                    LogLevel.BODY
                }
        }
    }

internal expect val httpClientEngine: HttpClientEngine

// Logging's BODY mode collects the entire outgoing stream. Keep upload payloads out of it,
// including X's base64 form chunks and the authenticated retry of a Bluesky upload.
internal fun HttpRequestBuilder.isMediaUpload(): Boolean {
    val path = url.pathSegments.joinToString("/")
    return path.endsWith("media/upload.json") ||
        path.endsWith("com.atproto.repo.uploadBlob") ||
        path.endsWith("app.bsky.video.uploadVideo") ||
        path.endsWith("api/v1/media") || path.endsWith("api/v2/media") ||
        path.endsWith("drive/files/create") || path.endsWith("upload") || path.endsWith("statuses/uploadPic")
}

internal data object FlareLogger : io.ktor.client.plugins.logging.Logger {
    override fun log(message: String) {
        if (BuildConfig.debug) {
            println(message)
        }
        DebugRepository.log(message)
    }
}
