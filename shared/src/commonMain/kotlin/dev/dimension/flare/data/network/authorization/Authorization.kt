package dev.dimension.flare.data.network.authorization

import io.ktor.client.request.HttpRequestBuilder

internal interface Authorization {
    val hasAuthorization: Boolean

    fun getAuthorizationHeader(context: HttpRequestBuilder): String = ""
}
