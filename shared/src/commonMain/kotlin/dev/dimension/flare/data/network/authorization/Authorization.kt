package dev.dimension.flare.data.network.authorization

import io.ktor.client.request.HttpRequestBuilder

interface Authorization {
    val hasAuthorization: Boolean
    fun getAuthorizationHeader(context: HttpRequestBuilder): String = ""
}
