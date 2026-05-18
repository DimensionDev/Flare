package dev.dimension.flare.data.model.tab

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.data.model.appearance.toBag
import dev.dimension.flare.data.model.appearance.withPatch
import dev.dimension.flare.data.platform.RssTimelineSpecs
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.SocialPlatformRegistry
import dev.dimension.flare.model.defaultSocialPlatformRegistry
import dev.dimension.flare.model.platformSpecs
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.MixedTimelinePresenter
import dev.dimension.flare.ui.presenter.home.SystemHomeMixedTimelinePresenter
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf

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
    public val source: TimelineSourceRef?,
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

public data class TimelineSpec<T : TimelineSpec.Data>(
    val id: String,
    val title: UiStrings,
    val icon: IconType,
    val serializer: KSerializer<T>,
    val targetId: (data: T) -> String,
    private val presenterFactory: (data: T) -> TimelinePresenter,
) {
    @OptIn(ExperimentalSerializationApi::class)
    public fun target(
        data: T,
        title: UiText = this.title.asText(),
        icon: IconType = this.icon,
    ): TimelineSourceRef =
        TimelineSourceRef(
            id = "$id:${targetId(data)}",
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
            val source: TimelineSourceRef,
        ) : Target

        public data class Route(
            val route: DeeplinkRoute,
        ) : Target
    }
}

public class TimelineResolver internal constructor(
    private val platformRegistry: SocialPlatformRegistry = defaultSocialPlatformRegistry,
) {
    private val specs: Map<String, TimelineSpec<out TimelineSpec.Data>> by lazy {
        (
            platformRegistry.platformSpecs
                .flatMap { it.timelineSpecs } +
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
