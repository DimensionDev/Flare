package dev.dimension.flare.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.ui.model.UiIcon
import kotlinx.serialization.Serializable

@Immutable
@Serializable
public data class PlatformMetadata(
    val displayName: String,
    val icon: UiIcon,
    val agentAliases: List<String> = emptyList(),
)

public enum class PlatformCapability {
    RelayManagement,
    MxgaFiltering,
    FirstEmbeddedQuoteTarget,
}

private val platformIdPattern = Regex("[A-Za-z][A-Za-z0-9_-]*")

public fun requireValidPlatformId(platformId: String): String {
    require(platformIdPattern.matches(platformId)) {
        "Invalid platform id: $platformId"
    }
    return platformId
}
