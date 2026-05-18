package dev.dimension.flare.data.datasource.xqt

import de.jensklingenberg.ktorfit.Response
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.GetProfileSpotlightsQuery200Response
import dev.dimension.flare.data.network.xqt.model.GetUserByRestId200Response
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class UserByRestIdRequest(
    @SerialName("userId")
    val userID: String? = null,
    val withSafetyModeUserFields: Boolean? = null,
)

@Serializable
internal data class UserByScreenNameRequest(
    @SerialName("screen_name")
    val screenName: String? = null,
    val withSafetyModeUserFields: Boolean? = null,
)

@Serializable
internal data class ProfileSpotlightsQueryRequest(
    @SerialName("screen_name")
    val screenName: String? = null,
)

public suspend fun XQTService.userById(id: String): Response<GetUserByRestId200Response> =
    getUserByRestId(
        variables =
            UserByRestIdRequest(
                userID = id,
                withSafetyModeUserFields = true,
            ).encodeJson(),
    )

public suspend fun XQTService.userByScreenName(screenName: String): Response<GetUserByRestId200Response> =
    getUserByScreenName(
        variables =
            UserByScreenNameRequest(
                screenName = screenName,
                withSafetyModeUserFields = true,
            ).encodeJson(),
    )

public suspend fun XQTService.profileSpotlights(screenName: String): Response<GetProfileSpotlightsQuery200Response> =
    getProfileSpotlightsQuery(
        variables =
            ProfileSpotlightsQueryRequest(
                screenName = screenName,
            ).encodeJson(),
    )
