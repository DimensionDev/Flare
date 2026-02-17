package dev.dimension.flare.data.network.misskey

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.misskey.api.AccountApi
import dev.dimension.flare.data.network.misskey.api.AntennasApi
import dev.dimension.flare.data.network.misskey.api.ChannelsApi
import dev.dimension.flare.data.network.misskey.api.DriveApi
import dev.dimension.flare.data.network.misskey.api.FollowingApi
import dev.dimension.flare.data.network.misskey.api.HashtagsApi
import dev.dimension.flare.data.network.misskey.api.ListsApi
import dev.dimension.flare.data.network.misskey.api.MetaApi
import dev.dimension.flare.data.network.misskey.api.NotesApi
import dev.dimension.flare.data.network.misskey.api.ReactionsApi
import dev.dimension.flare.data.network.misskey.api.UsersApi
import dev.dimension.flare.data.network.misskey.api.createAccountApi
import dev.dimension.flare.data.network.misskey.api.createAntennasApi
import dev.dimension.flare.data.network.misskey.api.createChannelsApi
import dev.dimension.flare.data.network.misskey.api.createDriveApi
import dev.dimension.flare.data.network.misskey.api.createFollowingApi
import dev.dimension.flare.data.network.misskey.api.createHashtagsApi
import dev.dimension.flare.data.network.misskey.api.createListsApi
import dev.dimension.flare.data.network.misskey.api.createMetaApi
import dev.dimension.flare.data.network.misskey.api.createNotesApi
import dev.dimension.flare.data.network.misskey.api.createReactionsApi
import dev.dimension.flare.data.network.misskey.api.createUsersApi
import dev.dimension.flare.data.network.misskey.api.model.DriveFile
import dev.dimension.flare.data.network.misskey.api.model.MisskeyException
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

private fun config(
    baseUrl: String,
    accessTokenFlow: Flow<String>?,
) = ktorfit(
    baseUrl = baseUrl,
    config = {
        if (accessTokenFlow != null) {
            install(MisskeyAuthorizationPlugin) {
                this.accessTokenFlow = accessTokenFlow
            }
        }
        install(DefaultRequest) {
            if (contentLength() != 0L) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
        }
        HttpResponseValidator {
            validateResponse {
                runCatching {
                    it
                        .bodyAsText()
                        .decodeJson<MisskeyException>()
                }.getOrNull()
                    ?.takeIf { it.error != null }
                    ?.let {
                        throw it
                    }
            }
        }
    },
)

internal class MisskeyService(
    baseUrl: String,
    private val accessTokenFlow: Flow<String>?,
) : UsersApi by config(baseUrl, accessTokenFlow).createUsersApi(),
    MetaApi by config(baseUrl, accessTokenFlow).createMetaApi(),
    NotesApi by config(baseUrl, accessTokenFlow).createNotesApi(),
    AccountApi by config(baseUrl, accessTokenFlow).createAccountApi(),
    DriveApi by config(baseUrl, accessTokenFlow).createDriveApi(),
    ReactionsApi by config(baseUrl, accessTokenFlow).createReactionsApi(),
    FollowingApi by config(baseUrl, accessTokenFlow).createFollowingApi(),
    HashtagsApi by config(baseUrl, accessTokenFlow).createHashtagsApi(),
    ListsApi by config(baseUrl, accessTokenFlow).createListsApi(),
    AntennasApi by config(baseUrl, accessTokenFlow).createAntennasApi(),
    ChannelsApi by config(baseUrl, accessTokenFlow).createChannelsApi() {
    suspend fun upload(
        data: ByteArray,
        name: String,
        sensitive: Boolean = false,
        comment: String? = null,
    ): DriveFile? {
        val token = accessTokenFlow?.firstOrNull()
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
                    append("isSensitive", sensitive)
                    if (token != null) {
                        append("i", token)
                    }
                    if (comment != null) {
                        append("comment", comment)
                    }
                },
            )
        return driveFilesCreate(
            multipart,
        )
    }
}
