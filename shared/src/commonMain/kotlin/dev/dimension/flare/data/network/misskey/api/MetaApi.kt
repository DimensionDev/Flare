package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.AdminInviteCreate200ResponseInner
import dev.dimension.flare.data.network.misskey.api.model.AdminMeta200Response
import dev.dimension.flare.data.network.misskey.api.model.Announcements200ResponseInner
import dev.dimension.flare.data.network.misskey.api.model.AnnouncementsRequest
import dev.dimension.flare.data.network.misskey.api.model.BlockingListRequest
import dev.dimension.flare.data.network.misskey.api.model.EmojiDetailed
import dev.dimension.flare.data.network.misskey.api.model.EmojiRequest
import dev.dimension.flare.data.network.misskey.api.model.Emojis200Response
import dev.dimension.flare.data.network.misskey.api.model.EndpointRequest
import dev.dimension.flare.data.network.misskey.api.model.FetchRssRequest
import dev.dimension.flare.data.network.misskey.api.model.InviteDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.InviteLimit200Response
import dev.dimension.flare.data.network.misskey.api.model.Meta200Response
import dev.dimension.flare.data.network.misskey.api.model.MetaRequest
import dev.dimension.flare.data.network.misskey.api.model.Ping200Response
import dev.dimension.flare.data.network.misskey.api.model.Stats200Response

internal interface MetaApi {
    /**
     * admin/meta
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [AdminMeta200Response]
     */
    @POST("admin/meta")
    suspend fun adminMeta(
        @Body body: kotlin.Any,
    ): AdminMeta200Response

    /**
     * announcements
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param announcementsRequest * @return [kotlin.collections.List<Announcements200ResponseInner>]
     */
    @POST("announcements")
    suspend fun announcements(
        @Body announcementsRequest: AnnouncementsRequest,
    ): kotlin.collections.List<Announcements200ResponseInner>

    /**
     * emoji
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param emojiRequest * @return [EmojiDetailed]
     */
    @POST("emoji")
    suspend fun emoji(
        @Body emojiRequest: EmojiRequest,
    ): EmojiDetailed

    /**
     * emojis
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @return [Emojis200Response]
     */
    @GET("emojis")
    suspend fun emojis(): Emojis200Response

    /**
     * endpoint
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param endpointRequest * @return [Unit]
     */
    @POST("endpoint")
    suspend fun endpoint(
        @Body endpointRequest: EndpointRequest,
    ): Unit

    /**
     * endpoints
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [kotlin.collections.List<kotlin.String>]
     */
    @POST("endpoints")
    suspend fun endpoints(
        @Body body: kotlin.Any,
    ): kotlin.collections.List<kotlin.String>

    /**
     * fetch-rss
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param fetchRssRequest * @return [Unit]
     */
    @POST("fetch-rss")
    suspend fun fetchRss(
        @Body fetchRssRequest: FetchRssRequest,
    ): Unit

    /**
     * get-online-users-count
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [Unit]
     */
    @POST("get-online-users-count")
    suspend fun getOnlineUsersCount(
        @Body body: kotlin.Any,
    ): Unit

    /**
     * invite/create
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [AdminInviteCreate200ResponseInner]
     */
    @POST("invite/create")
    suspend fun inviteCreate(
        @Body body: kotlin.Any,
    ): AdminInviteCreate200ResponseInner

    /**
     * invite/delete
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param inviteDeleteRequest * @return [Unit]
     */
    @POST("invite/delete")
    suspend fun inviteDelete(
        @Body inviteDeleteRequest: InviteDeleteRequest,
    ): Unit

    /**
     * invite/limit
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [InviteLimit200Response]
     */
    @POST("invite/limit")
    suspend fun inviteLimit(
        @Body body: kotlin.Any,
    ): InviteLimit200Response

    /**
     * invite/list
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param blockingListRequest * @return [kotlin.collections.List<kotlin.Any>]
     */
    @POST("invite/list")
    suspend fun inviteList(
        @Body blockingListRequest: BlockingListRequest,
    ): kotlin.collections.List<kotlin.Any>

    /**
     * meta
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param metaRequest * @return [Meta200Response]
     */
    @POST("meta")
    suspend fun meta(
        @Body metaRequest: MetaRequest,
    ): Meta200Response

    /**
     * ping
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [Ping200Response]
     */
    @POST("ping")
    suspend fun ping(
        @Body body: kotlin.Any,
    ): Ping200Response

    /**
     * server-info
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [Unit]
     */
    @POST("server-info")
    suspend fun serverInfo(
        @Body body: kotlin.Any,
    ): Unit

    /**
     * stats
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [Stats200Response]
     */
    @POST("stats")
    suspend fun stats(
        @Body body: kotlin.Any,
    ): Stats200Response
}
