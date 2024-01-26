package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.TestRequest

internal interface NonProductiveApi {
    /**
     * reset-db
     * Only available when running with &lt;code&gt;NODE_ENV&#x3D;testing&lt;/code&gt;. Reset the database and flush Redis.  **Credential required**: *No*
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
    @POST("reset-db")
    suspend fun resetDb(
        @Body body: kotlin.Any,
    ): Response<Unit>

    /**
     * test
     * Endpoint for testing input validation.  **Credential required**: *No*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param testRequest * @return [Unit]
     */
    @POST("test")
    suspend fun test(
        @Body testRequest: TestRequest,
    ): Response<Unit>
}
