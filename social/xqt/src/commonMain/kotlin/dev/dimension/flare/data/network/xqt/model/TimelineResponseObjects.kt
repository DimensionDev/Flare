package dev.dimension.flare.data.network.xqt.model

import kotlinx.serialization.Serializable

@Serializable
internal data class TimelineResponseObjects(
    val feedbackActions: List<TimelineFeedbackAction> = emptyList(),
)

@Serializable
internal data class TimelineFeedbackAction(
    val key: String,
    val value: TimelineFeedbackActionValue,
)

@Serializable
internal data class TimelineFeedbackActionValue(
    val feedbackType: String? = null,
    val feedbackUrl: String? = null,
    val prompt: String? = null,
)
