package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

internal interface FollowRequestsApi {
    @GET("1.1/friendships/incoming.json")
    suspend fun incomingFollowRequests(
        @Query("cursor") cursor: String,
        @Query("count") count: Int,
        @Query("stringify_ids") stringifyIds: Boolean = true,
    ): Response<JsonElement>

    @GET("1.1/users/lookup.json")
    suspend fun followRequestUsers(
        @Query("user_id") userIds: String,
        @Query("skip_status") skipStatus: Int = 1,
        @Query("include_entities") includeEntities: Boolean = true,
    ): Response<JsonElement>

    @FormUrlEncoded
    @POST("1.1/friendships/accept.json")
    suspend fun acceptFollowRequest(
        @Field("user_id") userId: String,
    ): Response<JsonElement>

    @FormUrlEncoded
    @POST("1.1/friendships/deny.json")
    suspend fun rejectFollowRequest(
        @Field("user_id") userId: String,
    ): Response<JsonElement>
}

internal suspend fun FollowRequestsApi.respondToFollowRequest(
    userId: String,
    accept: Boolean,
) {
    val response = if (accept) acceptFollowRequest(userId) else rejectFollowRequest(userId)
    check(response.checkedFollowRequestBody() is JsonObject) { "Invalid X follow request response" }
}

internal fun Response<JsonElement>.checkedFollowRequestBody(): JsonElement {
    check(isSuccessful) { "X follow request failed with HTTP $code" }
    val body = checkNotNull(body()) { "Empty X follow request response" }
    val errors = (body as? JsonObject)?.get("errors")
    check(errors == null || errors == JsonNull || (errors is JsonArray && errors.isEmpty())) {
        (errors as? JsonArray)?.firstOrNull()?.let { (it as? JsonObject)?.get("message")?.jsonPrimitive?.content }
            ?: "X follow request failed"
    }
    return body
}
