package dev.dimension.flare.data.network.misskey

import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.misskey.api.AccountApi
import dev.dimension.flare.data.network.misskey.api.MetaApi
import dev.dimension.flare.data.network.misskey.api.NotesApi
import dev.dimension.flare.data.network.misskey.api.UsersApi
import dev.dimension.flare.data.network.misskey.api.model.EmojiSimple
import dev.dimension.flare.data.network.misskey.api.model.INotificationsRequest
import dev.dimension.flare.data.network.misskey.api.model.IPinRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesChildrenRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesHybridTimelineRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesMentionsRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersNotesRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersShowRequest

class MisskeyService(
    private val baseUrl: String,
    private val token: String
) {
    private val usersApi: UsersApi by lazy {
        ktorfit(
            baseUrl = baseUrl,
            config = {
                install(MisskeyAuthorizationPlugin) {
                    token = this@MisskeyService.token
                }
            }
        ).create()
    }
    private val metaApi: MetaApi by lazy {
        ktorfit(
            baseUrl = baseUrl,
            config = {
                install(MisskeyAuthorizationPlugin) {
                    token = this@MisskeyService.token
                }
            }
        ).create()
    }
    private val notesApi: NotesApi by lazy {
        ktorfit(
            baseUrl = baseUrl,
            config = {
                install(MisskeyAuthorizationPlugin) {
                    token = this@MisskeyService.token
                }
            }
        ).create()
    }

    private val accountApi: AccountApi by lazy {
        ktorfit(
            baseUrl = baseUrl,
            config = {
                install(MisskeyAuthorizationPlugin) {
                    token = this@MisskeyService.token
                }
            }
        ).create()
    }

    suspend fun findUserById(
        userId: String,
    ) = usersApi.usersShow(UsersShowRequest(userId = userId)).body()

    suspend fun emojis(): List<EmojiSimple> {
        return metaApi.emojis(Any()).body()?.emojis ?: emptyList()
    }
    suspend fun homeTimeline(
        count: Int,
        since_id: String? = null,
        until_id: String? = null,
    ) = notesApi.notesTimeline(
        NotesHybridTimelineRequest(
            untilId = until_id,
            sinceId = since_id,
            limit = count
        )
    )

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
            limit = count
        )
    )

    suspend fun notifications(
        count: Int,
        since_id: String? = null,
        until_id: String? = null,
    ) = accountApi.iNotifications(
        INotificationsRequest(
            untilId = until_id,
            sinceId = since_id,
            limit = count
        )
    )

    suspend fun mentionTimeline(
        count: Int,
        since_id: String? = null,
        until_id: String? = null,
    ) = notesApi.notesMentions(
        NotesMentionsRequest(
            untilId = until_id,
            sinceId = since_id,
            limit = count
        )
    )

    suspend fun lookupStatus(
        noteId: String,
    ) = notesApi.notesShow(IPinRequest(noteId = noteId))

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
            limit = count
        )
    )

}

