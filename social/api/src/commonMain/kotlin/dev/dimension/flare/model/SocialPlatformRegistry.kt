package dev.dimension.flare.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.DebugRepository
import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import kotlinx.collections.immutable.ImmutableList

public interface SocialPlatformSpec {
    public val type: PlatformType
    public val metadata: PlatformTypeMetadata
    public val detector: PlatformDetector

    public fun agreementUrl(host: String): String?

    public fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>>

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

    public suspend fun recommendedInstances(): List<UiInstance> = emptyList()
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

    public suspend fun recommendedInstances(): List<UiInstance> =
        plugins.flatMap { plugin ->
            try {
                plugin.recommendedInstances()
            } catch (e: Exception) {
                DebugRepository.error(e)
                emptyList()
            }
        }.distinctBy { "${it.type}:${it.domain}" }

    public fun requireSpec(type: PlatformType): SocialPlatformSpec =
        requireNotNull(specsByType[type]?.spec) {
            "No social platform registered for $type"
        }

    public fun metadata(type: PlatformType): PlatformTypeMetadata = requireSpec(type).metadata

    public fun agreementUrl(
        type: PlatformType,
        host: String,
    ): String? = requireSpec(type).agreementUrl(host)

    public fun deepLinkPatterns(
        type: PlatformType,
        host: String,
    ): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> = requireSpec(type).deepLinkPatterns(host)

    public suspend fun instanceMetadata(
        type: PlatformType,
        host: String,
    ): UiInstanceMetadata = requireSpec(type).instanceMetadata(host)

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
