package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.Channel
import dev.dimension.flare.data.network.misskey.api.model.ChannelsCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.ChannelsFeaturedRequest
import dev.dimension.flare.data.network.misskey.api.model.ChannelsFollowRequest
import dev.dimension.flare.data.network.misskey.api.model.ChannelsFollowedRequest
import dev.dimension.flare.data.network.misskey.api.model.ChannelsSearchRequest
import dev.dimension.flare.data.network.misskey.api.model.ChannelsUpdateRequest

internal interface ChannelsApi {
    /**
     * channels/create
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:channels*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param channelsCreateRequest * @return [Channel]
     */
    @POST("channels/create")
    suspend fun channelsCreate(
        @Body channelsCreateRequest: ChannelsCreateRequest,
        @Header("Content-Type") contentType: kotlin.String = "application/json",
    ): Channel

    /**
     * channels/favorite
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:channels*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param channelsFollowRequest * @return [Unit]
     */
    @POST("channels/favorite")
    suspend fun channelsFavorite(
        @Body channelsFollowRequest: ChannelsFollowRequest,
        @Header("Content-Type") contentType: kotlin.String = "application/json",
    ): Unit

    /**
     * channels/featured
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param request * @return [kotlin.collections.List<Channel>]
     */
    @POST("channels/featured")
    suspend fun channelsFeatured(
        @Body request: ChannelsFeaturedRequest,
        @Header("Content-Type") contentType: kotlin.String = "application/json",
    ): kotlin.collections.List<Channel>

    /**
     * channels/follow
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:channels*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param channelsFollowRequest * @return [Unit]
     */
    @POST("channels/follow")
    suspend fun channelsFollow(
        @Body channelsFollowRequest: ChannelsFollowRequest,
        @Header("Content-Type") contentType: kotlin.String = "application/json",
    ): Unit

    /**
     * channels/followed
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:channels*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param channelsFollowedRequest * @return [kotlin.collections.List<Channel>]
     */
    @POST("channels/followed")
    suspend fun channelsFollowed(
        @Body channelsFollowedRequest: ChannelsFollowedRequest,
        @Header("Content-Type") contentType: kotlin.String = "application/json",
    ): kotlin.collections.List<Channel>

    /**
     * channels/my-favorites
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:channels*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [kotlin.collections.List<Channel>]
     */
    @POST("channels/my-favorites")
    suspend fun channelsMyFavorites(
        @Body channelsFollowedRequest: ChannelsFollowedRequest,
        @Header("Content-Type") contentType: kotlin.String = "application/json",
    ): kotlin.collections.List<Channel>

    /**
     * channels/owned
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:channels*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param channelsFollowedRequest * @return [kotlin.collections.List<Channel>]
     */
    @POST("channels/owned")
    suspend fun channelsOwned(
        @Body channelsFollowedRequest: ChannelsFollowedRequest,
        @Header("Content-Type") contentType: kotlin.String = "application/json",
    ): kotlin.collections.List<Channel>

    /**
     * channels/search
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param channelsSearchRequest * @return [kotlin.collections.List<Channel>]
     */
    @POST("channels/search")
    suspend fun channelsSearch(
        @Body channelsSearchRequest: ChannelsSearchRequest,
        @Header("Content-Type") contentType: kotlin.String = "application/json",
    ): kotlin.collections.List<Channel>

    /**
     * channels/show
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param channelsFollowRequest * @return [Channel]
     */
    @POST("channels/show")
    suspend fun channelsShow(
        @Body channelsFollowRequest: ChannelsFollowRequest,
        @Header("Content-Type") contentType: kotlin.String = "application/json",
    ): Channel

    /**
     * channels/unfavorite
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:channels*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param channelsFollowRequest * @return [Unit]
     */
    @POST("channels/unfavorite")
    suspend fun channelsUnfavorite(
        @Body channelsFollowRequest: ChannelsFollowRequest,
        @Header("Content-Type") contentType: kotlin.String = "application/json",
    ): Unit

    /**
     * channels/unfollow
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:channels*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param channelsFollowRequest * @return [Unit]
     */
    @POST("channels/unfollow")
    suspend fun channelsUnfollow(
        @Body channelsFollowRequest: ChannelsFollowRequest,
        @Header("Content-Type") contentType: kotlin.String = "application/json",
    ): Unit

    /**
     * channels/update
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:channels*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param channelsUpdateRequest * @return [Channel]
     */
    @POST("channels/update")
    suspend fun channelsUpdate(
        @Body channelsUpdateRequest: ChannelsUpdateRequest,
        @Header("Content-Type") contentType: kotlin.String = "application/json",
    ): Channel
}
