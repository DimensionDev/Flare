package dev.dimension.flare.data.network.vvo.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.HEAD
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.vvo.model.ProfileData
import dev.dimension.flare.data.network.vvo.model.User
import dev.dimension.flare.data.network.vvo.model.VVOResponse

internal interface UserApi {
    @POST("api/friendships/create")
    @FormUrlEncoded
    suspend fun follow(
        @Field("st") st: String,
        @Field("uid") uid: String,
    ): VVOResponse<User>

    @POST("api/friendships/destroy")
    @FormUrlEncoded
    suspend fun unfollow(
        @Field("st") st: String,
        @Field("uid") uid: String,
    ): VVOResponse<User>

    @GET("profile/info")
    suspend fun profileInfo(
        @Query("uid") uid: String,
        @Header("X-Xsrf-Token") xsrfToken: String,
    ): VVOResponse<ProfileData>

    @HEAD("n/{screenName}")
    suspend fun checkUserExistence(
        @Query("screen_name") screenName: String,
    ): Response<Unit>
}
