package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.tab.AccountTimelineSpec
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal data object MastodonPlatformSpec : PlatformSpec {
    override val type = MastodonSocialPlatformSpec.type
    override val timelineSpecs = MastodonSocialPlatformSpec.timelineSpecs
    override val metadata = MastodonSocialPlatformSpec.metadata
    override val detector = MastodonSocialPlatformSpec.detector

    override fun agreementUrl(host: String): String? = MastodonSocialPlatformSpec.agreementUrl(host)

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
        MastodonSocialPlatformSpec.deepLinkPatterns(host)

    internal val localTimelineSpec =
        AccountTimelineSpec(
            id = "mastodon.local",
            title = UiStrings.MastodonLocal,
            icon = UiIcon.Local.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is MastodonTimelineDataSource)
                service.publicTimelineLoader(local = true)
            },
        )

    internal val publicTimelineSpec =
        AccountTimelineSpec(
            id = "mastodon.public",
            title = UiStrings.MastodonPublic,
            icon = UiIcon.World.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is MastodonTimelineDataSource)
                service.publicTimelineLoader(local = false)
            },
        )

    internal val bookmarkTimelineSpec =
        AccountTimelineSpec(
            id = "mastodon.bookmark",
            title = UiStrings.Bookmark,
            icon = UiIcon.Bookmark.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is MastodonTimelineDataSource)
                service.bookmarkTimelineLoader()
            },
        )

    internal val favouriteTimelineSpec =
        AccountTimelineSpec(
            id = "mastodon.favourite",
            title = UiStrings.Favourite,
            icon = UiIcon.Favourite.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is MastodonTimelineDataSource)
                service.favouriteTimelineLoader()
            },
        )

    override val legacyTimelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
            CommonTimelineSpecs.discover,
            CommonTimelineSpecs.list,
            localTimelineSpec,
            publicTimelineSpec,
            bookmarkTimelineSpec,
            favouriteTimelineSpec,
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata = MastodonSocialPlatformSpec.instanceMetadata(host)

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource =
        MastodonSocialPlatformSpec.guestDataSource(
            host = host,
            locale = locale,
        )
}
