package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.network.authorization.BearerAuthorization
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
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

private fun config(
    baseUrl: String,
    accessToken: String,
) = ktorfit(baseUrl, BearerAuthorization(accessToken)) {
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

internal class MastodonService(
    private val baseUrl: String,
    private val accessToken: String,
) : TimelineResources by config(baseUrl, accessToken).createTimelineResources(),
    LookupResources by config(baseUrl, accessToken).createLookupResources(),
    FriendshipResources by config(baseUrl, accessToken).createFriendshipResources(),
    AccountResources by config(baseUrl, accessToken).createAccountResources(),
    SearchResources by config(baseUrl, accessToken).createSearchResources(),
    StatusResources by config(baseUrl, accessToken).createStatusResources(),
    ListsResources by config(baseUrl, accessToken).createListsResources(),
    TrendsResources by config(baseUrl, accessToken).createTrendsResources(),
    MastodonResources by config(baseUrl, accessToken).createMastodonResources() {
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
