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
import dev.dimension.flare.model.MicroBlogKey
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText

private fun config(baseUrl: String) =
    ktorfit(baseUrl) {
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
    }

private const val BASEURL = "https://mastodon.social/"

internal object GuestMastodonService :
    TrendsResources by config(BASEURL).createTrendsResources(),
    LookupResources by config(BASEURL).createLookupResources(),
    TimelineResources by config(BASEURL).createTimelineResources(),
    AccountResources by config(BASEURL).createAccountResources(),
    SearchResources by config(BASEURL).createSearchResources() {
    const val HOST = "mastodon.social"

    val GuestKey = MicroBlogKey("guest", HOST)
}
