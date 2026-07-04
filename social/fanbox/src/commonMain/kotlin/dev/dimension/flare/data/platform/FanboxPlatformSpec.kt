package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.fanbox.FanboxDataSource
import dev.dimension.flare.data.datasource.fanbox.fanboxCreatorKey
import dev.dimension.flare.data.datasource.fanbox.fanboxPostKey
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.accountLoader
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.login.FanboxLoginProvider
import dev.dimension.flare.ui.presenter.login.LoginPlatformProvider
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

private const val FANBOX_SUPPORTED_TIMELINE_SPEC_ID: String = "fanbox.supported"

@HiddenFromObjC
public data object FanboxPlatformSpec :
    PlatformSpec,
    LoginPlatformProvider by FanboxLoginProvider {
    override val type: PlatformType = PlatformType.Fanbox
    override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "FANBOX",
            icon = UiIcon.Fanbox,
        )

    internal val supportedTimelineSpec =
        TimelineSpec(
            id = FANBOX_SUPPORTED_TIMELINE_SPEC_ID,
            title = UiStrings.FanboxSupported,
            icon = UiIcon.Heart.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<FanboxDataSource, TimelineSpec.AccountBasedData> {
                    supportedTimelineLoader()
                },
        )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
            CommonTimelineSpecs.discover,
            supportedTimelineSpec,
        )

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> =
        persistentListOf(
            fanboxPostDeepLink(accountKey, "https://www.fanbox.cc/@{creatorId}/posts/{id}"),
            fanboxSubdomainPostDeepLink(accountKey, "https://{creatorid}.fanbox.cc/posts/{id}"),
            fanboxCreatorDeepLink(accountKey, "https://www.fanbox.cc/@{creatorId}"),
            fanboxSubdomainCreatorDeepLink(accountKey, "https://{creatorid}.fanbox.cc"),
        )

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
        FanboxDataSource(
            accountKey = context.accountKey,
            credentialFlow = context.credentialFlow(FanboxCredential.serializer()),
            updateCredential = { credential ->
                context.updateCredential(
                    serializer = FanboxCredential.serializer(),
                    credential = credential,
                )
            },
        )

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("FANBOX guest data source is not supported")
}

private fun fanboxPostDeepLink(
    accountKey: MicroBlogKey,
    uriPattern: String,
): PlatformDeepLink<FanboxPostDeepLink> =
    PlatformDeepLink(
        uriPattern = uriPattern,
        serializer = FanboxPostDeepLink.serializer(),
        callback = { data ->
            DeeplinkRoute.Article(
                accountType = AccountType.Specific(accountKey),
                articleKey = fanboxPostKey(data.id),
            )
        },
    )

private fun fanboxSubdomainPostDeepLink(
    accountKey: MicroBlogKey,
    uriPattern: String,
): PlatformDeepLink<FanboxSubdomainPostDeepLink> =
    PlatformDeepLink(
        uriPattern = uriPattern,
        serializer = FanboxSubdomainPostDeepLink.serializer(),
        matcher = { data -> data.creatorId.isFanboxSubdomainCreatorId() },
        callback = { data ->
            DeeplinkRoute.Article(
                accountType = AccountType.Specific(accountKey),
                articleKey = fanboxPostKey(data.id),
            )
        },
    )

private fun fanboxCreatorDeepLink(
    accountKey: MicroBlogKey,
    uriPattern: String,
): PlatformDeepLink<FanboxCreatorDeepLink> =
    PlatformDeepLink(
        uriPattern = uriPattern,
        serializer = FanboxCreatorDeepLink.serializer(),
        callback = { data ->
            DeeplinkRoute.Profile.User(
                accountType = AccountType.Specific(accountKey),
                userKey = fanboxCreatorKey(data.creatorId),
            )
        },
    )

private fun fanboxSubdomainCreatorDeepLink(
    accountKey: MicroBlogKey,
    uriPattern: String,
): PlatformDeepLink<FanboxSubdomainCreatorDeepLink> =
    PlatformDeepLink(
        uriPattern = uriPattern,
        serializer = FanboxSubdomainCreatorDeepLink.serializer(),
        matcher = { data -> data.creatorId.isFanboxSubdomainCreatorId() },
        callback = { data ->
            DeeplinkRoute.Profile.User(
                accountType = AccountType.Specific(accountKey),
                userKey = fanboxCreatorKey(data.creatorId),
            )
        },
    )

@Serializable
private data class FanboxPostDeepLink(
    val creatorId: String,
    val id: String,
)

@Serializable
private data class FanboxSubdomainPostDeepLink(
    @SerialName("creatorid")
    val creatorId: String,
    val id: String,
)

@Serializable
private data class FanboxCreatorDeepLink(
    val creatorId: String,
)

@Serializable
private data class FanboxSubdomainCreatorDeepLink(
    @SerialName("creatorid")
    val creatorId: String,
)

private fun String.isFanboxSubdomainCreatorId(): Boolean = !equals("www", ignoreCase = true)
