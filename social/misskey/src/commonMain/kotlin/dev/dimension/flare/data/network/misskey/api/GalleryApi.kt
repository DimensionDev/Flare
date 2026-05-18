package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.AdminAdListRequest
import dev.dimension.flare.data.network.misskey.api.model.GalleryPost
import dev.dimension.flare.data.network.misskey.api.model.GalleryPostsCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.GalleryPostsDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.GalleryPostsUpdateRequest

public interface GalleryApi {
    /**
     * gallery/featured
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [kotlin.collections.List<GalleryPost>]
     */
    @POST("gallery/featured")
    public suspend fun galleryFeatured(
        @Body body: kotlin.Any,
    ): kotlin.collections.List<GalleryPost>

    /**
     * gallery/popular
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [kotlin.collections.List<GalleryPost>]
     */
    @POST("gallery/popular")
    public suspend fun galleryPopular(
        @Body body: kotlin.Any,
    ): kotlin.collections.List<GalleryPost>

    /**
     * gallery/posts
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAdListRequest * @return [kotlin.collections.List<GalleryPost>]
     */
    @POST("gallery/posts")
    public suspend fun galleryPosts(
        @Body adminAdListRequest: AdminAdListRequest,
    ): kotlin.collections.List<GalleryPost>

    /**
     * gallery/posts/create
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:gallery*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param galleryPostsCreateRequest * @return [GalleryPost]
     */
    @POST("gallery/posts/create")
    public suspend fun galleryPostsCreate(
        @Body galleryPostsCreateRequest: GalleryPostsCreateRequest,
    ): GalleryPost

    /**
     * gallery/posts/delete
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:gallery*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param galleryPostsDeleteRequest * @return [Unit]
     */
    @POST("gallery/posts/delete")
    public suspend fun galleryPostsDelete(
        @Body galleryPostsDeleteRequest: GalleryPostsDeleteRequest,
    ): Unit

    /**
     * gallery/posts/like
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:gallery-likes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param galleryPostsDeleteRequest * @return [Unit]
     */
    @POST("gallery/posts/like")
    public suspend fun galleryPostsLike(
        @Body galleryPostsDeleteRequest: GalleryPostsDeleteRequest,
    ): Unit

    /**
     * gallery/posts/show
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param galleryPostsDeleteRequest * @return [GalleryPost]
     */
    @POST("gallery/posts/show")
    public suspend fun galleryPostsShow(
        @Body galleryPostsDeleteRequest: GalleryPostsDeleteRequest,
    ): GalleryPost

    /**
     * gallery/posts/unlike
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:gallery-likes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param galleryPostsDeleteRequest * @return [Unit]
     */
    @POST("gallery/posts/unlike")
    public suspend fun galleryPostsUnlike(
        @Body galleryPostsDeleteRequest: GalleryPostsDeleteRequest,
    ): Unit

    /**
     * gallery/posts/update
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:gallery*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param galleryPostsUpdateRequest * @return [GalleryPost]
     */
    @POST("gallery/posts/update")
    public suspend fun galleryPostsUpdate(
        @Body galleryPostsUpdateRequest: GalleryPostsUpdateRequest,
    ): GalleryPost
}
