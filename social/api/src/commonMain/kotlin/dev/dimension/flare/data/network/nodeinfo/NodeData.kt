package dev.dimension.flare.data.network.nodeinfo

import dev.dimension.flare.model.PlatformType

public data class NodeData(
    public val host: String,
    public val platformType: PlatformType,
    public val software: String,
    // not officially supported, but works fine for basic features
    public val compatibleMode: Boolean,
)
