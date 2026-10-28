package dev.dimension.flare.model

import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface PlatformSpec {
    public val platformId: String
    public val metadata: PlatformMetadata
    public val order: Int
        get() = 0
    public val isDefaultGuest: Boolean
        get() = false
    public val capabilities: Set<PlatformCapability>
        get() = emptySet()

    public fun resolveInitialText(context: ComposeInitialTextContext): InitialText? = null

    public val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>>
    public val subscriptionTimelineSpecs: ImmutableList<SubscriptionTimelineSpec>
        get() = persistentListOf()

    public fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>>

    public fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource

    public fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource
}

@HiddenFromObjC
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

@HiddenFromObjC
public interface PlatformDataSourceContext {
    public val accountKey: MicroBlogKey

    public fun <T : Any> credential(serializer: KSerializer<T>): T

    public fun <T : Any> credentialFlow(serializer: KSerializer<T>): Flow<T>

    public suspend fun <T : Any> updateCredential(
        serializer: KSerializer<T>,
        credential: T,
    )

    /** Clears content derived from an older datasource implementation without removing the account. */
    public suspend fun invalidateCachedContent() {}
}

@HiddenFromObjC
public data class PlatformDeepLink<T>(
    public val uriPattern: String,
    public val serializer: KSerializer<T>,
    public val matcher: (T) -> Boolean = { true },
    public val callback: (T) -> DeeplinkRoute,
)

@HiddenFromObjC
public data class RecommendedInstance(
    public val instance: UiInstance,
    public val priority: Int = 0,
)

@Provided
@HiddenFromObjC
public class PlatformRuntimeData(
    platformSpecs: List<PlatformSpec>,
    public val extraTimelineSpecs: List<TimelineSpec<out TimelineSpec.Data>>,
    platformSpecSources: List<PlatformSpecSource> = emptyList(),
) {
    internal val builtInPlatformIds = platformSpecs.mapTo(mutableSetOf()) { it.platformId.lowercase() }
    internal val mutablePlatforms = MutableStateFlow(mergePlatformSpecs(platformSpecs, platformSpecSources))
    public val platforms: StateFlow<List<PlatformSpec>> = mutablePlatforms.asStateFlow()
    public val platformSpecs: List<PlatformSpec> get() = platforms.value

    internal val timelineSpecs get() =
        platformSpecs
            .sortedWith(compareBy<PlatformSpec> { it.order }.thenBy { it.platformId })
            .flatMap { it.timelineSpecs }
            .plus(extraTimelineSpecs)
}

@HiddenFromObjC
public interface PlatformSpecSource {
    public fun load(occupiedPlatformIds: Set<String>): List<PlatformSpec>
}

private fun mergePlatformSpecs(
    builtInSpecs: List<PlatformSpec>,
    sources: List<PlatformSpecSource>,
): List<PlatformSpec> {
    val result = builtInSpecs.toMutableList()
    val occupiedIds = builtInSpecs.associateByTo(linkedMapOf()) { it.platformId.lowercase() }

    sources.forEach { source ->
        source.load(occupiedIds.values.mapTo(linkedSetOf()) { it.platformId }).forEach { spec ->
            val normalizedId = spec.platformId.lowercase()
            if (normalizedId !in occupiedIds) {
                occupiedIds[normalizedId] = spec
                result += if (spec.isDefaultGuest) NonDefaultGuestPlatformSpec(spec) else spec
            }
        }
    }
    return result
}

private class NonDefaultGuestPlatformSpec(
    delegate: PlatformSpec,
) : PlatformSpec by delegate {
    override val isDefaultGuest: Boolean = false
}

@Single
@HiddenFromObjC
public class PlatformRegistry(
    private val data: PlatformRuntimeData,
) {
    public val platforms: StateFlow<List<PlatformSpec>> get() = data.platforms
    public val all: List<PlatformSpec> get() = validate(data.platformSpecs)

    init {
        validate(data.platformSpecs)
    }

    /** Replaces one external platform without disturbing built-in platforms or other sources. */
    public fun replaceExternal(
        platformId: String,
        replacement: PlatformSpec?,
    ) {
        require(platformId.lowercase() !in data.builtInPlatformIds) { "Cannot replace a built-in platform" }
        require(replacement == null || (replacement.platformId == platformId && !replacement.isDefaultGuest)) {
            "Replacement platform identity mismatch"
        }
        data.mutablePlatforms.update { current ->
            validate(current.filterNot { it.platformId == platformId } + listOfNotNull(replacement))
        }
    }

    private fun validate(values: List<PlatformSpec>): List<PlatformSpec> =
        values
            .also { specs ->
                specs.forEach { requireValidPlatformId(it.platformId) }
                val duplicateIds =
                    specs
                        .groupBy { it.platformId.lowercase() }
                        .filterValues { it.size > 1 }
                        .keys
                require(duplicateIds.isEmpty()) {
                    "Duplicate platform specs: ${duplicateIds.joinToString()}"
                }
                val defaultGuests = specs.filter { it.isDefaultGuest }
                require(defaultGuests.size == 1) {
                    "Exactly one default guest platform is required: ${defaultGuests.joinToString { it.platformId }}"
                }
            }.sortedWith(compareBy<PlatformSpec> { it.order }.thenBy { it.platformId })

    private val byId: Map<String, PlatformSpec> get() = all.associateBy { it.platformId }

    public val defaultGuest: PlatformSpec get() = all.single { it.isDefaultGuest }

    public val subscriptionTimelineSpecs: List<SubscriptionTimelineSpec> get() =
        all
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

    private val subscriptionTimelineSpecsByType: Map<SubscriptionType, SubscriptionTimelineSpec>
        get() = subscriptionTimelineSpecs.associateBy { it.type }

    public fun get(platformId: String): PlatformSpec? = byId[platformId]

    public fun require(platformId: String): PlatformSpec = get(platformId) ?: throw UnsupportedPlatformException(platformId)

    public fun isRegistered(platformId: String): Boolean = platformId in byId

    public fun metadataOrFallback(platformId: String): PlatformMetadata =
        get(platformId)?.metadata
            ?: PlatformMetadata(
                displayName = platformId,
                icon = dev.dimension.flare.ui.model.UiIcon.World,
            )

    public fun supports(
        platformId: String,
        capability: PlatformCapability,
    ): Boolean = capability in get(platformId)?.capabilities.orEmpty()

    public fun getSubscriptionTimelineSpec(type: SubscriptionType): SubscriptionTimelineSpec? = subscriptionTimelineSpecsByType[type]

    public fun requireSubscriptionTimelineSpec(type: SubscriptionType): SubscriptionTimelineSpec =
        getSubscriptionTimelineSpec(type) ?: throw UnsupportedSubscriptionTimelineException(type)
}

@HiddenFromObjC
public class UnsupportedPlatformException(
    public val platformId: String,
    public val accountKey: MicroBlogKey? = null,
) : IllegalArgumentException("Platform is not registered: $platformId")

@HiddenFromObjC
public class UnsupportedSubscriptionTimelineException(
    public val type: SubscriptionType,
) : IllegalArgumentException("Subscription timeline is not registered: $type")
