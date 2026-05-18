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

public interface ChannelsApi {
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
    public suspend fun channelsCreate(
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
    public suspend fun channelsFavorite(
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
    public suspend fun channelsFeatured(
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
    public suspend fun channelsFollow(
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
    public suspend fun channelsFollowed(
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
    public suspend fun channelsMyFavorites(
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
    public suspend fun channelsOwned(
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
    public suspend fun channelsSearch(
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
    public suspend fun channelsShow(
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
    public suspend fun channelsUnfavorite(
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
    public suspend fun channelsUnfollow(
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
    public suspend fun channelsUpdate(
        @Body channelsUpdateRequest: ChannelsUpdateRequest,
        @Header("Content-Type") contentType: kotlin.String = "application/json",
    ): Channel
}
