package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.ListDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.model.tab.TimelineLoaderFactory
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineSpecIds
import dev.dimension.flare.data.model.tab.accountLoader
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public object CommonTimelineSpecs {
    public val home: TimelineSpec<TimelineSpec.AccountBasedData> =
        TimelineSpec(
            id = TimelineSpecIds.COMMON_HOME,
            title = UiStrings.Home,
            icon = UiIcon.Home.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<MicroblogDataSource, TimelineSpec.AccountBasedData> {
                    homeTimeline()
                },
        )

    public val guestHome: TimelineSpec<GuestHomeData> =
        TimelineSpec(
            id = TimelineSpecIds.COMMON_GUEST_HOME,
            title = UiStrings.Home,
            icon = UiIcon.Home.asType(),
            serializer = GuestHomeData.serializer(),
            targetId = { it.host },
            loaderFactory =
                TimelineLoaderFactory { data, context ->
                    context
                        .accountServiceFlow(AccountType.GuestHost(data.host))
                        .map { service -> service.homeTimeline() }
                },
        )

    public val discover: TimelineSpec<TimelineSpec.AccountBasedData> =
        TimelineSpec(
            id = TimelineSpecIds.COMMON_DISCOVER,
            title = UiStrings.Discover,
            icon = UiIcon.Search.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<MicroblogDataSource, TimelineSpec.AccountBasedData> {
                    discoverStatuses()
                },
        )

    public val list: TimelineSpec<TimelineSpec.AccountResourceData> =
        TimelineSpec(
            id = TimelineSpecIds.COMMON_LIST,
            title = UiStrings.List,
            icon = UiIcon.List.asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            targetId = { "${it.accountKey}:${it.resourceId}" },
            loaderFactory =
                accountLoader<ListDataSource, TimelineSpec.AccountResourceData> {
                    listTimeline(listId = it.resourceId)
                },
        )

    @Serializable
    public data class GuestHomeData(
        val host: String,
    ) : TimelineSpec.Data
}

public fun UiList.List.toTimelineCandidate(accountKey: MicroBlogKey): TimelineCandidate<*> =
    CommonTimelineSpecs.list
        .candidate(
            data = TimelineSpec.AccountResourceData(accountKey, id),
            title = UiText.Raw(title),
            icon = avatar?.let { IconType.Url(it) } ?: UiIcon.List.asType(),
        )
