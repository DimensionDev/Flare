package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.ui.model.UiIcon
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data class NodeDetection(
    public val host: String,
    public val software: String,
    // not officially supported, but works fine for basic features
    public val compatibleMode: Boolean,
)

@HiddenFromObjC
public interface PlatformDetector {
    public val priority: Int
        get() = 0

    public suspend fun detect(host: String): NodeDetection?
}

public data class NodeData(
    public val host: String,
    public val platformId: String,
    public val software: String,
    public val compatibleMode: Boolean,
    public val platformDisplayName: String,
    public val platformIcon: UiIcon,
    public val loginMethods: List<LoginMethodSpec>,
)
