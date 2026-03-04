package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.PostCreateRetweetRequestVariables
import dev.dimension.flare.data.network.xqt.model.PostDeleteTweetRequest
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.network.xqt.model.UserUnavailable
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.model.mapper.screenName
import dev.dimension.flare.ui.model.mapper.toUi

internal class XQTLoader(
    override val accountKey: MicroBlogKey,
    private val service: XQTService,
) : NotificationLoader,
    UserLoader,
    PostLoader,
    RelationLoader {
    override suspend fun notificationBadgeCount(): Int = service.getBadgeCount().ntabUnreadCount?.toInt() ?: 0

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
        require(uiHandle.normalizedHost == accountKey.host) {
            "Cross-host lookup is unsupported for XQT: ${uiHandle.normalizedHost}"
        }
        val user =
            service
                .userByScreenName(uiHandle.normalizedRaw)
                .body()
                ?.data
                ?.user
                ?.result
                ?.let {
                    when (it) {
                        is User -> it
                        is UserUnavailable -> null
                    }
                } ?: throw Exception("User not found")
        return user.render(accountKey)
    }

    override suspend fun userById(id: String): UiProfile {
        val user =
            service
                .userById(id)
                .body()
                ?.data
                ?.user
                ?.result
                ?.let {
                    when (it) {
                        is User -> it
                        is UserUnavailable -> null
                    }
                } ?: throw Exception("User not found")
        return user.render(accountKey)
    }

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 {
        val instructions =
            service
                .getTweetDetail(
                    variables =
                        TweetDetailRequest(
                            focalTweetID = statusKey.id,
                            cursor = null,
                        ).encodeJson(),
                ).body()
                ?.data
                ?.threadedConversationWithInjectionsV2
                ?.instructions
                .orEmpty()
        val item = instructions.tweets().firstOrNull { it.id == statusKey.id }
        return item?.render(accountKey) ?: throw Exception("Status not found")
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        service.postDeleteTweet(
            postDeleteTweetRequest =
                PostDeleteTweetRequest(
                    variables =
                        PostCreateRetweetRequestVariables(
                            tweetId = statusKey.id,
                        ),
                ),
        )
    }

    override suspend fun relation(userKey: MicroBlogKey): UiRelation {
        val userResponse =
            service
                .userById(userKey.id)
                .body()
                ?.data
                ?.user
                ?.result
                ?.let {
                    when (it) {
                        is User -> it
                        is UserUnavailable -> null
                    }
                } ?: throw Exception("User not found")
        return service
            .profileSpotlights(userResponse.screenName)
            .body()
            ?.toUi(muting = userResponse.legacy.muting) ?: throw Exception("User not found")
    }

    override suspend fun follow(userKey: MicroBlogKey) {
        service.postCreateFriendships(userId = userKey.id)
    }

    override suspend fun unfollow(userKey: MicroBlogKey) {
        service.postDestroyFriendships(userId = userKey.id)
    }

    override suspend fun block(userKey: MicroBlogKey) {
        service.postBlocksCreate(userKey.id)
    }

    override suspend fun unblock(userKey: MicroBlogKey) {
        service.postBlocksDestroy(userKey.id)
    }

    override suspend fun mute(userKey: MicroBlogKey) {
        service.postMutesUsersCreate(userKey.id)
    }

    override suspend fun unmute(userKey: MicroBlogKey) {
        service.postMutesUsersDestroy(userKey.id)
    }
}
