package dev.dimension.flare.model

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.KSerializer

public interface PlatformSpec {
    public val type: PlatformType
    public val metadata: PlatformTypeMetadata
    public val detector: PlatformDetector
    public val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>>

    public fun agreementUrl(host: String): String?

    public fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>>

    public suspend fun instanceMetadata(host: String): UiInstanceMetadata

    public fun restoreAccount(
        accountKey: MicroBlogKey,
        credentialJson: String,
    ): UiAccount

    public fun createDataSource(account: UiAccount): MicroblogDataSource

    public fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource
}

public data class PlatformDeepLink<T>(
    public val uriPattern: String,
    public val serializer: KSerializer<T>,
    public val callback: (T) -> DeeplinkRoute,
)

public class PlatformRegistry(
    public val all: List<PlatformSpec>,
) {
    private val byType: Map<PlatformType, PlatformSpec> =
        all
            .also { specs ->
                val duplicateTypes =
                    specs
                        .groupBy { it.type }
                        .filterValues { it.size > 1 }
                        .keys
                require(duplicateTypes.isEmpty()) {
                    "Duplicate platform specs: ${duplicateTypes.joinToString()}"
                }
            }.associateBy { it.type }

    public fun get(type: PlatformType): PlatformSpec? = byType[type]

    public fun require(type: PlatformType): PlatformSpec =
        get(type) ?: throw UnsupportedPlatformException(type)
}

public class UnsupportedPlatformException(
    public val type: PlatformType,
) : IllegalArgumentException("Platform is not registered: $type")
