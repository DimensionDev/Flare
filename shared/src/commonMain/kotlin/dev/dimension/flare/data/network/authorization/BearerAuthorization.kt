package dev.dimension.flare.data.network.authorization

import io.ktor.client.request.HttpRequestBuilder

internal class BearerAuthorization(
    private val accessToken: String,
) : Authorization {
    override val hasAuthorization: Boolean
        get() = true

    override fun getAuthorizationHeader(context: HttpRequestBuilder): String {
        return "Bearer $accessToken"
    }
}
