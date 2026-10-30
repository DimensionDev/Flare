package dev.dimension.flare.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.ui.model.UiText
import kotlinx.serialization.Serializable

@Immutable
@Serializable
public data class PlatformMetadata(
    val displayName: String,
    val icon: IconType,
    val agentAliases: List<String> = emptyList(),
    val displayNameText: UiText = UiText.Raw(displayName),
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
