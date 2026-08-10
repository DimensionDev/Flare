package dev.dimension.flare.data.network.nodeinfo.model

import kotlinx.serialization.Serializable

@Serializable
internal data class NodeInfo(
    val links: List<Link>,
)

@Serializable
internal data class Link(
    val rel: String,
    val href: String,
)
