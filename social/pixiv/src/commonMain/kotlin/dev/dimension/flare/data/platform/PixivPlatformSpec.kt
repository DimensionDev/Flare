package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.pixiv.PixivDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.accountLoader
import dev.dimension.flare.data.network.pixiv.PixivRankingMode
import dev.dimension.flare.data.network.pixiv.PixivRestrict
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
import dev.dimension.flare.ui.presenter.login.LoginPlatformProvider
import dev.dimension.flare.ui.presenter.login.PixivLoginProvider
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data object PixivPlatformSpec :
    PlatformSpec,
    LoginPlatformProvider by PixivLoginProvider {
    override val type: PlatformType = PlatformType.Pixiv
    override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "pixiv",
            icon = UiIcon.Pixiv,
        )

    internal val bookmarkTimelineSpec =
        TimelineSpec(
            id = PIXIV_BOOKMARK,
            title = UiStrings.Favourite,
            icon = UiIcon.Heart.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<PixivDataSource, TimelineSpec.AccountBasedData> {
                    bookmarkTimelineLoader()
                },
        )

    internal val privateBookmarkTimelineSpec =
        TimelineSpec(
            id = PIXIV_PRIVATE_BOOKMARK,
            title = UiStrings.PixivPrivateFavourites,
            icon = UiIcon.Heart.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<PixivDataSource, TimelineSpec.AccountBasedData> {
                    bookmarkTimelineLoader(PixivRestrict.Private)
                },
        )

    internal val followingTimelineSpec =
        TimelineSpec(
            id = PIXIV_FOLLOWING,
            title = UiStrings.Following,
            icon = UiIcon.Follow.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<PixivDataSource, TimelineSpec.AccountBasedData> {
                    followingTimelineLoader()
                },
        )

    internal val privateFollowingTimelineSpec =
        TimelineSpec(
            id = PIXIV_PRIVATE_FOLLOWING,
            title = UiStrings.PixivPrivateFollowing,
            icon = UiIcon.Follow.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<PixivDataSource, TimelineSpec.AccountBasedData> {
                    followingTimelineLoader(PixivRestrict.Private)
                },
        )

    internal val rankingWeekTimelineSpec =
        pixivRankingTimelineSpec(
            id = PIXIV_RANKING_WEEK,
            title = UiStrings.PixivRankingWeek,
            mode = PixivRankingMode.Week,
        )

    internal val rankingMonthTimelineSpec =
        pixivRankingTimelineSpec(
            id = PIXIV_RANKING_MONTH,
            title = UiStrings.PixivRankingMonth,
            mode = PixivRankingMode.Month,
        )

    internal val rankingDayMaleTimelineSpec =
        pixivRankingTimelineSpec(
            id = PIXIV_RANKING_DAY_MALE,
            title = UiStrings.PixivRankingDayMale,
            mode = PixivRankingMode.DayMale,
        )

    internal val rankingDayFemaleTimelineSpec =
        pixivRankingTimelineSpec(
            id = PIXIV_RANKING_DAY_FEMALE,
            title = UiStrings.PixivRankingDayFemale,
            mode = PixivRankingMode.DayFemale,
        )

    internal val rankingWeekOriginalTimelineSpec =
        pixivRankingTimelineSpec(
            id = PIXIV_RANKING_WEEK_ORIGINAL,
            title = UiStrings.PixivRankingWeekOriginal,
            mode = PixivRankingMode.WeekOriginal,
        )

    internal val rankingWeekRookieTimelineSpec =
        pixivRankingTimelineSpec(
            id = PIXIV_RANKING_WEEK_ROOKIE,
            title = UiStrings.PixivRankingWeekRookie,
            mode = PixivRankingMode.WeekRookie,
        )

    internal val rankingDayMangaTimelineSpec =
        pixivRankingTimelineSpec(
            id = PIXIV_RANKING_DAY_MANGA,
            title = UiStrings.PixivRankingDayManga,
            mode = PixivRankingMode.DayManga,
        )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
            CommonTimelineSpecs.discover,
            followingTimelineSpec,
            privateFollowingTimelineSpec,
            bookmarkTimelineSpec,
            privateBookmarkTimelineSpec,
            rankingWeekTimelineSpec,
            rankingMonthTimelineSpec,
            rankingDayMaleTimelineSpec,
            rankingDayFemaleTimelineSpec,
            rankingWeekOriginalTimelineSpec,
            rankingWeekRookieTimelineSpec,
            rankingDayMangaTimelineSpec,
        )

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> =
        persistentListOf(
            PlatformDeepLink(
                uriPattern = "https://www.pixiv.net/artworks/{id}",
                serializer = PixivIllustDeepLink.serializer(),
                callback = { data ->
                    DeeplinkRoute.Gallery.Detail(
                        accountType = AccountType.Specific(accountKey),
                        statusKey = MicroBlogKey(data.id, PIXIV_HOST),
                    )
                },
            ),
            PlatformDeepLink(
                uriPattern = "https://www.pixiv.net/users/{id}",
                serializer = PixivUserDeepLink.serializer(),
                callback = { data ->
                    DeeplinkRoute.Profile.User(
                        accountType = AccountType.Specific(accountKey),
                        userKey = MicroBlogKey(data.id, PIXIV_HOST),
                    )
                },
            ),
        )

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
        PixivDataSource(
            accountKey = context.accountKey,
            credentialFlow = context.credentialFlow(PixivCredential.serializer()),
            updateCredential = { credential ->
                context.updateCredential(
                    serializer = PixivCredential.serializer(),
                    credential = credential,
                )
            },
        )

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("Pixiv guest data source is not supported")
}

@Serializable
private data class PixivIllustDeepLink(
    val id: String,
)

@Serializable
private data class PixivUserDeepLink(
    val id: String,
)

@HiddenFromObjC
public const val PIXIV_HOST: String = "pixiv.net"

private const val PIXIV_FOLLOWING: String = "pixiv.following"
private const val PIXIV_BOOKMARK: String = "pixiv.bookmark"
private const val PIXIV_PRIVATE_FOLLOWING: String = "pixiv.following.private"
private const val PIXIV_PRIVATE_BOOKMARK: String = "pixiv.bookmark.private"
private const val PIXIV_RANKING_WEEK: String = "pixiv.ranking.week"
private const val PIXIV_RANKING_MONTH: String = "pixiv.ranking.month"
private const val PIXIV_RANKING_DAY_MALE: String = "pixiv.ranking.day_male"
private const val PIXIV_RANKING_DAY_FEMALE: String = "pixiv.ranking.day_female"
private const val PIXIV_RANKING_WEEK_ORIGINAL: String = "pixiv.ranking.week_original"
private const val PIXIV_RANKING_WEEK_ROOKIE: String = "pixiv.ranking.week_rookie"
private const val PIXIV_RANKING_DAY_MANGA: String = "pixiv.ranking.day_manga"

private fun pixivRankingTimelineSpec(
    id: String,
    title: UiStrings,
    mode: PixivRankingMode,
): TimelineSpec<TimelineSpec.AccountBasedData> =
    TimelineSpec(
        id = id,
        title = title,
        icon = UiIcon.Featured.asType(),
        serializer = TimelineSpec.AccountBasedData.serializer(),
        targetId = { it.accountKey.toString() },
        loaderFactory =
            accountLoader<PixivDataSource, TimelineSpec.AccountBasedData> {
                rankingTimelineLoader(mode)
            },
    )
