package dev.dimension.flare.data.model.tab

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.data.model.appearance.AppearanceKeys
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.data.model.appearance.toBag
import dev.dimension.flare.data.model.appearance.toPatch
import dev.dimension.flare.data.model.appearance.withPatch
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.SystemHomeMixedTimelinePresenter
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@Immutable
@Serializable
internal data class TabSettingsV2(
    val homeSlots: List<TimelineSlot> = emptyList(),
)

@Immutable
@Serializable
public data class TimelineFilterConfig(
    val excludedKinds: List<TimelinePostKind> = emptyList(),
    val excludedContents: List<TimelinePostContent> = emptyList(),
)

@Immutable
@Serializable
public enum class TimelinePostKind {
    Original,
    Reply,
    Repost,
    Quote,
}

@Immutable
@Serializable
public enum class TimelinePostContent {
    Text,
    Image,
    Video,
    Other,
}

@Immutable
public sealed interface UiTimelineTabItem {
    public val id: String
    public val title: UiText
    public val icon: IconType
    public val appearancePatch: AppearancePatch?
    public val enabled: Boolean
    public val filterConfig: TimelineFilterConfig
    public val loaderKey: String

    // for iOS and Compose call sites
    public val key: String get() = id

    public fun withPresentationOverrides(
        title: String,
        icon: IconType,
        appearancePatch: AppearancePatch? = this.appearancePatch,
        enabled: Boolean = this.enabled,
        filterConfig: TimelineFilterConfig = this.filterConfig,
    ): UiTimelineTabItem
}

public fun UiTimelineTabItem.resolveTimelineAppearance(base: TimelineAppearance): TimelineAppearance = base.withPatch(appearancePatch)

private val galleryAppearancePatch: AppearancePatch =
    AppearancePatch.EMPTY.set(
        AppearanceKeys.TimelineDisplayMode,
        TimelineDisplayMode.Gallery,
    )

@Immutable
public class UiSourceTimelineTabItem internal constructor(
    override val id: String,
    internal val source: TimelineSourceRef?,
    internal val presentation: TimelinePresentation?,
    override val title: UiText,
    override val icon: IconType,
    override val appearancePatch: AppearancePatch?,
    override val enabled: Boolean,
) : UiTimelineTabItem {
    public companion object {}

    override val filterConfig: TimelineFilterConfig
        get() = presentation?.filterConfig ?: TimelineFilterConfig()

    override val loaderKey: String
        get() = encodeTimelineLoaderKey(toSlotForLoaderKey())

    override fun withPresentationOverrides(
        title: String,
        icon: IconType,
        appearancePatch: AppearancePatch?,
        enabled: Boolean,
        filterConfig: TimelineFilterConfig,
    ): UiTimelineTabItem {
        val updatedPresentation =
            presentation?.withOverrides(
                titleOverride = title,
                iconOverride = icon,
                appearancePatch = appearancePatch,
                enabled = enabled,
                filterConfig = filterConfig,
            ) ?: TimelinePresentation(
                titleOverride = title,
                iconOverride = icon,
                appearanceOverride = appearancePatch?.takeUnless { it == AppearancePatch.EMPTY }?.toBag(),
                enabled = enabled,
                filterConfig = filterConfig,
            )
        return UiSourceTimelineTabItem(
            id = id,
            source = source,
            presentation = updatedPresentation,
            title = UiText.Raw(title),
            icon = icon,
            appearancePatch = updatedPresentation.appearance,
            enabled = updatedPresentation.enabled,
        )
    }
}

@Immutable
public class UiGroupTimelineTabItem internal constructor(
    override val id: String,
    public val children: List<UiTimelineTabItem>,
    public val mergePolicy: TimelineMergePolicy,
    internal val source: GroupSource,
    internal val presentation: TimelinePresentation,
    override val title: UiText,
    override val icon: IconType,
    override val appearancePatch: AppearancePatch?,
    override val enabled: Boolean,
) : UiTimelineTabItem {
    override val filterConfig: TimelineFilterConfig
        get() = presentation.filterConfig

    override val loaderKey: String
        get() = encodeTimelineLoaderKey(toSlotForLoaderKey())

    override fun withPresentationOverrides(
        title: String,
        icon: IconType,
        appearancePatch: AppearancePatch?,
        enabled: Boolean,
        filterConfig: TimelineFilterConfig,
    ): UiTimelineTabItem {
        val updatedPresentation =
            presentation.withOverrides(
                titleOverride = title,
                iconOverride = icon,
                appearancePatch = appearancePatch,
                enabled = enabled,
                filterConfig = filterConfig,
            )
        return UiGroupTimelineTabItem(
            id = id,
            children = children,
            mergePolicy = mergePolicy,
            source = source,
            presentation = updatedPresentation,
            title = UiText.Raw(title),
            icon = icon,
            appearancePatch = updatedPresentation.appearance,
            enabled = updatedPresentation.enabled,
        )
    }
}

public val UiTimelineTabItem.isSystemHomeMixedTimeline: Boolean
    get() = this is UiGroupTimelineTabItem && source == GroupSource.SystemHome

internal fun UiTimelineTabItem.findById(id: String): UiTimelineTabItem? =
    when (this) {
        is UiSourceTimelineTabItem -> takeIf { this.id == id }
        is UiGroupTimelineTabItem -> takeIf { this.id == id } ?: children.firstNotNullOfOrNull { it.findById(id) }
    }

internal fun List<UiTimelineTabItem>.findById(id: String): UiTimelineTabItem? = firstNotNullOfOrNull { it.findById(id) }

public fun List<UiTimelineTabItem>.withSystemHomeMixedTimelineEnabled(
    enabled: Boolean,
    mergePolicy: TimelineMergePolicy? = null,
    filterConfig: TimelineFilterConfig? = null,
): List<UiTimelineTabItem> {
    val existingGroup = filterIsInstance<UiGroupTimelineTabItem>().firstOrNull { it.source == GroupSource.SystemHome }
    val tabsWithoutSystemGroup = filterNot { it.isSystemHomeMixedTimeline }
    if (!enabled || tabsWithoutSystemGroup.size < 2) {
        return tabsWithoutSystemGroup
    }

    val systemGroup =
        UiGroupTimelineTabItem(
            id = SYSTEM_HOME_MIXED_TIMELINE_ID,
            children = tabsWithoutSystemGroup,
            mergePolicy = mergePolicy ?: existingGroup?.mergePolicy ?: TimelineMergePolicy.TimePerPage,
            source = GroupSource.SystemHome,
            presentation =
                (existingGroup?.presentation ?: TimelinePresentation()).copy(
                    filterConfig = filterConfig ?: existingGroup?.filterConfig ?: TimelineFilterConfig(),
                ),
            title = existingGroup?.title ?: UiStrings.MixedTimeline.asText(),
            icon = existingGroup?.icon ?: UiIcon.Rss.asType(),
            appearancePatch = existingGroup?.appearancePatch,
            enabled = existingGroup?.enabled ?: true,
        )
    val targetIndex =
        indexOfFirst { it.isSystemHomeMixedTimeline }
            .takeIf { it >= 0 }
            ?: 0
    return tabsWithoutSystemGroup
        .toMutableList()
        .apply {
            add(minOf(targetIndex, size), systemGroup)
        }
}

internal const val SYSTEM_HOME_MIXED_TIMELINE_ID = "mixed_timeline_system_home"

@Immutable
@Serializable
internal data class TimelineSlot(
    val id: String,
    val content: TimelineSlotContent,
    val presentation: TimelinePresentation = TimelinePresentation(),
) {
    val title: UiText =
        presentation.titleOverride?.let { UiText.Raw(it) } ?: when (content) {
            is TimelineSlotContent.Source -> content.source.title
            is TimelineSlotContent.Group -> UiStrings.MixedTimeline.asText()
        }

    val icon: IconType =
        presentation.iconOverride ?: when (content) {
            is TimelineSlotContent.Source -> content.source.icon
            is TimelineSlotContent.Group -> UiIcon.Rss.asType()
        }
}

@Immutable
@Serializable
internal data class TimelinePresentation internal constructor(
    val titleOverride: String? = null,
    val iconOverride: IconType? = null,
    private val appearanceOverride: AppearanceBag? = null,
    val enabled: Boolean = true,
    val filterConfig: TimelineFilterConfig = TimelineFilterConfig(),
) {
    val appearance: AppearancePatch? by lazy {
        appearanceOverride?.toPatch()
    }

    internal fun withOverrides(
        titleOverride: String?,
        iconOverride: IconType?,
        appearancePatch: AppearancePatch?,
        enabled: Boolean,
        filterConfig: TimelineFilterConfig,
    ): TimelinePresentation =
        TimelinePresentation(
            titleOverride = titleOverride,
            iconOverride = iconOverride,
            appearanceOverride = appearancePatch?.takeUnless { it == AppearancePatch.EMPTY }?.toBag(),
            enabled = enabled,
            filterConfig = filterConfig,
        )
}

@Immutable
@Serializable
internal sealed interface TimelineSlotContent {
    @Immutable
    @Serializable
    @SerialName("source")
    data class Source(
        @SerialName("target")
        val source: TimelineSourceRef,
    ) : TimelineSlotContent

    @Immutable
    @Serializable
    @SerialName("group")
    data class Group(
        val children: List<TimelineSlot> = emptyList(),
        val source: GroupSource = GroupSource.Manual,
        val mergePolicy: TimelineMergePolicy = TimelineMergePolicy.Time,
    ) : TimelineSlotContent
}

@Immutable
@Serializable
internal enum class GroupSource {
    Manual,
    SystemHome,
}

@Immutable
@Serializable
public enum class TimelineMergePolicy {
    Time,
    TimePerPage,
    Staggered,
}

@Immutable
@Serializable
internal data class TimelineSourceRef(
    val id: String,
    val specId: String,
    val title: UiText,
    val icon: IconType,
    val data: String,
)

internal fun TimelineSourceRef.toSlot(
    slotId: String = id,
    presentation: TimelinePresentation = TimelinePresentation(),
): TimelineSlot =
    TimelineSlot(
        id = slotId,
        content = TimelineSlotContent.Source(this),
        presentation = presentation,
    )

@OptIn(ExperimentalSerializationApi::class)
private fun encodeTimelineLoaderKey(slot: TimelineSlot): String = ProtoBuf.encodeToHexString(TimelineSlot.serializer(), slot)

@OptIn(ExperimentalSerializationApi::class)
private fun decodeTimelineLoaderKey(key: String): TimelineSlot = ProtoBuf.decodeFromHexString(TimelineSlot.serializer(), key)

private fun UiTimelineTabItem.toSlotForLoaderKey(): TimelineSlot =
    when (this) {
        is UiSourceTimelineTabItem -> {
            val source =
                source ?: throw IllegalArgumentException("Timeline tab has no source: $id")
            source.toSlot(
                slotId = id,
                presentation = presentation ?: TimelinePresentation(),
            )
        }

        is UiGroupTimelineTabItem -> {
            TimelineSlot(
                id = id,
                content =
                    TimelineSlotContent.Group(
                        children = children.map { it.toSlotForLoaderKey() },
                        source = source,
                        mergePolicy = mergePolicy,
                    ),
                presentation = presentation,
            )
        }
    }

@HiddenFromObjC
public fun interface TimelineLoaderFactory<T : TimelineSpec.Data> {
    public fun create(
        data: T,
        context: TimelineLoaderContext,
    ): Flow<RemoteLoader<UiTimelineV2>>
}

@HiddenFromObjC
public class TimelineLoaderContext(
    private val accountService: AccountService,
) {
    public fun accountServiceFlow(accountType: AccountType): Flow<MicroblogDataSource> = accountService.accountServiceFlow(accountType)
}

@HiddenFromObjC
public fun <T : TimelineSpec.Data> remoteLoaderFactory(factory: (data: T) -> RemoteLoader<UiTimelineV2>): TimelineLoaderFactory<T> =
    TimelineLoaderFactory { data, _ ->
        flowOf(factory(data))
    }

@HiddenFromObjC
public inline fun <reified S : Any, T> accountLoader(
    crossinline factory: S.(data: T) -> RemoteLoader<UiTimelineV2>,
): TimelineLoaderFactory<T>
    where T : TimelineSpec.Data, T : TimelineSpec.AccountData =
    TimelineLoaderFactory { data, context ->
        context
            .accountServiceFlow(AccountType.Specific(data.accountKey))
            .map { service ->
                require(service is S) {
                    "Expected ${S::class} for ${data.accountKey}, but got ${service::class}"
                }
                service.factory(data)
            }
    }

@HiddenFromObjC
public data class TimelineTarget<T : TimelineSpec.Data>(
    val spec: TimelineSpec<T>,
    val data: T,
)

@HiddenFromObjC
public data class TimelineCandidate<T : TimelineSpec.Data>(
    val target: TimelineTarget<T>,
    val title: UiText = target.spec.title.asText(),
    val icon: IconType = target.spec.icon,
    val appearancePatch: AppearancePatch? = null,
    val filterConfig: TimelineFilterConfig = TimelineFilterConfig(),
) {
    public val id: String get() = target.spec.itemId(target.data)
}

public fun TimelineCandidate<*>.toUiTimelineTabItem(): UiSourceTimelineTabItem {
    val source = target.toSource(title, icon)
    val presentation =
        appearancePatch?.takeUnless { it == AppearancePatch.EMPTY }?.let {
            TimelinePresentation(appearanceOverride = it.toBag(), filterConfig = filterConfig)
        } ?: TimelinePresentation(filterConfig = filterConfig)
    return UiSourceTimelineTabItem(
        id = id,
        source = source,
        presentation = presentation,
        title = title,
        icon = icon,
        appearancePatch = presentation.appearance,
        enabled = presentation.enabled,
    )
}

@HiddenFromObjC
public fun TimelineCandidate<*>.createLoader(context: TimelineLoaderContext): Flow<RemoteLoader<UiTimelineV2>> =
    target.createLoader(context)

private fun <T : TimelineSpec.Data> TimelineTarget<T>.createLoader(context: TimelineLoaderContext): Flow<RemoteLoader<UiTimelineV2>> =
    spec.loaderFactory.create(data, context)

@HiddenFromObjC
public data class TimelineSpec<T : TimelineSpec.Data>(
    val id: String,
    val title: UiStrings,
    val icon: IconType,
    val serializer: KSerializer<T>,
    val targetId: (data: T) -> String,
    val loaderFactory: TimelineLoaderFactory<T>,
) {
    public fun itemId(data: T): String = "$id:${targetId(data)}"

    public fun target(data: T): TimelineTarget<T> = TimelineTarget(this, data)

    public fun candidate(
        data: T,
        title: UiText = this.title.asText(),
        icon: IconType = this.icon,
        filterConfig: TimelineFilterConfig = TimelineFilterConfig(),
    ): TimelineCandidate<T> =
        TimelineCandidate(
            target = target(data),
            title = title,
            icon = icon,
            filterConfig = filterConfig,
        )

    public fun galleryCandidate(
        data: T,
        title: UiText = this.title.asText(),
        icon: IconType = this.icon,
        filterConfig: TimelineFilterConfig = TimelineFilterConfig(),
    ): TimelineCandidate<T> =
        TimelineCandidate(
            target = target(data),
            title = title,
            icon = icon,
            appearancePatch = galleryAppearancePatch,
            filterConfig = filterConfig,
        )

    @OptIn(ExperimentalSerializationApi::class)
    internal fun source(
        data: T,
        title: UiText = this.title.asText(),
        icon: IconType = this.icon,
    ): TimelineSourceRef =
        TimelineSourceRef(
            id = itemId(data),
            specId = id,
            title = title,
            icon = icon,
            data = ProtoBuf.encodeToHexString(serializer, data),
        )

    @OptIn(ExperimentalSerializationApi::class)
    internal fun decode(encodedData: String): T = ProtoBuf.decodeFromHexString(serializer, encodedData)

    public interface Data

    public interface AccountData : Data {
        public val accountKey: MicroBlogKey
    }

    @Serializable
    public open class AccountBasedData(
        public override val accountKey: MicroBlogKey,
    ) : AccountData

    @Serializable
    public data class AccountResourceData(
        public override val accountKey: MicroBlogKey,
        public val resourceId: String,
    ) : AccountData
}

@HiddenFromObjC
public data class ShortcutSpec(
    val title: UiStrings,
    val icon: UiIcon,
    val target: Target,
) {
    public sealed interface Target {
        public data class Timeline(
            val candidate: TimelineCandidate<*>,
        ) : Target

        public data class Route(
            val route: DeeplinkRoute,
        ) : Target
    }
}

@Single
internal class TimelineResolver(
    data: PlatformRuntimeData,
    accountService: AccountService,
) {
    private val loaderContext = TimelineLoaderContext(accountService)

    private val specs: Map<String, TimelineSpec<out TimelineSpec.Data>> by lazy {
        data.timelineSpecs
            .distinctBy { it.id }
            .associateBy { it.id }
    }

    fun toTabItem(slot: TimelineSlot): UiTimelineTabItem =
        when (val content = slot.content) {
            is TimelineSlotContent.Source -> {
                UiSourceTimelineTabItem(
                    id = slot.id,
                    source = content.source,
                    presentation = slot.presentation,
                    title = slot.title,
                    icon = slot.icon,
                    appearancePatch = slot.presentation.appearance,
                    enabled = slot.presentation.enabled,
                )
            }

            is TimelineSlotContent.Group -> {
                UiGroupTimelineTabItem(
                    id = slot.id,
                    children = content.children.map { toTabItem(it) }.filter { it.enabled },
                    mergePolicy = content.mergePolicy,
                    source = content.source,
                    presentation = slot.presentation,
                    title = slot.title,
                    icon = slot.icon,
                    appearancePatch = slot.presentation.appearance,
                    enabled = slot.presentation.enabled,
                )
            }
        }

    fun toTabItem(candidate: TimelineCandidate<*>): UiSourceTimelineTabItem = candidate.toUiTimelineTabItem()

    fun toTabItem(loaderKey: String): UiTimelineTabItem = toTabItem(decodeTimelineLoaderKey(loaderKey))

    fun toSlot(candidate: TimelineCandidate<*>): TimelineSlot {
        val source = candidate.target.toSource(candidate.title, candidate.icon)
        val presentation =
            candidate.appearancePatch?.takeUnless { it == AppearancePatch.EMPTY }?.let {
                TimelinePresentation(appearanceOverride = it.toBag())
            } ?: TimelinePresentation()
        return source.toSlot(presentation = presentation)
    }

    fun toSlot(item: UiTimelineTabItem): TimelineSlot =
        when (item) {
            is UiSourceTimelineTabItem -> {
                val source =
                    item.source
                        ?: throw IllegalArgumentException("Runtime timeline tab cannot be persisted: ${item.id}")
                source.toSlot(
                    slotId = item.id,
                    presentation = item.presentation ?: TimelinePresentation(),
                )
            }

            is UiGroupTimelineTabItem -> {
                TimelineSlot(
                    id = item.id,
                    content =
                        TimelineSlotContent.Group(
                            children = item.children.map { toSlot(it) },
                            source = item.source,
                            mergePolicy = item.mergePolicy,
                        ),
                    presentation = item.presentation,
                )
            }
        }

    fun resolveLoader(item: UiTimelineTabItem): Flow<RemoteLoader<UiTimelineV2>> =
        when (item) {
            is UiSourceTimelineTabItem -> {
                val source = item.source ?: return flowOf(notSupported())
                resolveLoader(source)
            }

            is UiGroupTimelineTabItem -> {
                resolveGroupLoader(item)
            }
        }

    fun resolveLoader(target: TimelineTarget<out TimelineSpec.Data>): Flow<RemoteLoader<UiTimelineV2>> = target.createLoader()

    @OptIn(ExperimentalSerializationApi::class)
    fun resolveAccountKey(item: UiTimelineTabItem): MicroBlogKey? =
        when (item) {
            is UiSourceTimelineTabItem -> item.source?.let(::resolveAccountKey)
            is UiGroupTimelineTabItem -> null
        }

    @OptIn(ExperimentalSerializationApi::class)
    fun resolveAccountKey(slot: TimelineSlot): MicroBlogKey? =
        when (val content = slot.content) {
            is TimelineSlotContent.Source -> resolveAccountKey(content.source)
            is TimelineSlotContent.Group -> null
        }

    private fun resolveLoader(source: TimelineSourceRef): Flow<RemoteLoader<UiTimelineV2>> = resolveSpec(source).createLoader(source.data)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun resolveGroupLoader(item: UiGroupTimelineTabItem): Flow<RemoteLoader<UiTimelineV2>> =
        flowOf(item.children.filter { it.enabled })
            .distinctUntilChangedByTabIds()
            .flatMapLatest { tabs ->
                if (tabs.isEmpty()) {
                    flowOf(notSupported())
                } else {
                    combine(tabs.map { resolveLoader(it) }) { loaders ->
                        MixedTimelineLoaderFactory.create(
                            loaders = loaders.toList(),
                            mergePolicy = item.mergePolicy,
                        )
                    }
                }
            }

    private fun resolveSpec(source: TimelineSourceRef): TimelineSpec<out TimelineSpec.Data> =
        specs[source.specId]
            ?: throw IllegalArgumentException("No timeline spec found for source ID: ${source.specId}")

    @OptIn(ExperimentalSerializationApi::class)
    private fun resolveAccountKey(source: TimelineSourceRef): MicroBlogKey? {
        val spec = resolveSpec(source)
        val data = spec.decode(source.data)
        return (data as? TimelineSpec.AccountData)?.accountKey
    }

    private fun <T : TimelineSpec.Data> TimelineSpec<T>.createLoader(encodedData: String): Flow<RemoteLoader<UiTimelineV2>> =
        loaderFactory.create(decode(encodedData), loaderContext)

    private fun <T : TimelineSpec.Data> TimelineTarget<T>.createLoader(): Flow<RemoteLoader<UiTimelineV2>> =
        spec.loaderFactory.create(data, loaderContext)
}

private fun <T : TimelineSpec.Data> TimelineTarget<T>.toSource(
    title: UiText,
    icon: IconType,
): TimelineSourceRef =
    spec.source(
        data = data,
        title = title,
        icon = icon,
    )

@Single
internal class TimelinePresenterFactory(
    private val timelineResolver: TimelineResolver,
) {
    fun create(
        item: UiTimelineTabItem,
        isHomeTimeline: Boolean = false,
    ): TimelinePresenter =
        if (item.isSystemHomeMixedTimeline) {
            SystemHomeMixedTimelinePresenter(item.id, isHomeTimeline)
        } else {
            TimelinePresenter(
                tabId = item.id,
                loader = timelineResolver.resolveLoader(item),
                isHomeTimeline = isHomeTimeline,
            )
        }
}

internal object MixedTimelineLoaderFactory {
    private val database: dev.dimension.flare.data.database.cache.CacheDatabase by koinInject()

    fun create(
        loaders: List<RemoteLoader<UiTimelineV2>>,
        mergePolicy: TimelineMergePolicy,
    ): RemoteLoader<UiTimelineV2> {
        val cacheableLoaders = loaders.filterIsInstance<CacheableRemoteLoader<UiTimelineV2>>()
        return if (cacheableLoaders.isEmpty()) {
            notSupported()
        } else {
            dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator(
                database = database,
                mediators = cacheableLoaders,
                mergePolicy = mergePolicy,
            )
        }
    }
}

private fun Flow<List<UiTimelineTabItem>>.distinctUntilChangedByTabIds(): Flow<List<UiTimelineTabItem>> =
    distinctUntilChanged { old, new ->
        old.map { it.id } == new.map { it.id }
    }
