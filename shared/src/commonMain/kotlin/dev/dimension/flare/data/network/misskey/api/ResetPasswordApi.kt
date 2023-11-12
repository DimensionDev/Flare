package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.RequestResetPasswordRequest
import dev.dimension.flare.data.network.misskey.api.model.ResetPasswordRequest

interface ResetPasswordApi {
    /**
     * request-reset-password
     * Request a users password to be reset.  **Credential required**: *No*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param requestResetPasswordRequest * @return [Unit]
     */
    @POST("request-reset-password")
    suspend fun requestResetPassword(
        @Body requestResetPasswordRequest: RequestResetPasswordRequest,
    ): Response<Unit>

    /**
     * reset-password
     * Complete the password reset that was previously requested.  **Credential required**: *No*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param resetPasswordRequest * @return [Unit]
     */
    @POST("reset-password")
    suspend fun resetPassword(
        @Body resetPasswordRequest: ResetPasswordRequest,
    ): Response<Unit>
}
