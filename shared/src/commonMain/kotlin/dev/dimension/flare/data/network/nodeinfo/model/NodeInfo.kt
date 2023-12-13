package dev.dimension.flare.data.network.nodeinfo.model

import kotlinx.serialization.Serializable

@Serializable
data class NodeInfo(
    val links: List<Link>,
)

@Serializable
data class Link(
    val rel: String,
    val href: String,
)
