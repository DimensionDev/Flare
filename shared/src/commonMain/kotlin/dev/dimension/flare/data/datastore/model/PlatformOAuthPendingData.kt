package dev.dimension.flare.data.datastore.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PlatformOAuthPendingData(
    val entries: List<Entry> = emptyList(),
) {
    @Serializable
    internal data class Entry(
        @SerialName("platformType")
        val platformId: String,
        val host: String,
        val flowId: String = "OAuth",
        val createdAtEpochMillis: Long = 0L,
        val attributes: List<Attribute> = emptyList(),
    )

    @Serializable
    internal data class Attribute(
        val key: String,
        val value: String,
    )
}
