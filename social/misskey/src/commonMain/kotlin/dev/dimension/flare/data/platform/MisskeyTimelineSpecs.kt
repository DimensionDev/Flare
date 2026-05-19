package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.timeline.AccountTimelineSpec
import dev.dimension.flare.data.datasource.microblog.timeline.CommonTimelineSpecs
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public interface MisskeyTimelineDataSource {
    public fun favouriteTimelineLoader(): RemoteLoader<UiTimelineV2>

    public fun hybridTimelineLoader(): RemoteLoader<UiTimelineV2>

    public fun localTimelineLoader(): RemoteLoader<UiTimelineV2>

    public fun publicTimelineLoader(): RemoteLoader<UiTimelineV2>

    public fun antennasTimelineLoader(id: String): RemoteLoader<UiTimelineV2>

    public fun channelTimelineLoader(id: String): RemoteLoader<UiTimelineV2>
}

public object MisskeyTimelineSpecs {
    public val favourite: AccountTimelineSpec<TimelineSpec.AccountBasedData> =
        AccountTimelineSpec(
            id = "misskey.favourite",
            title = UiStrings.Favourite,
            icon = UiIcon.Favourite.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            stableKeyFactory = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is MisskeyTimelineDataSource)
                service.favouriteTimelineLoader()
            },
        )

    public val hybrid: AccountTimelineSpec<TimelineSpec.AccountBasedData> =
        AccountTimelineSpec(
            id = "misskey.hybrid",
            title = UiStrings.Social,
            icon = UiIcon.Featured.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            stableKeyFactory = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is MisskeyTimelineDataSource)
                service.hybridTimelineLoader()
            },
        )

    public val local: AccountTimelineSpec<TimelineSpec.AccountBasedData> =
        AccountTimelineSpec(
            id = "misskey.local",
            title = UiStrings.MastodonLocal,
            icon = UiIcon.Local.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            stableKeyFactory = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is MisskeyTimelineDataSource)
                service.localTimelineLoader()
            },
        )

    public val global: AccountTimelineSpec<TimelineSpec.AccountBasedData> =
        AccountTimelineSpec(
            id = "misskey.global",
            title = UiStrings.MastodonPublic,
            icon = UiIcon.World.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            stableKeyFactory = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is MisskeyTimelineDataSource)
                service.publicTimelineLoader()
            },
        )

    public val antenna: AccountTimelineSpec<TimelineSpec.AccountResourceData> =
        AccountTimelineSpec(
            id = "misskey.antenna",
            title = UiStrings.Antenna,
            icon = UiIcon.Rss.asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            stableKeyFactory = { "${it.accountKey}:${it.resourceId}" },
            loaderFactory = { service, data ->
                require(service is MisskeyTimelineDataSource)
                service.antennasTimelineLoader(data.resourceId)
            },
        )

    public val channel: AccountTimelineSpec<TimelineSpec.AccountResourceData> =
        AccountTimelineSpec(
            id = "misskey.channel",
            title = UiStrings.Channel,
            icon = UiIcon.Channel.asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            stableKeyFactory = { "${it.accountKey}:${it.resourceId}" },
            loaderFactory = { service, data ->
                require(service is MisskeyTimelineDataSource)
                service.channelTimelineLoader(data.resourceId)
            },
        )

    public val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
            CommonTimelineSpecs.discover,
            CommonTimelineSpecs.list,
            favourite,
            hybrid,
            local,
            global,
            antenna,
            channel,
        )
}
