package dev.dimension.flare.data.model.tab

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.data.model.appearance.toBag
import dev.dimension.flare.data.model.appearance.toPatch
import dev.dimension.flare.data.model.appearance.withPatch
import dev.dimension.flare.data.platform.RssTimelineSpecs
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.MixedTimelinePresenter
import dev.dimension.flare.ui.presenter.home.SystemHomeMixedTimelinePresenter
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import dev.dimension.flare.ui.presenter.home.xqt.XQTDeviceFollowTimelinePresenter
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf

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
public sealed interface TimelineTabItemV2 {
    public val id: String
    public val title: UiText
    public val icon: IconType
    public val appearancePatch: AppearancePatch?
    public val enabled: Boolean
    public val filterConfig: TimelineFilterConfig

    // for iOS and Compose call sites
    public val key: String get() = id

    public fun createPresenter(): TimelinePresenter

    public fun withPresentationOverrides(
        title: String,
        icon: IconType,
        appearancePatch: AppearancePatch? = this.appearancePatch,
        enabled: Boolean = this.enabled,
        filterConfig: TimelineFilterConfig = this.filterConfig,
    ): TimelineTabItemV2
}

public fun TimelineTabItemV2.resolveTimelineAppearance(base: TimelineAppearance): TimelineAppearance = base.withPatch(appearancePatch)

@Immutable
public class SourceTimelineTabItemV2 private constructor(
    override val id: String,
    internal val source: TimelineSourceRef?,
    internal val presentation: TimelinePresentation?,
    override val title: UiText,
    override val icon: IconType,
    override val appearancePatch: AppearancePatch?,
    override val enabled: Boolean,
    private val presenterFactory: () -> TimelinePresenter,
) : TimelineTabItemV2 {
    override val filterConfig: TimelineFilterConfig
        get() = presentation?.filterConfig ?: TimelineFilterConfig()

    override fun createPresenter(): TimelinePresenter = presenterFactory().also { it.bindTimelineTabItemId(id) }

    override fun withPresentationOverrides(
        title: String,
        icon: IconType,
        appearancePatch: AppearancePatch?,
        enabled: Boolean,
        filterConfig: TimelineFilterConfig,
    ): TimelineTabItemV2 {
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
        return SourceTimelineTabItemV2(
            id = id,
            source = source,
            presentation = updatedPresentation,
            title = UiText.Raw(title),
            icon = icon,
            appearancePatch = updatedPresentation.appearance,
            enabled = updatedPresentation.enabled,
            presenterFactory = presenterFactory,
        )
    }

    public companion object {
        public fun runtime(
            id: String,
            title: UiText,
            icon: IconType,
            appearancePatch: AppearancePatch? = null,
            enabled: Boolean = true,
            createPresenter: () -> TimelinePresenter,
        ): SourceTimelineTabItemV2 =
            SourceTimelineTabItemV2(
                id = id,
                source = null,
                presentation = null,
                title = title,
                icon = icon,
                appearancePatch = appearancePatch,
                enabled = enabled,
                presenterFactory = createPresenter,
            )

        public fun xqtDeviceFollow(accountKey: MicroBlogKey): SourceTimelineTabItemV2 =
            runtime(
                id = "${TimelineSpecIds.XQT_DEVICE_FOLLOW}:$accountKey",
                title = UiStrings.Posts.asText(),
                icon = UiIcon.List.asType(),
                createPresenter = {
                    XQTDeviceFollowTimelinePresenter(
                        AccountType.Specific(accountKey),
                    )
                },
            )

        internal fun fromSlot(
            slot: TimelineSlot,
            source: TimelineSourceRef,
            presenterFactory: () -> TimelinePresenter,
        ): SourceTimelineTabItemV2 =
            SourceTimelineTabItemV2(
                id = slot.id,
                source = source,
                presentation = slot.presentation,
                title = slot.title,
                icon = slot.icon,
                appearancePatch = slot.presentation.appearance,
                enabled = slot.presentation.enabled,
                presenterFactory = presenterFactory,
            )

        internal fun fromSource(
            source: TimelineSourceRef,
            presenterFactory: () -> TimelinePresenter,
        ): SourceTimelineTabItemV2 =
            SourceTimelineTabItemV2(
                id = source.id,
                source = source,
                presentation = null,
                title = source.title,
                icon = source.icon,
                appearancePatch = null,
                enabled = true,
                presenterFactory = presenterFactory,
            )
    }
}

@Immutable
public class GroupTimelineTabItemV2 internal constructor(
    override val id: String,
    public val children: List<TimelineTabItemV2>,
    public val mergePolicy: TimelineMergePolicy,
    internal val source: GroupSource,
    internal val presentation: TimelinePresentation,
    override val title: UiText,
    override val icon: IconType,
    override val appearancePatch: AppearancePatch?,
    override val enabled: Boolean,
) : TimelineTabItemV2 {
    override val filterConfig: TimelineFilterConfig
        get() = presentation.filterConfig

    override fun createPresenter(): TimelinePresenter =
        when (source) {
            GroupSource.SystemHome -> {
                SystemHomeMixedTimelinePresenter(id = id)
            }

            GroupSource.Manual -> {
                MixedTimelinePresenter(id = id)
            }
        }

    override fun withPresentationOverrides(
        title: String,
        icon: IconType,
        appearancePatch: AppearancePatch?,
        enabled: Boolean,
        filterConfig: TimelineFilterConfig,
    ): TimelineTabItemV2 {
        val updatedPresentation =
            presentation.withOverrides(
                titleOverride = title,
                iconOverride = icon,
                appearancePatch = appearancePatch,
                enabled = enabled,
                filterConfig = filterConfig,
            )
        return GroupTimelineTabItemV2(
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

public val TimelineTabItemV2.isSystemHomeMixedTimeline: Boolean
    get() = this is GroupTimelineTabItemV2 && source == GroupSource.SystemHome

internal fun TimelineTabItemV2.findById(id: String): TimelineTabItemV2? =
    when (this) {
        is SourceTimelineTabItemV2 -> takeIf { this.id == id }
        is GroupTimelineTabItemV2 -> takeIf { this.id == id } ?: children.firstNotNullOfOrNull { it.findById(id) }
    }

internal fun List<TimelineTabItemV2>.findById(id: String): TimelineTabItemV2? = firstNotNullOfOrNull { it.findById(id) }

public fun List<TimelineTabItemV2>.withSystemHomeMixedTimelineEnabled(
    enabled: Boolean,
    mergePolicy: TimelineMergePolicy? = null,
    filterConfig: TimelineFilterConfig? = null,
): List<TimelineTabItemV2> {
    val existingGroup = filterIsInstance<GroupTimelineTabItemV2>().firstOrNull { it.source == GroupSource.SystemHome }
    val tabsWithoutSystemGroup = filterNot { it.isSystemHomeMixedTimeline }
    if (!enabled || tabsWithoutSystemGroup.size < 2) {
        return tabsWithoutSystemGroup
    }

    val systemGroup =
        GroupTimelineTabItemV2(
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
public data class TimelineSlot(
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
public data class TimelinePresentation internal constructor(
    val titleOverride: String? = null,
    val iconOverride: IconType? = null,
    private val appearanceOverride: AppearanceBag? = null,
    val enabled: Boolean = true,
    val filterConfig: TimelineFilterConfig = TimelineFilterConfig(),
) {
    public val appearance: AppearancePatch? by lazy {
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
public sealed interface TimelineSlotContent {
    @Immutable
    @Serializable
    @SerialName("source")
    public data class Source(
        @SerialName("target")
        public val source: TimelineSourceRef,
    ) : TimelineSlotContent

    @Immutable
    @Serializable
    @SerialName("group")
    public data class Group(
        public val children: List<TimelineSlot> = emptyList(),
        public val source: GroupSource = GroupSource.Manual,
        public val mergePolicy: TimelineMergePolicy = TimelineMergePolicy.Time,
    ) : TimelineSlotContent
}

@Immutable
@Serializable
public enum class GroupSource {
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
public data class TimelineSourceRef(
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

public data class TimelineSpec<T : TimelineSpec.Data>(
    val id: String,
    val title: UiStrings,
    val icon: IconType,
    val serializer: KSerializer<T>,
    val targetId: (data: T) -> String,
    private val presenterFactory: (data: T) -> TimelinePresenter,
) {
    public fun itemId(data: T): String = "$id:${targetId(data)}"

    @OptIn(ExperimentalSerializationApi::class)
    internal fun target(
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

    public fun tabItem(
        data: T,
        title: UiText = this.title.asText(),
        icon: IconType = this.icon,
    ): SourceTimelineTabItemV2 {
        val source =
            target(
                data = data,
                title = title,
                icon = icon,
            )
        return SourceTimelineTabItemV2.fromSource(source) {
            createPresenter(source.data)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    public fun createPresenter(encodedData: String): TimelinePresenter {
        val data = ProtoBuf.decodeFromHexString(serializer, encodedData)
        return presenterFactory(data)
    }

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

public data class ShortcutSpec(
    val title: UiStrings,
    val icon: UiIcon,
    val target: Target,
) {
    public sealed interface Target {
        public data class Timeline(
            val tabItem: SourceTimelineTabItemV2,
        ) : Target

        public data class Route(
            val route: DeeplinkRoute,
        ) : Target
    }
}

public class TimelineResolver internal constructor(
    private val platformRegistry: PlatformRegistry,
) {
    private val specs: Map<String, TimelineSpec<out TimelineSpec.Data>> by lazy {
        (
            platformRegistry.all.flatMap { it.timelineSpecs } +
                RssTimelineSpecs.timelineSpecs
        ).distinctBy { it.id }
            .associateBy { it.id }
    }

    internal fun toTabItem(slot: TimelineSlot): TimelineTabItemV2 =
        when (val content = slot.content) {
            is TimelineSlotContent.Source -> {
                SourceTimelineTabItemV2.fromSlot(
                    slot = slot,
                    source = content.source,
                    presenterFactory = { resolvePresenter(content.source) },
                )
            }

            is TimelineSlotContent.Group -> {
                GroupTimelineTabItemV2(
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

    public fun toTabItem(source: TimelineSourceRef): SourceTimelineTabItemV2 =
        SourceTimelineTabItemV2.fromSource(
            source = source,
            presenterFactory = { resolvePresenter(source) },
        )

    internal fun toSlot(item: TimelineTabItemV2): TimelineSlot =
        when (item) {
            is SourceTimelineTabItemV2 -> {
                val source =
                    item.source
                        ?: throw IllegalArgumentException("Runtime timeline tab cannot be persisted: ${item.id}")
                source.toSlot(
                    slotId = item.id,
                    presentation = item.presentation ?: TimelinePresentation(),
                )
            }

            is GroupTimelineTabItemV2 -> {
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

    private fun resolvePresenter(source: TimelineSourceRef): TimelinePresenter = resolveSpec(source).createPresenter(source.data)

    @OptIn(ExperimentalSerializationApi::class)
    public fun resolveAccountKey(item: TimelineTabItemV2): MicroBlogKey? =
        when (item) {
            is SourceTimelineTabItemV2 -> item.source?.let(::resolveAccountKey)
            is GroupTimelineTabItemV2 -> null
        }

    @OptIn(ExperimentalSerializationApi::class)
    internal fun resolveAccountKey(slot: TimelineSlot): MicroBlogKey? =
        when (val content = slot.content) {
            is TimelineSlotContent.Source -> resolveAccountKey(content.source)
            is TimelineSlotContent.Group -> null
        }

    @OptIn(ExperimentalSerializationApi::class)
    private fun resolveAccountKey(source: TimelineSourceRef): MicroBlogKey? {
        val spec = resolveSpec(source)
        val data = ProtoBuf.decodeFromHexString(spec.serializer, source.data)
        return (data as? TimelineSpec.AccountData)?.accountKey
    }

    private fun resolveSpec(source: TimelineSourceRef): TimelineSpec<out TimelineSpec.Data> =
        specs[source.specId]
            ?: throw IllegalArgumentException("No timeline spec found for source ID: ${source.specId}")
}
