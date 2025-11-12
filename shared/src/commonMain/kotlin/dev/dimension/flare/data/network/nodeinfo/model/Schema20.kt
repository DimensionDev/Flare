package dev.dimension.flare.data.network.nodeinfo.model

import kotlinx.serialization.Serializable

/**
 * NodeInfo schema version 2.0.
 */
@Serializable
internal data class Schema20(
    /**
     * Metadata about server software in use.
     */
    val software: Software? = null,
) {
    /**
     * Metadata about server software in use.
     */
    @Serializable
    internal data class Software(
        /**
         * The canonical name of this server software.
         */
        val name: String? = null,
        /**
         * The version of this server software.
         */
        val version: String? = null,
    )
}
