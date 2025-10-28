package dev.dimension.flare.data.network.vvo.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.HEAD
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.vvo.model.ContainerInfo
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

    @POST("api/friendships/destory")
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
        @Path("screenName") screenName: String,
    ): Response<Unit>

    @GET("api/container/getIndex")
    suspend fun getContainerIndex(
        @Query("type") type: String? = null,
        @Query("value") value: String? = null,
        @Query("containerid") containerId: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("page") page: Int? = null,
        @Query("page_type") pageType: String? = null,
        @Query("openApp") openApp: Int? = null,
    ): VVOResponse<ContainerInfo>
}
