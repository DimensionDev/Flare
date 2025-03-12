package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.AuthSessionGenerate200Response
import dev.dimension.flare.data.network.misskey.api.model.AuthSessionGenerateRequest
import dev.dimension.flare.data.network.misskey.api.model.AuthSessionShow200Response
import dev.dimension.flare.data.network.misskey.api.model.AuthSessionShowRequest
import dev.dimension.flare.data.network.misskey.api.model.AuthSessionUserkey200Response
import dev.dimension.flare.data.network.misskey.api.model.AuthSessionUserkeyRequest

internal interface AuthApi {
    /**
     * auth/session/generate
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param authSessionGenerateRequest * @return [AuthSessionGenerate200Response]
     */
    @POST("auth/session/generate")
    suspend fun authSessionGenerate(
        @Body authSessionGenerateRequest: AuthSessionGenerateRequest,
    ): AuthSessionGenerate200Response

    /**
     * auth/session/show
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param authSessionShowRequest * @return [AuthSessionShow200Response]
     */
    @POST("auth/session/show")
    suspend fun authSessionShow(
        @Body authSessionShowRequest: AuthSessionShowRequest,
    ): AuthSessionShow200Response

    /**
     * auth/session/userkey
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param authSessionUserkeyRequest * @return [AuthSessionUserkey200Response]
     */
    @POST("auth/session/userkey")
    suspend fun authSessionUserkey(
        @Body authSessionUserkeyRequest: AuthSessionUserkeyRequest,
    ): AuthSessionUserkey200Response
}
