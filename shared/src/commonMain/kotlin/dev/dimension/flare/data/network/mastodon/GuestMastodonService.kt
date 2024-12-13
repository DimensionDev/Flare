package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.mastodon.api.AccountResources
import dev.dimension.flare.data.network.mastodon.api.LookupResources
import dev.dimension.flare.data.network.mastodon.api.SearchResources
import dev.dimension.flare.data.network.mastodon.api.TimelineResources
import dev.dimension.flare.data.network.mastodon.api.TrendsResources
import dev.dimension.flare.data.network.mastodon.api.createAccountResources
import dev.dimension.flare.data.network.mastodon.api.createLookupResources
import dev.dimension.flare.data.network.mastodon.api.createSearchResources
import dev.dimension.flare.data.network.mastodon.api.createTimelineResources
import dev.dimension.flare.data.network.mastodon.api.createTrendsResources
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText

private fun config(
    baseUrl: String,
    locale: String,
) = ktorfit(baseUrl) {
    expectSuccess = true
    HttpResponseValidator {
        handleResponseExceptionWithRequest { exception, _ ->
            if (exception is ResponseException) {
                exception.response
                    .bodyAsText()
                    .decodeJson<MastodonException>()
                    .takeIf { it.error != null }
                    ?.let {
                        throw it
                    }
            }
        }
    }
    defaultRequest {
        headers {
            append("accept-language", locale)
        }
    }
}

internal class GuestMastodonService(
    private val baseUrl: String,
    private val locale: String,
) : TrendsResources by config(baseUrl, locale).createTrendsResources(),
    LookupResources by config(baseUrl, locale).createLookupResources(),
    TimelineResources by config(baseUrl, locale).createTimelineResources(),
    AccountResources by config(baseUrl, locale).createAccountResources(),
    SearchResources by config(baseUrl, locale).createSearchResources()
