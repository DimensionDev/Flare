package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineSpecIds
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.network.bluesky.BlueskyPlatformDetector
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyBookmarkTimelinePresenter
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyFeedTimelinePresenter
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data object BlueskyPlatformSpec : PlatformSpec {
    public override val type: PlatformType = PlatformType.Bluesky
    public override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "Bluesky",
            icon = UiIcon.Bluesky,
        )
    override val detector: PlatformDetector = BlueskyPlatformDetector

    override fun agreementUrl(host: String): String? = "https://bsky.social/about/support/tos"

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> =
        buildList {
            add(profileDeepLink(accountKey, "https://${accountKey.host}/profile/{handle}"))
            add(postDeepLink(accountKey, "https://${accountKey.host}/profile/{handle}/post/{id}"))
            if (accountKey.host == "bsky.social") {
                add(profileDeepLink(accountKey, "https://bsky.app/profile/{handle}"))
                add(postDeepLink(accountKey, "https://bsky.app/profile/{handle}/post/{id}"))
            }
        }.toImmutableList()

    internal val bookmarkTimelineSpec =
        TimelineSpec(
            id = TimelineSpecIds.BLUESKY_BOOKMARK,
            title = UiStrings.Bookmark,
            icon = UiIcon.Bookmark.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                BlueskyBookmarkTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    internal val feedTimelineSpec =
        TimelineSpec(
            id = TimelineSpecIds.BLUESKY_FEED,
            title = UiStrings.Feeds,
            icon = UiIcon.Feeds.asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            targetId = { "${it.accountKey}:${it.resourceId}" },
            presenterFactory = {
                BlueskyFeedTimelinePresenter(
                    accountType = AccountType.Specific(it.accountKey),
                    uri = it.resourceId,
                )
            },
        )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
            CommonTimelineSpecs.list,
            bookmarkTimelineSpec,
            feedTimelineSpec,
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${type.name} is not supported yet")

    override suspend fun recommendInstances(): List<RecommendedInstance> =
        listOf(
            RecommendedInstance(
                instance =
                    UiInstance(
                        name = metadata.displayName,
                        description =
                            "The web. Email. RSS feeds. XMPP chats. " +
                                "What all these technologies had in common is they allowed people to freely interact " +
                                "and create content, without a single intermediary.",
                        iconUrl = null,
                        domain = "bsky.social",
                        type = type,
                        bannerUrl = null,
                        usersCount = 0,
                    ),
                priority = 70,
            ),
        )

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
        BlueskyDataSource(
            accountKey = context.accountKey,
            credentialFlow = context.credentialFlow(BlueskyCredential.serializer()),
            updateCredential = { credential ->
                context.updateCredential(
                    serializer = BlueskyCredential.serializer(),
                    credential = credential,
                )
            },
        )

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")

    private fun profileDeepLink(
        accountKey: MicroBlogKey,
        uriPattern: String,
    ): PlatformDeepLink<BlueskyProfileDeepLink> =
        PlatformDeepLink(
            uriPattern = uriPattern,
            serializer = BlueskyProfileDeepLink.serializer(),
            callback = { data ->
                profileRoute(
                    accountKey = accountKey,
                    handle = data.handle,
                )
            },
        )

    private fun postDeepLink(
        accountKey: MicroBlogKey,
        uriPattern: String,
    ): PlatformDeepLink<BlueskyPostDeepLink> =
        PlatformDeepLink(
            uriPattern = uriPattern,
            serializer = BlueskyPostDeepLink.serializer(),
            callback = { data ->
                DeeplinkRoute.Status.Detail(
                    accountType = AccountType.Specific(accountKey),
                    statusKey =
                        MicroBlogKey(
                            id = "at://${data.handle}/app.bsky.feed.post/${data.id}",
                            host = accountKey.host,
                        ),
                )
            },
        )
}

@Serializable
private data class BlueskyProfileDeepLink(
    val handle: String,
)

@Serializable
private data class BlueskyPostDeepLink(
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

internal fun UiList.Feed.toTimelineTabItemV2(accountKey: MicroBlogKey): TimelineTabItemV2 =
    BlueskyPlatformSpec.feedTimelineSpec
        .tabItem(
            data = TimelineSpec.AccountResourceData(accountKey, id),
            title = UiText.Raw(title),
            icon = avatar?.let { IconType.Url(it) } ?: UiIcon.Feeds.asType(),
        )
