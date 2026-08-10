package dev.dimension.flare.data.network.nodeinfo.model

import kotlinx.serialization.Serializable

/**
 * NodeInfo schema version 2.1.
 */
@Serializable
internal data class Schema21(
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
         * The url of the homepage of this server software.
         */
        val homepage: String? = null,
        /**
         * The canonical name of this server software.
         */
        val name: String? = null,
        /**
         * The url of the source code repository of this server software.
         */
        val repository: String? = null,
        /**
         * The version of this server software.
         */
        val version: String? = null,
    )
}
