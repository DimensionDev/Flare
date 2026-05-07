package dev.dimension.flare.data.model.tab

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.appearance.toPatch
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.spec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.MixedTimelinePresenter
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
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
public sealed interface TimelineTabItemV2 {
    public val id: String
    public val title: UiText
    public val icon: IconType
    public val appearancePatch: AppearancePatch?
    public val enabled: Boolean

    // for iOS and Compose call sites
    public val key: String get() = id

    public fun createPresenter(): TimelinePresenter
}

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
    override fun createPresenter(): TimelinePresenter = presenterFactory()

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
    override fun createPresenter(): TimelinePresenter =
        MixedTimelinePresenter(
            subTimelinePresenter =
                children
                    .filter { it.enabled }
                    .map { it.createPresenter() },
            mergePolicy = mergePolicy,
        )
}

@Immutable
@Serializable
internal data class TimelineSlot(
    val id: String,
    val content: TimelineSlotContent,
    val presentation: TimelinePresentation = TimelinePresentation(),
) {
    val title: UiText = presentation.titleOverride?.let { UiText.Raw(it) } ?: when (content) {
        is TimelineSlotContent.Source -> content.source.title
        is TimelineSlotContent.Group -> UiStrings.MixedTimeline.asText()
    }

    val icon: IconType = presentation.iconOverride ?: when (content) {
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
) {
    public val appearance: AppearancePatch? by lazy {
        appearanceOverride?.toPatch()
    }
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
) {
    public companion object {
        @OptIn(ExperimentalSerializationApi::class)
        public fun xqtDeviceFollow(accountKey: MicroBlogKey): TimelineSourceRef =
            TimelineSourceRef(
                id = "xqt.device_follow:$accountKey",
                specId = "xqt.device_follow",
                title = UiStrings.Posts.asText(),
                icon = UiIcon.List.asType(),
                data =
                    ProtoBuf.encodeToHexString(
                        TimelineSpec.AccountBasedData.serializer(),
                        TimelineSpec.AccountBasedData(accountKey),
                    ),
            )
    }
}

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
        public data class Timeline(val source: TimelineSourceRef) : Target
        public data class Route(val route: DeeplinkRoute) : Target
    }
}

public class TimelineResolver {
    private val specs: Map<String, TimelineSpec<out TimelineSpec.Data>> by lazy {
        PlatformType.entries
            .flatMap { it.spec.timelineSpecs }
            .distinctBy { it.id }
            .associateBy { it.id }
    }

    internal fun toTabItem(slot: TimelineSlot): TimelineTabItemV2 =
        when (val content = slot.content) {
            is TimelineSlotContent.Source ->
                SourceTimelineTabItemV2.fromSlot(
                    slot = slot,
                    source = content.source,
                    presenterFactory = { resolvePresenter(content.source) },
                )

            is TimelineSlotContent.Group ->
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

            is GroupTimelineTabItemV2 ->
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

    public fun resolvePresenter(source: TimelineSourceRef): TimelinePresenter =
        resolveSpec(source).createPresenter(source.data)

    internal fun resolvePresenter(slot: TimelineSlot): TimelinePresenter = toTabItem(slot).createPresenter()

    @OptIn(ExperimentalSerializationApi::class)
    internal fun resolveAccountKey(slot: TimelineSlot): MicroBlogKey? =
        when (val content = slot.content) {
            is TimelineSlotContent.Source -> resolveAccountKey(content.source)
            is TimelineSlotContent.Group -> null
        }

    @OptIn(ExperimentalSerializationApi::class)
    public fun resolveAccountKey(source: TimelineSourceRef): MicroBlogKey? {
        val spec = resolveSpec(source)
        val data = ProtoBuf.decodeFromHexString(spec.serializer, source.data)
        return (data as? TimelineSpec.AccountData)?.accountKey
    }

    private fun resolveSpec(source: TimelineSourceRef): TimelineSpec<out TimelineSpec.Data> =
        specs[source.specId]
            ?: throw IllegalArgumentException("No timeline spec found for source ID: ${source.specId}")
}
