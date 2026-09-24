package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.common.UploadMedia
import dev.dimension.flare.data.network.appendMedia
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.mastodon.api.AccountResources
import dev.dimension.flare.data.network.mastodon.api.FriendshipResources
import dev.dimension.flare.data.network.mastodon.api.InstanceResources
import dev.dimension.flare.data.network.mastodon.api.ListsResources
import dev.dimension.flare.data.network.mastodon.api.LookupResources
import dev.dimension.flare.data.network.mastodon.api.MastodonResources
import dev.dimension.flare.data.network.mastodon.api.SearchResources
import dev.dimension.flare.data.network.mastodon.api.StatusResources
import dev.dimension.flare.data.network.mastodon.api.TimelineResources
import dev.dimension.flare.data.network.mastodon.api.TrendsResources
import dev.dimension.flare.data.network.mastodon.api.createAccountResources
import dev.dimension.flare.data.network.mastodon.api.createFriendshipResources
import dev.dimension.flare.data.network.mastodon.api.createInstanceResources
import dev.dimension.flare.data.network.mastodon.api.createListsResources
import dev.dimension.flare.data.network.mastodon.api.createLookupResources
import dev.dimension.flare.data.network.mastodon.api.createMastodonResources
import dev.dimension.flare.data.network.mastodon.api.createSearchResources
import dev.dimension.flare.data.network.mastodon.api.createStatusResources
import dev.dimension.flare.data.network.mastodon.api.createTimelineResources
import dev.dimension.flare.data.network.mastodon.api.createTrendsResources
import dev.dimension.flare.data.network.mastodon.api.model.MastodonPagingConverterFactory
import dev.dimension.flare.data.network.mastodon.api.model.MediaAttachments
import dev.dimension.flare.data.network.mastodon.api.model.UploadResponse
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

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
) = ktorfit(
    baseUrl = baseUrl,
    extraConverterFactories = listOf(MastodonPagingConverterFactory()),
) {
    expectSuccess = true
    install(MastodonHeaderPlugin) {
        this.accessTokenFlow = accessTokenFlow
    }
    HttpResponseValidator {
        handleResponseExceptionWithRequest { exception, _ ->
            if (exception is ResponseException) {
                exception.response
                    .bodyAsText()
                    .toMastodonExceptionOrNull()
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
    InstanceResources by config(baseUrl, accessTokenFlow).createInstanceResources(),
    MastodonResources by config(baseUrl, accessTokenFlow).createMastodonResources() {
    suspend fun mediaLimits(): MediaAttachments? =
        withTimeoutOrNull(5_000) {
            suspend fun fetch(v2: Boolean): MediaAttachments? =
                try {
                    if (v2) instance().configuration?.mediaAttachments else instanceV1().configuration?.mediaAttachments
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null // Older Mastodon-compatible servers may not expose media limits.
                }
            fetch(true) ?: fetch(false)
        }

    suspend fun upload(
        data: UploadMedia,
        description: String?,
    ): UploadResponse =
        coroutineScope {
            val multipart =
                MultiPartFormDataContent(
                    formData {
                        appendMedia("file", data, this@coroutineScope)
                        if (description != null) {
                            append("description", description)
                        }
                    },
                )
            upload(multipart)
        }
}
