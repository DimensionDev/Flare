package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.Page
import dev.dimension.flare.data.network.misskey.api.model.PagesCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.PagesDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.PagesShowRequest
import dev.dimension.flare.data.network.misskey.api.model.PagesUpdateRequest

interface PagesApi {
    /**
     * pages/create
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:pages*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param pagesCreateRequest * @return [Page]
     */
    @POST("pages/create")
    suspend fun pagesCreate(
        @Body pagesCreateRequest: PagesCreateRequest,
    ): Response<Page>

    /**
     * pages/delete
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:pages*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param pagesDeleteRequest * @return [Unit]
     */
    @POST("pages/delete")
    suspend fun pagesDelete(
        @Body pagesDeleteRequest: PagesDeleteRequest,
    ): Response<Unit>

    /**
     * pages/featured
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [kotlin.collections.List<Page>]
     */
    @POST("pages/featured")
    suspend fun pagesFeatured(
        @Body body: kotlin.Any,
    ): Response<kotlin.collections.List<Page>>

    /**
     * pages/like
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:page-likes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param pagesDeleteRequest * @return [Unit]
     */
    @POST("pages/like")
    suspend fun pagesLike(
        @Body pagesDeleteRequest: PagesDeleteRequest,
    ): Response<Unit>

    /**
     * pages/show
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param pagesShowRequest * @return [Page]
     */
    @POST("pages/show")
    suspend fun pagesShow(
        @Body pagesShowRequest: PagesShowRequest,
    ): Response<Page>

    /**
     * pages/unlike
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:page-likes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param pagesDeleteRequest * @return [Unit]
     */
    @POST("pages/unlike")
    suspend fun pagesUnlike(
        @Body pagesDeleteRequest: PagesDeleteRequest,
    ): Response<Unit>

    /**
     * pages/update
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:pages*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param pagesUpdateRequest * @return [Unit]
     */
    @POST("pages/update")
    suspend fun pagesUpdate(
        @Body pagesUpdateRequest: PagesUpdateRequest,
    ): Response<Unit>
}
