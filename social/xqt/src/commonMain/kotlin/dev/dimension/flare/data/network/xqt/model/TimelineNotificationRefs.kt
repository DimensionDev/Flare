package dev.dimension.flare.data.network.xqt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class TimelineNotificationUserRef(
    @SerialName("user_results")
    val userResults: UserResults? = null,
)

@Serializable
internal data class TimelineNotificationTweetRef(
    @SerialName("tweet_results")
    val tweetResults: ItemResult? = null,
)
