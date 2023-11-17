package dev.dimension.flare.data.network.misskey

import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.misskey.api.AccountApi
import dev.dimension.flare.data.network.misskey.api.DriveApi
import dev.dimension.flare.data.network.misskey.api.FollowingApi
import dev.dimension.flare.data.network.misskey.api.MetaApi
import dev.dimension.flare.data.network.misskey.api.NotesApi
import dev.dimension.flare.data.network.misskey.api.ReactionsApi
import dev.dimension.flare.data.network.misskey.api.UsersApi
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

class MisskeyService(
    private val baseUrl: String,
    private val token: String,
) : UsersApi by config(baseUrl, token).create(),
    MetaApi by config(baseUrl, token).create(),
    NotesApi by config(baseUrl, token).create(),
    AccountApi by config(baseUrl, token).create(),
    DriveApi by config(baseUrl, token).create(),
    ReactionsApi by config(baseUrl, token).create(),
    FollowingApi by config(baseUrl, token).create() {
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
        ).body()
    }
}
