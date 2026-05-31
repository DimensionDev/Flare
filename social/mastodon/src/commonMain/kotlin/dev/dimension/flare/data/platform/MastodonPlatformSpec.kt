package dev.dimension.flare.data.platform

import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.guest.mastodon.GuestMastodonDataSource
import dev.dimension.flare.data.datasource.guest.mastodon.GuestPublicTimelineRemoteMediator
import dev.dimension.flare.data.datasource.guest.mastodon.GuestTrendsRemoteMediator
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.pleroma.PleromaDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineSpecIds
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.SubscriptionTimelineSpec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.home.mastodon.MastodonBookmarkTimelinePresenter
import dev.dimension.flare.ui.presenter.home.mastodon.MastodonFavouriteTimelinePresenter
import dev.dimension.flare.ui.presenter.home.mastodon.MastodonLocalTimelinePresenter
import dev.dimension.flare.ui.presenter.home.mastodon.MastodonPublicTimelinePresenter
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data object MastodonPlatformSpec : PlatformSpec {
    public override val type: PlatformType = PlatformType.Mastodon
    public override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "Mastodon",
            icon = UiIcon.Mastodon,
        )

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> =
        persistentListOf(
            PlatformDeepLink(
                uriPattern = "https://${accountKey.host}/@{handle}",
                serializer = MastodonProfileDeepLink.serializer(),
                callback = { data -> profileRoute(accountKey, data.handle) },
            ),
            PlatformDeepLink(
                uriPattern = "https://${accountKey.host}/@{handle}/{id}",
                serializer = MastodonPostDeepLink.serializer(),
                callback = { data ->
                    DeeplinkRoute.Status.Detail(
                        accountType = AccountType.Specific(accountKey),
                        statusKey = MicroBlogKey(data.id, accountKey.host),
                    )
                },
            ),
        )

    internal val localTimelineSpec =
        TimelineSpec(
            id = TimelineSpecIds.MASTODON_LOCAL,
            title = UiStrings.MastodonLocal,
            icon = UiIcon.Local.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                MastodonLocalTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    internal val publicTimelineSpec =
        TimelineSpec(
            id = TimelineSpecIds.MASTODON_PUBLIC,
            title = UiStrings.MastodonPublic,
            icon = UiIcon.World.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                MastodonPublicTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    internal val bookmarkTimelineSpec =
        TimelineSpec(
            id = TimelineSpecIds.MASTODON_BOOKMARK,
            title = UiStrings.Bookmark,
            icon = UiIcon.Bookmark.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                MastodonBookmarkTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    internal val favouriteTimelineSpec =
        TimelineSpec(
            id = TimelineSpecIds.MASTODON_FAVOURITE,
            title = UiStrings.Favourite,
            icon = UiIcon.Favourite.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                MastodonFavouriteTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
            CommonTimelineSpecs.discover,
            CommonTimelineSpecs.list,
            localTimelineSpec,
            publicTimelineSpec,
            bookmarkTimelineSpec,
            favouriteTimelineSpec,
        )

    override val subscriptionTimelineSpecs: ImmutableList<SubscriptionTimelineSpec> =
        persistentListOf(
            MastodonSubscriptionTimelineSpec(
                type = SubscriptionType.MASTODON_TRENDS,
                loaderFactory = { host, locale ->
                    GuestTrendsRemoteMediator(
                        host = host,
                        locale = locale,
                    )
                },
            ),
            MastodonSubscriptionTimelineSpec(
                type = SubscriptionType.MASTODON_PUBLIC,
                loaderFactory = { host, locale ->
                    GuestPublicTimelineRemoteMediator(
                        host = host,
                        locale = locale,
                        local = false,
                    )
                },
            ),
            MastodonSubscriptionTimelineSpec(
                type = SubscriptionType.MASTODON_LOCAL,
                loaderFactory = { host, locale ->
                    GuestPublicTimelineRemoteMediator(
                        host = host,
                        locale = locale,
                        local = true,
                    )
                },
            ),
        )

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource {
        val credential = context.credential(MastodonCredential.serializer())
        val credentialFlow = context.credentialFlow(MastodonCredential.serializer())
        return when (credential.forkType) {
            MastodonCredential.ForkType.Mastodon -> {
                MastodonDataSource(
                    accountKey = context.accountKey,
                    instance = credential.instance,
                    credentialFlow = credentialFlow,
                )
            }

            MastodonCredential.ForkType.Pleroma -> {
                PleromaDataSource(
                    accountKey = context.accountKey,
                    instance = credential.instance,
                    credentialFlow = credentialFlow,
                )
            }
        }
    }

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource =
        GuestMastodonDataSource(
            host = host,
            locale = locale,
        )
}

private class MastodonSubscriptionTimelineSpec(
    override val type: SubscriptionType,
    private val loaderFactory: (host: String, locale: String) -> CacheableRemoteLoader<UiTimelineV2>,
) : SubscriptionTimelineSpec {
    override suspend fun isAvailable(
        host: String,
        locale: String,
    ): Boolean =
        runCatching {
            createLoader(host, locale).load(1, PagingRequest.Refresh)
            true
        }.getOrDefault(false)

    override fun createLoader(
        host: String,
        locale: String,
    ): CacheableRemoteLoader<UiTimelineV2> = loaderFactory(host, locale)
}

@Serializable
private data class MastodonProfileDeepLink(
    val handle: String,
)

@Serializable
private data class MastodonPostDeepLink(
    val handle: String,
    val id: String,
)

private fun profileRoute(
    accountKey: MicroBlogKey,
    handle: String,
): DeeplinkRoute {
    val target =
        if (handle.contains('@')) {
            MicroBlogKey.valueOf(handle)
        } else {
            MicroBlogKey(handle, accountKey.host)
        }
    return DeeplinkRoute.Profile.UserNameWithHost(
        accountType = AccountType.Specific(accountKey),
        userName = target.id,
        host = target.host,
    )
}
