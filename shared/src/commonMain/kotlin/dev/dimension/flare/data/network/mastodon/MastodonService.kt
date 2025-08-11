package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.mastodon.api.AccountResources
import dev.dimension.flare.data.network.mastodon.api.FriendshipResources
import dev.dimension.flare.data.network.mastodon.api.ListsResources
import dev.dimension.flare.data.network.mastodon.api.LookupResources
import dev.dimension.flare.data.network.mastodon.api.MastodonResources
import dev.dimension.flare.data.network.mastodon.api.SearchResources
import dev.dimension.flare.data.network.mastodon.api.StatusResources
import dev.dimension.flare.data.network.mastodon.api.TimelineResources
import dev.dimension.flare.data.network.mastodon.api.TrendsResources
import dev.dimension.flare.data.network.mastodon.api.createAccountResources
import dev.dimension.flare.data.network.mastodon.api.createFriendshipResources
import dev.dimension.flare.data.network.mastodon.api.createListsResources
import dev.dimension.flare.data.network.mastodon.api.createLookupResources
import dev.dimension.flare.data.network.mastodon.api.createMastodonResources
import dev.dimension.flare.data.network.mastodon.api.createSearchResources
import dev.dimension.flare.data.network.mastodon.api.createStatusResources
import dev.dimension.flare.data.network.mastodon.api.createTimelineResources
import dev.dimension.flare.data.network.mastodon.api.createTrendsResources
import dev.dimension.flare.data.network.mastodon.api.model.UploadResponse
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

private class MastodonHeaderConfig {
    var accessTokenFlow: Flow<String>? = null
}

private val MastodonHeaderPlugin =
    createClientPlugin("MastodonHeaderPlugin", ::MastodonHeaderConfig) {
        val accessTokenFlow = pluginConfig.accessTokenFlow
        onRequest { request, _ ->
            accessTokenFlow?.let { flow ->
                val accessToken = flow.firstOrNull()
                if (accessToken != null) {
                    request.headers.append(
                        HttpHeaders.Authorization,
                        "Bearer $accessToken",
                    )
                }
            }
        }
    }

private fun config(
    baseUrl: String,
    accessTokenFlow: Flow<String>,
) = ktorfit(baseUrl) {
    expectSuccess = true
    install(MastodonHeaderPlugin) {
        this.accessTokenFlow = accessTokenFlow
    }
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

internal class MastodonService(
    baseUrl: String,
    accessTokenFlow: Flow<String>,
) : TimelineResources by config(baseUrl, accessTokenFlow).createTimelineResources(),
    LookupResources by config(baseUrl, accessTokenFlow).createLookupResources(),
    FriendshipResources by config(baseUrl, accessTokenFlow).createFriendshipResources(),
    AccountResources by config(baseUrl, accessTokenFlow).createAccountResources(),
    SearchResources by config(baseUrl, accessTokenFlow).createSearchResources(),
    StatusResources by config(baseUrl, accessTokenFlow).createStatusResources(),
    ListsResources by config(baseUrl, accessTokenFlow).createListsResources(),
    TrendsResources by config(baseUrl, accessTokenFlow).createTrendsResources(),
    MastodonResources by config(baseUrl, accessTokenFlow).createMastodonResources() {
    suspend fun upload(
        data: ByteArray,
        name: String,
    ): UploadResponse {
        val multipart =
            MultiPartFormDataContent(
                formData {
                    append(
                        "file",
                        data,
                        Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=$name")
                        },
                    )
                },
            )
        return upload(multipart)
    }
}
