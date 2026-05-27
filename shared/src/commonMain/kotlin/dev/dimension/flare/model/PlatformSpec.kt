package dev.dimension.flare.model

import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

public interface PlatformSpec {
    public val type: PlatformType
    public val metadata: PlatformTypeMetadata
    public val detector: PlatformDetector
    public val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>>
    public val subscriptionTimelineSpecs: ImmutableList<SubscriptionTimelineSpec>
        get() = persistentListOf()

    public fun agreementUrl(host: String): String?

    public fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>>

    public suspend fun instanceMetadata(host: String): UiInstanceMetadata

    public suspend fun recommendInstances(): List<RecommendedInstance> = emptyList()

    public fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource

    public fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource
}

public interface SubscriptionTimelineSpec {
    public val type: SubscriptionType

    public suspend fun isAvailable(
        host: String,
        locale: String,
    ): Boolean

    public fun createLoader(
        host: String,
        locale: String,
    ): CacheableRemoteLoader<UiTimelineV2>
}

public interface PlatformDataSourceContext {
    public val accountKey: MicroBlogKey

    public fun <T : Any> credential(serializer: KSerializer<T>): T

    public fun <T : Any> credentialFlow(serializer: KSerializer<T>): Flow<T>

    public suspend fun <T : Any> updateCredential(
        serializer: KSerializer<T>,
        credential: T,
    )
}

public data class PlatformDeepLink<T>(
    public val uriPattern: String,
    public val serializer: KSerializer<T>,
    public val callback: (T) -> DeeplinkRoute,
)

public data class RecommendedInstance(
    public val instance: UiInstance,
    public val priority: Int = 0,
)

@Provided
public data class PlatformRuntimeData(
    public val platformSpecs: List<PlatformSpec>,
    public val extraTimelineSpecs: List<TimelineSpec<out TimelineSpec.Data>>,
) {
    internal val timelineSpecs by lazy {
        platformSpecs
            .flatMap { it.timelineSpecs }
            .plus(extraTimelineSpecs)
    }
}

@Single
public class PlatformRegistry(
    data: PlatformRuntimeData,
) {
    public val all: List<PlatformSpec> = data.platformSpecs
    private val byType: Map<PlatformType, PlatformSpec> =
        data.platformSpecs
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

    public val subscriptionTimelineSpecs: List<SubscriptionTimelineSpec> by lazy {
        data.platformSpecs
            .flatMap { it.subscriptionTimelineSpecs }
            .also { specs ->
                val duplicateTypes =
                    specs
                        .groupBy { it.type }
                        .filterValues { it.size > 1 }
                        .keys
                require(duplicateTypes.isEmpty()) {
                    "Duplicate subscription timeline specs: ${duplicateTypes.joinToString()}"
                }
            }
    }

    private val subscriptionTimelineSpecsByType: Map<SubscriptionType, SubscriptionTimelineSpec> by lazy {
        subscriptionTimelineSpecs.associateBy { it.type }
    }

    public fun get(type: PlatformType): PlatformSpec? = byType[type]

    public fun require(type: PlatformType): PlatformSpec = get(type) ?: throw UnsupportedPlatformException(type)

    public fun getSubscriptionTimelineSpec(type: SubscriptionType): SubscriptionTimelineSpec? = subscriptionTimelineSpecsByType[type]

    public fun requireSubscriptionTimelineSpec(type: SubscriptionType): SubscriptionTimelineSpec =
        getSubscriptionTimelineSpec(type) ?: throw UnsupportedSubscriptionTimelineException(type)
}

public class UnsupportedPlatformException(
    public val type: PlatformType,
) : IllegalArgumentException("Platform is not registered: $type")

public class UnsupportedSubscriptionTimelineException(
    public val type: SubscriptionType,
) : IllegalArgumentException("Subscription timeline is not registered: $type")
