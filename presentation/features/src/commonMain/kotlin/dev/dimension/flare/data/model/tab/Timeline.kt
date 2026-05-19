package dev.dimension.flare.data.model.tab

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineRef
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec as SocialTimelineSpec
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.data.model.appearance.toBag
import dev.dimension.flare.data.model.appearance.withPatch
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.TimelinePresenter

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
    public val ref: TimelineRef<out SocialTimelineSpec.Data>?,
    internal val presentation: TimelinePresentation?,
    override val title: UiText,
    override val icon: IconType,
    override val appearancePatch: AppearancePatch?,
    override val enabled: Boolean,
    internal val runtimePresenterFactory: (() -> TimelinePresenter)?,
) : TimelineTabItemV2 {
    override val filterConfig: TimelineFilterConfig
        get() = presentation?.filterConfig ?: TimelineFilterConfig()

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
            ref = ref,
            presentation = updatedPresentation,
            title = UiText.Raw(title),
            icon = icon,
            appearancePatch = updatedPresentation.appearance,
            enabled = updatedPresentation.enabled,
            runtimePresenterFactory = runtimePresenterFactory,
        )
    }

    public companion object {
        internal fun runtime(
            id: String,
            title: UiText,
            icon: IconType,
            appearancePatch: AppearancePatch? = null,
            enabled: Boolean = true,
            runtimePresenterFactory: () -> TimelinePresenter,
        ): SourceTimelineTabItemV2 =
            SourceTimelineTabItemV2(
                id = id,
                source = null,
                ref = null,
                presentation = null,
                title = title,
                icon = icon,
                appearancePatch = appearancePatch,
                enabled = enabled,
                runtimePresenterFactory = runtimePresenterFactory,
            )

        internal fun fromSlot(
            slot: TimelineSlot,
            source: TimelineSourceRef,
            ref: TimelineRef<out SocialTimelineSpec.Data>? = null,
        ): SourceTimelineTabItemV2 =
            SourceTimelineTabItemV2(
                id = slot.id,
                source = source,
                ref = ref,
                presentation = slot.presentation,
                title = slot.title,
                icon = slot.icon,
                appearancePatch = slot.presentation.appearance,
                enabled = slot.presentation.enabled,
                runtimePresenterFactory = null,
            )

        internal fun fromSource(
            source: TimelineSourceRef,
            ref: TimelineRef<out SocialTimelineSpec.Data>? = null,
        ): SourceTimelineTabItemV2 =
            SourceTimelineTabItemV2(
                id = source.id,
                source = source,
                ref = ref,
                presentation = null,
                title = source.title,
                icon = source.icon,
                appearancePatch = null,
                enabled = true,
                runtimePresenterFactory = null,
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
