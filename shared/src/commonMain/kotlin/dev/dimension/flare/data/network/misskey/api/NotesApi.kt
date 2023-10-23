package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.ChannelsTimelineRequest
import dev.dimension.flare.data.network.misskey.api.model.IPinRequest
import dev.dimension.flare.data.network.misskey.api.model.MyAppsRequest
import dev.dimension.flare.data.network.misskey.api.model.Note
import dev.dimension.flare.data.network.misskey.api.model.NoteReaction
import dev.dimension.flare.data.network.misskey.api.model.NotesChildrenRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesConversationRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesCreate200Response
import dev.dimension.flare.data.network.misskey.api.model.NotesCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesFeaturedRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesGlobalTimelineRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesHybridTimelineRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesLocalTimelineRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesMentionsRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesPollsVoteRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesReactionsRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesRepliesRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesSearchByTagRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesSearchRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesState200Response
import dev.dimension.flare.data.network.misskey.api.model.NotesTranslateRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesUserListTimelineRequest

interface NotesApi {
    /**
     * channels/timeline
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param channelsTimelineRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("channels/timeline")
    suspend fun channelsTimeline(@Body channelsTimelineRequest: ChannelsTimelineRequest): Response<kotlin.collections.List<Note>>

    /**
     * notes
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notesRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("notes")
    suspend fun notes(@Body notesRequest: NotesRequest): Response<kotlin.collections.List<Note>>

    /**
     * notes/children
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notesChildrenRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("notes/children")
    suspend fun notesChildren(@Body notesChildrenRequest: NotesChildrenRequest): Response<kotlin.collections.List<Note>>

    /**
     * notes/conversation
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notesConversationRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("notes/conversation")
    suspend fun notesConversation(@Body notesConversationRequest: NotesConversationRequest): Response<kotlin.collections.List<Note>>

    /**
     * notes/create
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:notes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param notesCreateRequest * @return [NotesCreate200Response]
     */
    @POST("notes/create")
    suspend fun notesCreate(@Body notesCreateRequest: NotesCreateRequest): Response<NotesCreate200Response>

    /**
     * notes/delete
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:notes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param ipinRequest * @return [Unit]
     */
    @POST("notes/delete")
    suspend fun notesDelete(@Body ipinRequest: IPinRequest): Response<Unit>

    /**
     * notes/favorites/create
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:favorites*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param ipinRequest * @return [Unit]
     */
    @POST("notes/favorites/create")
    suspend fun notesFavoritesCreate(@Body ipinRequest: IPinRequest): Response<Unit>

    /**
     * notes/favorites/delete
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:favorites*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param ipinRequest * @return [Unit]
     */
    @POST("notes/favorites/delete")
    suspend fun notesFavoritesDelete(@Body ipinRequest: IPinRequest): Response<Unit>

    /**
     * notes/featured
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notesFeaturedRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("notes/featured")
    suspend fun notesFeatured(@Body notesFeaturedRequest: NotesFeaturedRequest): Response<kotlin.collections.List<Note>>

    /**
     * notes/global-timeline
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notesGlobalTimelineRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("notes/global-timeline")
    suspend fun notesGlobalTimeline(@Body notesGlobalTimelineRequest: NotesGlobalTimelineRequest): Response<kotlin.collections.List<Note>>

    /**
     * notes/hybrid-timeline
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notesHybridTimelineRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("notes/hybrid-timeline")
    suspend fun notesHybridTimeline(@Body notesHybridTimelineRequest: NotesHybridTimelineRequest): Response<kotlin.collections.List<Note>>

    /**
     * notes/local-timeline
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notesLocalTimelineRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("notes/local-timeline")
    suspend fun notesLocalTimeline(@Body notesLocalTimelineRequest: NotesLocalTimelineRequest): Response<kotlin.collections.List<Note>>

    /**
     * notes/mentions
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notesMentionsRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("notes/mentions")
    suspend fun notesMentions(@Body notesMentionsRequest: NotesMentionsRequest): Response<kotlin.collections.List<Note>>

    /**
     * notes/polls/recommendation
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param myAppsRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("notes/polls/recommendation")
    suspend fun notesPollsRecommendation(@Body myAppsRequest: MyAppsRequest): Response<kotlin.collections.List<Note>>

    /**
     * notes/polls/vote
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:votes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notesPollsVoteRequest * @return [Unit]
     */
    @POST("notes/polls/vote")
    suspend fun notesPollsVote(@Body notesPollsVoteRequest: NotesPollsVoteRequest): Response<Unit>

    /**
     * notes/reactions
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notesReactionsRequest * @return [kotlin.collections.List<NoteReaction>]
     */
    @POST("notes/reactions")
    suspend fun notesReactions(@Body notesReactionsRequest: NotesReactionsRequest): Response<kotlin.collections.List<NoteReaction>>

    /**
     * notes/renotes
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notesChildrenRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("notes/renotes")
    suspend fun notesRenotes(@Body notesChildrenRequest: NotesChildrenRequest): Response<kotlin.collections.List<Note>>

    /**
     * notes/replies
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notesRepliesRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("notes/replies")
    suspend fun notesReplies(@Body notesRepliesRequest: NotesRepliesRequest): Response<kotlin.collections.List<Note>>

    /**
     * notes/search
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notesSearchRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("notes/search")
    suspend fun notesSearch(@Body notesSearchRequest: NotesSearchRequest): Response<kotlin.collections.List<Note>>

    /**
     * notes/search-by-tag
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notesSearchByTagRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("notes/search-by-tag")
    suspend fun notesSearchByTag(@Body notesSearchByTagRequest: NotesSearchByTagRequest): Response<kotlin.collections.List<Note>>

    /**
     * notes/show
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param ipinRequest * @return [Note]
     */
    @POST("notes/show")
    suspend fun notesShow(@Body ipinRequest: IPinRequest): Response<Note>

    /**
     * notes/state
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param ipinRequest * @return [NotesState200Response]
     */
    @POST("notes/state")
    suspend fun notesState(@Body ipinRequest: IPinRequest): Response<NotesState200Response>

    /**
     * notes/thread-muting/create
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param ipinRequest * @return [Unit]
     */
    @POST("notes/thread-muting/create")
    suspend fun notesThreadMutingCreate(@Body ipinRequest: IPinRequest): Response<Unit>

    /**
     * notes/thread-muting/delete
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param ipinRequest * @return [Unit]
     */
    @POST("notes/thread-muting/delete")
    suspend fun notesThreadMutingDelete(@Body ipinRequest: IPinRequest): Response<Unit>

    /**
     * notes/timeline
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notesHybridTimelineRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("notes/timeline")
    suspend fun notesTimeline(@Body notesHybridTimelineRequest: NotesHybridTimelineRequest): Response<kotlin.collections.List<Note>>

    /**
     * notes/translate
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notesTranslateRequest * @return [kotlin.Any]
     */
    @POST("notes/translate")
    suspend fun notesTranslate(@Body notesTranslateRequest: NotesTranslateRequest): Response<kotlin.Any>

    /**
     * notes/unrenote
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:notes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param ipinRequest * @return [Unit]
     */
    @POST("notes/unrenote")
    suspend fun notesUnrenote(@Body ipinRequest: IPinRequest): Response<Unit>

    /**
     * notes/user-list-timeline
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notesUserListTimelineRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("notes/user-list-timeline")
    suspend fun notesUserListTimeline(@Body notesUserListTimelineRequest: NotesUserListTimelineRequest): Response<kotlin.collections.List<Note>>

    /**
     * promo/read
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param ipinRequest * @return [Unit]
     */
    @POST("promo/read")
    suspend fun promoRead(@Body ipinRequest: IPinRequest): Response<Unit>
}
