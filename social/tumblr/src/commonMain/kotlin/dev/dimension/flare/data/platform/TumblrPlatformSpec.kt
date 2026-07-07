package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.tumblr.TumblrDataSource
import dev.dimension.flare.data.datasource.tumblr.tumblrPostKey
import dev.dimension.flare.data.datasource.tumblr.tumblrUserKey
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.presenter.login.LoginPlatformProvider
import dev.dimension.flare.ui.presenter.login.TumblrLoginProvider
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

public const val TUMBLR_HOST: String = "tumblr.com"
public const val TUMBLR_WEB_HOST: String = "www.tumblr.com"

@HiddenFromObjC
public data object TumblrPlatformSpec :
    PlatformSpec,
    LoginPlatformProvider by TumblrLoginProvider {
    override val type: PlatformType = PlatformType.Tumblr
    override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "Tumblr",
            icon = UiIcon.Tumblr,
        )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(CommonTimelineSpecs.home)

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> =
        persistentListOf(
            tumblrWebPostDeepLink(accountKey, "https://www.tumblr.com/{blogName}/{id}"),
            tumblrSubdomainPostDeepLink(accountKey, "https://{blogname}.tumblr.com/post/{id}"),
            tumblrWebProfileDeepLink(accountKey, "https://www.tumblr.com/{blogName}"),
            tumblrSubdomainProfileDeepLink(accountKey, "https://{blogname}.tumblr.com"),
        )

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
        TumblrDataSource(
            accountKey = context.accountKey,
            credentialFlow = context.credentialFlow(TumblrCredential.serializer()),
            updateCredential = { credential ->
                context.updateCredential(
                    serializer = TumblrCredential.serializer(),
                    credential = credential,
                )
            },
        )

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("Tumblr guest data source is not supported")
}

@Serializable
public data class TumblrCredential(
    val accessToken: String,
    val refreshToken: String? = null,
    val tokenType: String = "bearer",
    val scope: String? = null,
    val expiresAtEpochSeconds: Long? = null,
    val blogIdentifier: String,
    val blogName: String,
    val blogUrl: String,
    val blogUuid: String? = null,
    val isPrimary: Boolean = false,
)

private fun tumblrWebPostDeepLink(
    accountKey: MicroBlogKey,
    uriPattern: String,
): PlatformDeepLink<TumblrWebPostDeepLink> =
    PlatformDeepLink(
        uriPattern = uriPattern,
        serializer = TumblrWebPostDeepLink.serializer(),
        callback = { data ->
            DeeplinkRoute.Status.Detail(
                accountType = AccountType.Specific(accountKey),
                statusKey = tumblrPostKey(data.blogName, data.id),
            )
        },
    )

private fun tumblrSubdomainPostDeepLink(
    accountKey: MicroBlogKey,
    uriPattern: String,
): PlatformDeepLink<TumblrSubdomainPostDeepLink> =
    PlatformDeepLink(
        uriPattern = uriPattern,
        serializer = TumblrSubdomainPostDeepLink.serializer(),
        matcher = { data -> !data.blogName.equals("www", ignoreCase = true) },
        callback = { data ->
            DeeplinkRoute.Status.Detail(
                accountType = AccountType.Specific(accountKey),
                statusKey = tumblrPostKey(data.blogName, data.id),
            )
        },
    )

private fun tumblrWebProfileDeepLink(
    accountKey: MicroBlogKey,
    uriPattern: String,
): PlatformDeepLink<TumblrWebProfileDeepLink> =
    PlatformDeepLink(
        uriPattern = uriPattern,
        serializer = TumblrWebProfileDeepLink.serializer(),
        callback = { data ->
            DeeplinkRoute.Profile.User(
                accountType = AccountType.Specific(accountKey),
                userKey = tumblrUserKey(data.blogName),
            )
        },
    )

private fun tumblrSubdomainProfileDeepLink(
    accountKey: MicroBlogKey,
    uriPattern: String,
): PlatformDeepLink<TumblrSubdomainProfileDeepLink> =
    PlatformDeepLink(
        uriPattern = uriPattern,
        serializer = TumblrSubdomainProfileDeepLink.serializer(),
        matcher = { data -> !data.blogName.equals("www", ignoreCase = true) },
        callback = { data ->
            DeeplinkRoute.Profile.User(
                accountType = AccountType.Specific(accountKey),
                userKey = tumblrUserKey(data.blogName),
            )
        },
    )

@Serializable
private data class TumblrWebPostDeepLink(
    val blogName: String,
    val id: String,
)

@Serializable
private data class TumblrSubdomainPostDeepLink(
    @SerialName("blogname")
    val blogName: String,
    val id: String,
)

@Serializable
private data class TumblrWebProfileDeepLink(
    val blogName: String,
)

@Serializable
private data class TumblrSubdomainProfileDeepLink(
    @SerialName("blogname")
    val blogName: String,
)
