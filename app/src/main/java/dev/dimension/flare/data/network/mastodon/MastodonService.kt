package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.data.network.authorization.BearerAuthorization
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.mastodon.api.TimelineResources
import dev.dimension.flare.data.network.mastodon.api.model.MastodonPaging
import dev.dimension.flare.data.network.mastodon.api.model.Notification
import dev.dimension.flare.data.network.mastodon.api.model.NotificationTypes
import dev.dimension.flare.data.network.mastodon.api.model.Status


class MastodonService(
    private val baseUrl: String,
    private val accessToken: String,
) {
    private val resources: TimelineResources by lazy {
        ktorfit(baseUrl, BearerAuthorization(accessToken)).create()
    }

    suspend fun homeTimeline(
        count: Int,
        since_id: String? = null,
        max_id: String? = null,
        min_id: String? = null,
    ) = resources.homeTimeline(max_id = max_id, since_id = since_id, min_id = min_id, limit = count)

    suspend fun mentionsTimeline(
        count: Int,
        since_id: String?,
        max_id: String?
    ): List<Notification> {
        return resources.notification(
            max_id = max_id,
            since_id = since_id,
            limit = count,
            exclude_types = NotificationTypes.values().filter { it != NotificationTypes.mention }
        )
    }

    suspend fun userTimeline(
        user_id: String,
        count: Int,
        since_id: String?,
        max_id: String?,
        exclude_replies: Boolean
    ): List<Status> = resources.userTimeline(
        user_id = user_id,
        max_id = max_id,
        since_id = since_id,
        limit = count,
        exclude_replies = exclude_replies,
    )

    suspend fun favorites(
        user_id: String,
        count: Int,
        since_id: String?,
        max_id: String?
    ): List<Status> {
        val response = resources.favoritesList(
            max_id = max_id,
            since_id = since_id,
            limit = count,
        )
        return MastodonPaging.from(response)
    }

    suspend fun listTimeline(
        list_id: String,
        count: Int,
        max_id: String?,
        since_id: String?
    ) = resources.listTimeline(
        listId = list_id,
        max_id = max_id,
        since_id = since_id,
        limit = count,
    )
//
//    suspend fun lookupUser(id: String): Account {
//        return resources.lookupUser(id)
//    }
//
//    suspend fun lookupStatus(id: String): Status {
//        return resources.lookupStatus(id)
//    }
//
//    suspend fun userPinnedStatus(userId: String): List<Status> {
//        return resources.userTimeline(user_id = userId, pinned = true)
//    }
//
//    suspend fun showRelationship(target_id: String): List<RelationshipResponse> {
//        return resources.showFriendships(listOf(target_id))
//    }
//
//    suspend fun followers(user_id: String, nextPage: String?) = resources.followers(
//        user_id,
//        max_id = nextPage,
//    ).let {
//        MastodonPaging.from(it)
//    }
//
//    suspend fun following(user_id: String, nextPage: String?) = resources.following(
//        user_id,
//        max_id = nextPage,
//    ).let {
//        MastodonPaging.from(it)
//    }
//
//    suspend fun block(id: String) = resources.block(id = id)
//
//    suspend fun unblock(id: String) = resources.unblock(id = id)
//
//    suspend fun report(id: String, scenes: List<String>?, reason: String?) =
//        resources.report(
//            PostReport(
//                accountId = id,
//                statusIds = scenes,
//                comment = reason
//            )
//        )
//
//    suspend fun follow(user_id: String) {
//        resources.follow(user_id)
//    }
//
//    suspend fun unfollow(user_id: String) {
//        resources.unfollow(user_id)
//    }
//
//    suspend fun notificationTimeline(
//        count: Int,
//        since_id: String?,
//        max_id: String?
//    ): List<Notification> {
//        return resources.notification(
//            max_id = max_id,
//            since_id = since_id,
//            limit = count,
//        )
//    }
//
//    suspend fun context(id: String): Context {
//        return resources.context(id)
//    }
//
//    suspend fun searchHashTag(
//        query: String,
//        offset: Int,
//        count: Int,
//    ): List<Hashtag> {
//        return resources.searchV2(
//            query = query,
//            type = SearchType.hashtags,
//            offset = offset,
//            limit = count,
//        ).hashtags ?: emptyList()
//    }
//
//    suspend fun searchTweets(
//        query: String,
//        count: Int,
//        nextPage: String?
//    ) = resources.searchV2(
//        query = query,
//        type = SearchType.statuses,
//        max_id = nextPage,
//        limit = count
//    )
//
//    suspend fun searchUsers(
//        query: String,
//        page: Int?,
//        count: Int,
//        following: Boolean
//    ): List<Account> {
//        return resources.searchV2(
//            query = query,
//            type = SearchType.accounts,
//            limit = count,
//            offset = (page ?: 0) * count,
//            following = following,
//        ).accounts ?: emptyList()
//    }
//
//    suspend fun hashtagTimeline(
//        query: String,
//        count: Int? = null,
//        since_id: String? = null,
//        max_id: String? = null,
//    ): List<Status> = resources.hashtagTimeline(
//        hashtag = query,
//        limit = count,
//        since_id = since_id,
//        max_id = max_id,
//    )
//
//    suspend fun like(id: String): Status {
//        return resources.favourite(id)
//    }
//
//    suspend fun unlike(id: String): Status {
//        return resources.unfavourite(id).let {
//            it.copy(favouritesCount = it.favouritesCount?.let { it - 1 })
//        }
//    }
//
//    suspend fun retweet(id: String): Status {
//        return resources.reblog(id)
//    }
//
//    suspend fun unRetweet(id: String): Status {
//        return resources.unreblog(id).let {
//            it.copy(favouritesCount = it.reblogsCount?.let { it - 1 })
//        }
//    }
//
//    suspend fun delete(id: String): Status {
//        return resources.delete(id)
//    }
//
//    suspend fun upload(channel: ByteReadChannel, name: String): UploadResponse {
//        val data = channel.toByteArray()
//        val multipart = formData {
//            append(
//                "file",
//                data,
//                Headers.build {
//                    append(HttpHeaders.ContentDisposition, "filename=${name}")
//                },
//            )
//        }
//        return resources.upload(multipart)
//    }
//
//    suspend fun compose(data: PostStatus): Status {
//        return resources.post(data)
//    }
//
//    suspend fun vote(id: String, choice: List<Int>): Poll {
//        return resources.vote(id, PostVote(choices = choice.map { it.toString() }))
//    }
//
//    suspend fun emojis(): List<Emoji> = resources.emojis()
//
////    suspend fun download(target: String): InputStream {
////        return httpClientFactory.createHttpClientBuilder()
////            .addInterceptor(AuthorizationInterceptor(BearerAuthorization(accessToken)))
////            .build()
////            .newCall(
////                Request
////                    .Builder()
////                    .url(target)
////                    .get()
////                    .build()
////            )
////            .await()
////            .body
////            ?.byteStream() ?: throw IllegalArgumentException()
////    }
//
//    suspend fun lists(
//        userId: String?,
//        screenName: String?,
//        reverse: Boolean
//    ) = resources.lists()
//
//    suspend fun createList(
//        name: String,
//        mode: String?,
//        description: String?,
//        repliesPolicy: String?
//    ) = resources.createList(PostList(name, repliesPolicy))
//
//    suspend fun updateList(
//        listId: String,
//        name: String?,
//        mode: String?,
//        description: String?,
//        repliesPolicy: String?
//    ) = resources.updateList(listId, PostList(name, repliesPolicy))
//
//    suspend fun destroyList(
//        listId: String
//    ) {
//        resources.deleteList(listId)
//    }
//
//    suspend fun listMembers(
//        listId: String,
//        count: Int,
//        cursor: String?
//    ) = resources.listMembers(listId, max_id = cursor, limit = count)
//        .let {
//            MastodonPaging.from(it)
//        }
//
//    suspend fun addMember(
//        listId: String,
//        userId: String,
//        screenName: String
//    ) {
//        // FIXME: 2021/7/12 API exception 'Record not found' should be 'You need to follow this user first'
//        resources.addMember(listId, PostAccounts(listOf(userId)))
//    }
//
//    suspend fun removeMember(
//        listId: String,
//        userId: String,
//        screenName: String
//    ) {
//        resources.removeMember(listId, PostAccounts(listOf(userId)))
//    }
//
//    suspend fun trends(locationId: String, limit: Int?) = resources.trends(limit)

    suspend fun localTimeline(
        count: Int,
        since_id: String?,
        max_id: String?
    ): List<Status> {
        return resources.publicTimeline(
            since_id = since_id,
            max_id = max_id,
            limit = count,
            local = true
        )
    }

    suspend fun federatedTimeline(
        count: Int,
        since_id: String?,
        max_id: String?
    ): List<Status> {
        return resources.publicTimeline(
            since_id = since_id,
            max_id = max_id,
            limit = count,
            local = false,
            remote = false,
        )
    }
}
