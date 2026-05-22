package dev.dimension.flare.data.model.tab

import dev.dimension.flare.data.database.app.model.SubscriptionType
import kotlinx.serialization.Serializable

@Serializable
internal data class RssTimelineData(
    val feedUrl: String,
) : TimelineSpec.Data

@Serializable
internal data object AllRssTimelineData : TimelineSpec.Data

@Serializable
internal data class SubscriptionTimelineData(
    val subscriptionUrl: String,
    val subscriptionType: SubscriptionType,
) : TimelineSpec.Data
