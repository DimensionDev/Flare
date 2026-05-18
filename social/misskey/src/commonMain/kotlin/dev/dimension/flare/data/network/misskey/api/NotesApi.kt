package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Header
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

public interface NotesApi {
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
    public suspend fun channelsTimeline(
        @Header("Content-Type") contentType: kotlin.String = "application/json",
        @Body channelsTimelineRequest: ChannelsTimelineRequest,
    ): kotlin.collections.List<Note>

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
    public suspend fun notes(
        @Body notesRequest: NotesRequest,
    ): kotlin.collections.List<Note>

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
    public suspend fun notesChildren(
        @Body notesChildrenRequest: NotesChildrenRequest,
    ): kotlin.collections.List<Note>

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
    public suspend fun notesConversation(
        @Body notesConversationRequest: NotesConversationRequest,
    ): kotlin.collections.List<Note>

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
    public suspend fun notesCreate(
        @Body notesCreateRequest: NotesCreateRequest,
    ): NotesCreate200Response

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
    public suspend fun notesDelete(
        @Body ipinRequest: IPinRequest,
    ): Unit

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
    public suspend fun notesFavoritesCreate(
        @Body ipinRequest: IPinRequest,
    ): Unit

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
    public suspend fun notesFavoritesDelete(
        @Body ipinRequest: IPinRequest,
    ): Unit

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
    public suspend fun notesFeatured(
        @Body notesFeaturedRequest: NotesFeaturedRequest,
    ): kotlin.collections.List<Note>

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
    public suspend fun notesGlobalTimeline(
        @Body notesGlobalTimelineRequest: NotesGlobalTimelineRequest,
    ): kotlin.collections.List<Note>

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
    public suspend fun notesHybridTimeline(
        @Body notesHybridTimelineRequest: NotesHybridTimelineRequest,
    ): kotlin.collections.List<Note>

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
    public suspend fun notesLocalTimeline(
        @Body notesLocalTimelineRequest: NotesLocalTimelineRequest,
    ): kotlin.collections.List<Note>

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
    public suspend fun notesMentions(
        @Body notesMentionsRequest: NotesMentionsRequest,
    ): kotlin.collections.List<Note>

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
    public suspend fun notesPollsRecommendation(
        @Body myAppsRequest: MyAppsRequest,
    ): kotlin.collections.List<Note>

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
    public suspend fun notesPollsVote(
        @Body notesPollsVoteRequest: NotesPollsVoteRequest,
    ): Unit

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
    public suspend fun notesReactions(
        @Body notesReactionsRequest: NotesReactionsRequest,
    ): kotlin.collections.List<NoteReaction>

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
    public suspend fun notesRenotes(
        @Body notesChildrenRequest: NotesChildrenRequest,
    ): kotlin.collections.List<Note>

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
    public suspend fun notesReplies(
        @Body notesRepliesRequest: NotesRepliesRequest,
    ): kotlin.collections.List<Note>

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
    public suspend fun notesSearch(
        @Body notesSearchRequest: NotesSearchRequest,
    ): kotlin.collections.List<Note>

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
    public suspend fun notesSearchByTag(
        @Body notesSearchByTagRequest: NotesSearchByTagRequest,
    ): kotlin.collections.List<Note>

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
    public suspend fun notesShow(
        @Body ipinRequest: IPinRequest,
    ): Note

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
    public suspend fun notesState(
        @Body ipinRequest: IPinRequest,
    ): NotesState200Response

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
    public suspend fun notesThreadMutingCreate(
        @Body ipinRequest: IPinRequest,
    ): Unit

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
    public suspend fun notesThreadMutingDelete(
        @Body ipinRequest: IPinRequest,
    ): Unit

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
    public suspend fun notesTimeline(
        @Body notesHybridTimelineRequest: NotesHybridTimelineRequest,
    ): kotlin.collections.List<Note>

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
    public suspend fun notesTranslate(
        @Body notesTranslateRequest: NotesTranslateRequest,
    ): kotlin.Any

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
    public suspend fun notesUnrenote(
        @Body ipinRequest: IPinRequest,
    ): Unit

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
    public suspend fun notesUserListTimeline(
        @Body notesUserListTimelineRequest: NotesUserListTimelineRequest,
    ): kotlin.collections.List<Note>

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
    public suspend fun promoRead(
        @Body ipinRequest: IPinRequest,
    ): Unit
}
