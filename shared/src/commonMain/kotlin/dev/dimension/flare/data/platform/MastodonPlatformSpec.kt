package dev.dimension.flare.data.platform

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.datasource.guest.mastodon.GuestMastodonDataSource
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.pleroma.PleromaDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineSpecIds
import dev.dimension.flare.data.network.mastodon.MastodonInstanceService
import dev.dimension.flare.data.network.mastodon.MastodonPlatformDetector
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
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

public data object MastodonPlatformSpec : PlatformSpec {
    public override val type: PlatformType = PlatformType.Mastodon
    public override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "Mastodon",
            icon = UiIcon.Mastodon,
        )
    override val detector: PlatformDetector = MastodonPlatformDetector

    override fun agreementUrl(host: String): String? = "https://$host/about"

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

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata = MastodonInstanceService("https://$host/").instance().render()

    override fun restoreAccount(
        accountKey: MicroBlogKey,
        credentialJson: String,
    ): UiAccount {
        val credential = credentialJson.decodeJson<UiAccount.Mastodon.Credential>()
        return UiAccount.Mastodon(
            accountKey = accountKey,
            forkType = credential.forkType,
            instance = credential.instance,
            nodeType = credential.nodeType,
        )
    }

    override fun createDataSource(account: UiAccount): MicroblogDataSource {
        require(account is UiAccount.Mastodon) {
            "Expected Mastodon account for ${type.name}, got ${account.platformType.name}"
        }
        return when (account.forkType) {
            UiAccount.Mastodon.Credential.ForkType.Mastodon -> {
                MastodonDataSource(
                    accountKey = account.accountKey,
                    instance = account.instance,
                )
            }

            UiAccount.Mastodon.Credential.ForkType.Pleroma -> {
                PleromaDataSource(
                    accountKey = account.accountKey,
                    instance = account.instance,
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
