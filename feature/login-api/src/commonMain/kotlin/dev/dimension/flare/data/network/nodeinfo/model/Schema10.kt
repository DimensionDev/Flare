package dev.dimension.flare.data.network.nodeinfo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * NodeInfo schema version 1.0.
 */
@Serializable
internal data class Schema10(
    /**
     * Metadata about server software in use.
     */
    val software: Software? = null,
) {
    /**
     * Metadata about server software in use.
     */
    @Serializable
    data class Software(
        /**
         * The canonical name of this server software.
         */
        val name: Name? = null,
        /**
         * The version of this server software.
         */
        val version: String? = null,
    )

    /**
     * The canonical name of this server software.
     */
    @Serializable
    enum class Name(
        val value: String,
    ) {
        @SerialName("diaspora")
        Diaspora("diaspora"),

        @SerialName("friendica")
        Friendica("friendica"),

        @SerialName("redmatrix")
        Redmatrix("redmatrix"),
    }
}
