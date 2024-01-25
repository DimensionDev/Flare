package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.network.xqt.XQTService
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserByRestIdRequest(
    @SerialName("userId")
    val userID: String? = null,
    val withSafetyModeUserFields: Boolean? = null,
)

@Serializable
data class UserByScreenNameRequest(
    @SerialName("screen_name")
    val screenName: String? = null,
    val withSafetyModeUserFields: Boolean? = null,
)

@Serializable
data class ProfileSpotlightsQueryRequest(
    @SerialName("screen_name")
    val screenName: String? = null,
)

suspend fun XQTService.userById(id: String) =
    getUserByRestId(
        variables =
            UserByRestIdRequest(
                userID = id,
                withSafetyModeUserFields = true,
            ).encodeJson(),
    )

suspend fun XQTService.userByScreenName(screenName: String) =
    getUserByScreenName(
        variables =
            UserByScreenNameRequest(
                screenName = screenName,
                withSafetyModeUserFields = true,
            ).encodeJson(),
    )

suspend fun XQTService.profileSpotlights(screenName: String) =
    getProfileSpotlightsQuery(
        variables =
            ProfileSpotlightsQueryRequest(
                screenName = screenName,
            ).encodeJson(),
    )
