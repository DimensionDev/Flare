package dev.dimension.flare.data.network.misskey

import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.misskey.api.AccountApi
import dev.dimension.flare.data.network.misskey.api.DriveApi
import dev.dimension.flare.data.network.misskey.api.FollowingApi
import dev.dimension.flare.data.network.misskey.api.HashtagsApi
import dev.dimension.flare.data.network.misskey.api.ListsApi
import dev.dimension.flare.data.network.misskey.api.MetaApi
import dev.dimension.flare.data.network.misskey.api.NotesApi
import dev.dimension.flare.data.network.misskey.api.ReactionsApi
import dev.dimension.flare.data.network.misskey.api.UsersApi
import dev.dimension.flare.data.network.misskey.api.createAccountApi
import dev.dimension.flare.data.network.misskey.api.createDriveApi
import dev.dimension.flare.data.network.misskey.api.createFollowingApi
import dev.dimension.flare.data.network.misskey.api.createHashtagsApi
import dev.dimension.flare.data.network.misskey.api.createListsApi
import dev.dimension.flare.data.network.misskey.api.createMetaApi
import dev.dimension.flare.data.network.misskey.api.createNotesApi
import dev.dimension.flare.data.network.misskey.api.createReactionsApi
import dev.dimension.flare.data.network.misskey.api.createUsersApi
import dev.dimension.flare.data.network.misskey.api.model.DriveFile
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength

private fun config(
    baseUrl: String,
    accessToken: String,
) = ktorfit(
    baseUrl = baseUrl,
    config = {
        install(MisskeyAuthorizationPlugin) {
            token = accessToken
        }
        install(DefaultRequest) {
            if (contentLength() != 0L) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
        }
    },
)

internal class MisskeyService(
    private val baseUrl: String,
    private val token: String,
) : UsersApi by config(baseUrl, token).createUsersApi(),
    MetaApi by config(baseUrl, token).createMetaApi(),
    NotesApi by config(baseUrl, token).createNotesApi(),
    AccountApi by config(baseUrl, token).createAccountApi(),
    DriveApi by config(baseUrl, token).createDriveApi(),
    ReactionsApi by config(baseUrl, token).createReactionsApi(),
    FollowingApi by config(baseUrl, token).createFollowingApi(),
    HashtagsApi by config(baseUrl, token).createHashtagsApi(),
    ListsApi by config(baseUrl, token).createListsApi() {
    suspend fun upload(
        data: ByteArray,
        name: String,
        sensitive: Boolean = false,
    ): DriveFile? {
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
                    append("i", token)
                },
            )
        return driveFilesCreate(
            multipart,
        )
    }
}
