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
                exception.response.bodyAsText().decodeJson<MastodonException>()
                    .takeIf { it.error != null }?.let {
                        throw it
                    }
            }
        }
    }
}

class MastodonService(
    private val baseUrl: String,
    private val accessToken: String,
) : TimelineResources by config(baseUrl, accessToken).create(),
    LookupResources by config(baseUrl, accessToken).create(),
    FriendshipResources by config(baseUrl, accessToken).create(),
    AccountResources by config(baseUrl, accessToken).create(),
    SearchResources by config(baseUrl, accessToken).create(),
    StatusResources by config(baseUrl, accessToken).create(),
    ListsResources by config(baseUrl, accessToken).create(),
    TrendsResources by config(baseUrl, accessToken).create(),
    MastodonResources by config(baseUrl, accessToken).create() {
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
