package dev.dimension.flare.data.network.misskey

import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.misskey.api.AccountApi
import dev.dimension.flare.data.network.misskey.api.DriveApi
import dev.dimension.flare.data.network.misskey.api.MetaApi
import dev.dimension.flare.data.network.misskey.api.NotesApi
import dev.dimension.flare.data.network.misskey.api.UsersApi
import dev.dimension.flare.data.network.misskey.api.model.DriveFile
import dev.dimension.flare.data.network.misskey.api.model.EmojiSimple
import dev.dimension.flare.data.network.misskey.api.model.INotificationsRequest
import dev.dimension.flare.data.network.misskey.api.model.IPinRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesChildrenRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesHybridTimelineRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesMentionsRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersNotesRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersShowRequest
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import java.io.InputStream

class MisskeyService(
    private val baseUrl: String,
    private val token: String,
) {
    private val usersApi: UsersApi by lazy {
        ktorfit(
            baseUrl = baseUrl,
            config = {
                install(MisskeyAuthorizationPlugin) {
                    token = this@MisskeyService.token
                }
                install(DefaultRequest) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                }
            },
        ).create()
    }
    private val metaApi: MetaApi by lazy {
        ktorfit(
            baseUrl = baseUrl,
            config = {
                install(DefaultRequest) {
//                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                }
            },
        ).create()
    }
    private val notesApi: NotesApi by lazy {
        ktorfit(
            baseUrl = baseUrl,
            config = {
                install(MisskeyAuthorizationPlugin) {
                    token = this@MisskeyService.token
                }
                install(DefaultRequest) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                }
            },
        ).create()
    }

    private val accountApi: AccountApi by lazy {
        ktorfit(
            baseUrl = baseUrl,
            config = {
                install(MisskeyAuthorizationPlugin) {
                    token = this@MisskeyService.token
                }
                install(DefaultRequest) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                }
            },
        ).create()
    }

    private val driveApi: DriveApi by lazy {
        ktorfit(
            baseUrl = baseUrl,
            config = {
                install(MisskeyAuthorizationPlugin) {
                    token = this@MisskeyService.token
                }
                install(DefaultRequest) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                }
            },
        ).create()
    }

    suspend fun findUserById(
        userId: String,
    ) = usersApi.usersShow(UsersShowRequest(userId = userId)).body()

    suspend fun emojis(): List<EmojiSimple> {
        return metaApi.emojis().body()?.emojis ?: emptyList()
    }

    suspend fun homeTimeline(
        count: Int,
        since_id: String? = null,
        until_id: String? = null,
    ) = notesApi.notesTimeline(
        NotesHybridTimelineRequest(
            untilId = until_id,
            sinceId = since_id,
            limit = count,
        ),
    ).body()

    suspend fun userTimeline(
        userId: String,
        count: Int,
        since_id: String? = null,
        until_id: String? = null,
    ) = usersApi.usersNotes(
        UsersNotesRequest(
            userId = userId,
            untilId = until_id,
            sinceId = since_id,
            limit = count,
        ),
    ).body()

    suspend fun notifications(
        count: Int,
        since_id: String? = null,
        until_id: String? = null,
    ) = accountApi.iNotifications(
        INotificationsRequest(
            untilId = until_id,
            sinceId = since_id,
            limit = count,
        ),
    ).body()

    suspend fun mentionTimeline(
        count: Int,
        since_id: String? = null,
        until_id: String? = null,
    ) = notesApi.notesMentions(
        NotesMentionsRequest(
            untilId = until_id,
            sinceId = since_id,
            limit = count,
        ),
    ).body()

    suspend fun lookupStatus(
        noteId: String,
    ) = notesApi.notesShow(IPinRequest(noteId = noteId)).body()

    suspend fun childrenTimeline(
        noteId: String,
        count: Int,
        since_id: String? = null,
        until_id: String? = null,
    ) = notesApi.notesChildren(
        NotesChildrenRequest(
            noteId = noteId,
            untilId = until_id,
            sinceId = since_id,
            limit = count,
        ),
    ).body()

    suspend fun findUserByName(name: String, host: String) =
        usersApi.usersShow(UsersShowRequest(username = name, host = host)).body()

    suspend fun notesCreate(notesCreateRequest: NotesCreateRequest) =
        notesApi.notesCreate(notesCreateRequest).body()

    suspend fun upload(
        channel: InputStream,
        name: String,
        sensitive: Boolean = false,
    ): DriveFile? {
        val data = channel.readBytes()
        val multipart = MultiPartFormDataContent(
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
        return driveApi.driveFilesCreate(
            multipart,
        ).body()
    }

    suspend fun renote(id: String) {
        notesApi.notesRenotes(NotesChildrenRequest(noteId = id))
    }
}
