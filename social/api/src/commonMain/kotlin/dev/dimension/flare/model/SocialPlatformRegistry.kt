package dev.dimension.flare.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import kotlinx.collections.immutable.ImmutableList

public interface SocialPlatformSpec {
    public val type: PlatformType
    public val metadata: PlatformTypeMetadata
    public val detector: PlatformDetector

    public fun agreementUrl(host: String): String?

    public fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out Any>>

    public suspend fun instanceMetadata(host: String): UiInstanceMetadata

    public fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource
}

@Immutable
public data class PlatformTypeMetadata(
    public val displayName: String,
    public val icon: UiIcon,
)

public interface SocialPlatformPlugin {
    public val spec: SocialPlatformSpec

    public fun createDataSource(account: UiAccount): MicroblogDataSource?
}

public class SocialPlatformRegistry(
    plugins: List<SocialPlatformPlugin>,
) {
    private val plugins = plugins.distinctBy { it.spec.type }
    private val specsByType = this.plugins.associateBy { it.spec.type }

    public val specs: List<SocialPlatformSpec>
        get() = plugins.map { it.spec }

    public val loginPlatformTypes: List<PlatformType>
        get() = specs.map { it.type }

    public fun requireSpec(type: PlatformType): SocialPlatformSpec =
        requireNotNull(specsByType[type]?.spec) {
            "No social platform registered for $type"
        }

    public fun createDataSource(account: UiAccount): MicroblogDataSource =
        plugins.firstNotNullOfOrNull { it.createDataSource(account) }
            ?: error("No social platform data source registered for ${account.platformType}")

    public fun guestDataSource(
        type: PlatformType,
        host: String,
        locale: String,
    ): MicroblogDataSource =
        requireSpec(type).guestDataSource(
            host = host,
            locale = locale,
        )
}
