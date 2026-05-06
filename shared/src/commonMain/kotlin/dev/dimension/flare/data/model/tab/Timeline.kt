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
public data class TabSettingsV2(
    val homeSlots: List<TimelineSlot> = emptyList(),
)

@Immutable
public data class UiTimelineItem(
    val id: String,
    val title: UiText,
    val icon: IconType,
    val appearancePatch: AppearancePatch? = null,
    val createPresenter: () -> TimelinePresenter,
)

@Immutable
@Serializable
public data class TimelineSlot(
    val id: String,
    val content: TimelineSlotContent,
    val presentation: TimelinePresentation = TimelinePresentation(),
) {
    val title: UiText = presentation.titleOverride?.let { UiText.Raw(it) } ?: when (content) {
        is TimelineSlotContent.Source -> content.target.title
        is TimelineSlotContent.Group -> UiStrings.MixedTimeline.asText()
    }

    val icon: IconType = presentation.iconOverride ?: when (content) {
        is TimelineSlotContent.Source -> content.target.icon
        is TimelineSlotContent.Group -> UiIcon.Rss.asType()
    }

    // for iOS and Compose call sites
    public val key: String get() = id
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
public sealed interface TimelineSlotContent {
    @Immutable
    @Serializable
    @SerialName("source")
    public data class Source(
        val target: TimelineTargetRef,
    ) : TimelineSlotContent

    @Immutable
    @Serializable
    @SerialName("group")
    public data class Group(
        val children: List<TimelineSlot> = emptyList(),
        val source: GroupSource = GroupSource.Manual,
        val mergePolicy: TimelineMergePolicy = TimelineMergePolicy.Time,
    ) : TimelineSlotContent
}

@Immutable
@Serializable
public enum class GroupSource {
    Manual,
    SystemHome
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
public data class TimelineTargetRef(
    val id: String,
    val specId: String,
    val title: UiText,
    val icon: IconType,
    val data: String,
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
    ): TimelineTargetRef =
        TimelineTargetRef(
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
        public data class Timeline(val target: TimelineTargetRef) : Target
        public data class Route(val route: DeeplinkRoute) : Target
    }
}

public class TimelineResolver {

    public fun toUi(slot: TimelineSlot): UiTimelineItem {
        return UiTimelineItem(
            id = slot.id,
            title = slot.title,
            icon = slot.icon,
            appearancePatch = slot.presentation.appearance,
            createPresenter = { resolvePresenter(slot) },
        )
    }

    public fun toUi(ref: TimelineTargetRef): UiTimelineItem {
        return UiTimelineItem(
            id = ref.id,
            title = ref.title,
            icon = ref.icon,
            createPresenter = { resolvePresenter(ref) },
        )
    }

    public fun resolvePresenter(ref: TimelineTargetRef): TimelinePresenter {
        val spec = PlatformType.entries
            .flatMap { it.spec.timelineSpecs }
            .distinctBy { it.id }
            .find { it.id == ref.specId }
            ?: throw IllegalArgumentException("No timeline spec found for source ID: ${ref.specId}")
        return spec.createPresenter(ref.data)
    }

    public fun resolvePresenter(slot: TimelineSlot): TimelinePresenter {
        return when (val content = slot.content) {
            is TimelineSlotContent.Source -> {
                resolvePresenter(content.target)
            }

            is TimelineSlotContent.Group -> {
                MixedTimelinePresenter(
                    subTimelinePresenter =
                        content.children.map {
                            resolvePresenter(it)
                        },
                    mergePolicy = content.mergePolicy,
                )
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    public fun resolveAccountKey(slot: TimelineSlot): MicroBlogKey? {
        return when (val content = slot.content) {
            is TimelineSlotContent.Source -> {
                val spec = PlatformType.entries
                    .flatMap { it.spec.timelineSpecs }
                    .distinctBy { it.id }
                    .find { it.id == content.target.specId } ?: return null
                val data = ProtoBuf.decodeFromHexString(spec.serializer, content.target.data)
                if (data is TimelineSpec.AccountData) {
                    data.accountKey
                } else {
                    null
                }
            }

            is TimelineSlotContent.Group -> {
                // For group slots, we can decide on a policy. Here we return null, but we could also check child slots.
                null
            }
        }
    }

}

//public fun TimelineTargetRef.resolvePresenter(): TimelinePresenter {
//    return resolvePresenterForTimelineTargetRef(this, PlatformType.entries.flatMap { it.spec.timelineSpecs }.distinctBy { it.id })
//}
//
//public fun TimelineTargetRef.resolveSpec(): TimelineSpec<out TimelineSpec.Data> {
//    val spec = PlatformType.entries
//        .flatMap { it.spec.timelineSpecs }
//        .distinctBy { it.id }
//        .find { it.id == specId }
//        ?: throw IllegalArgumentException("No timeline spec found for source ID: $specId")
//    return spec
//}
//
//public fun TimelineTargetRef.resolveAccountKey(): MicroBlogKey? {
//    val spec = resolveSpec()
//    return spec.resolveAccountKey(this)
//}
//
//public fun resolvePresenterForTimelineTargetRef(
//    targetRef: TimelineTargetRef,
//    timelineSpecs: List<TimelineSpec<out TimelineSpec.Data>>,
//): TimelinePresenter {
//    val spec = timelineSpecs.find { it.id == targetRef.specId }
//        ?: throw IllegalArgumentException("No timeline spec found for source ID: ${targetRef.specId}")
//    return spec.createPresenter(targetRef.data)
//}
//
//public fun resolvePresenterForTimelineSlot(
//    slot: TimelineSlot,
//    timelineSpecs: List<TimelineSpec<out TimelineSpec.Data>>,
//): TimelinePresenter {
//    return when (val content = slot.content) {
//        is TimelineSlotContent.Source -> {
//            resolvePresenterForTimelineTargetRef(content.target, timelineSpecs)
//        }
//
//        is TimelineSlotContent.Group -> {
//            MixedTimelinePresenter(
//                subTimelinePresenter =
//                    content.children.map {
//                        resolvePresenterForTimelineSlot(it, timelineSpecs)
//                    },
//                mergePolicy = content.mergePolicy,
//            )
//        }
//    }
//}
