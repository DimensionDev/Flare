package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.network.authorization.BearerAuthorization
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.mastodon.api.AccountResources
import dev.dimension.flare.data.network.mastodon.api.FriendshipResources
import dev.dimension.flare.data.network.mastodon.api.ListsResources
import dev.dimension.flare.data.network.mastodon.api.LookupResources
import dev.dimension.flare.data.network.mastodon.api.MastodonResources
import dev.dimension.flare.data.network.mastodon.api.SearchResources
import dev.dimension.flare.data.network.mastodon.api.StatusResources
import dev.dimension.flare.data.network.mastodon.api.TimelineResources
import dev.dimension.flare.data.network.mastodon.api.TrendsResources
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.Context
import dev.dimension.flare.data.network.mastodon.api.model.Emoji
import dev.dimension.flare.data.network.mastodon.api.model.Hashtag
import dev.dimension.flare.data.network.mastodon.api.model.MastodonPaging
import dev.dimension.flare.data.network.mastodon.api.model.Notification
import dev.dimension.flare.data.network.mastodon.api.model.NotificationTypes
import dev.dimension.flare.data.network.mastodon.api.model.Poll
import dev.dimension.flare.data.network.mastodon.api.model.PostAccounts
import dev.dimension.flare.data.network.mastodon.api.model.PostList
import dev.dimension.flare.data.network.mastodon.api.model.PostReport
import dev.dimension.flare.data.network.mastodon.api.model.PostStatus
import dev.dimension.flare.data.network.mastodon.api.model.PostVote
import dev.dimension.flare.data.network.mastodon.api.model.RelationshipResponse
import dev.dimension.flare.data.network.mastodon.api.model.SearchType
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.data.network.mastodon.api.model.UploadResponse
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import java.io.InputStream

class MastodonService(
    private val baseUrl: String,
    private val accessToken: String
) {
    private val clientConfig: HttpClientConfig<OkHttpConfig>.() -> Unit by lazy {
        {
            expectSuccess = true
            HttpResponseValidator {
                handleResponseExceptionWithRequest { exception, request ->
                    if (exception is ResponseException) {
                        exception.response.bodyAsText().decodeJson<MastodonException>()
                            .takeIf { it.error != null }?.let {
                                throw it
                            }
                    }
                }
            }
        }
    }
    private val timelineResources: TimelineResources by lazy {
        ktorfit(baseUrl, BearerAuthorization(accessToken), clientConfig).create()
    }

    private val lookupResources: LookupResources by lazy {
        ktorfit(baseUrl, BearerAuthorization(accessToken), clientConfig).create()
    }

    private val friendshipResources: FriendshipResources by lazy {
        ktorfit(baseUrl, BearerAuthorization(accessToken), clientConfig).create()
    }

    private val accountResources: AccountResources by lazy {
        ktorfit(baseUrl, BearerAuthorization(accessToken), clientConfig).create()
    }

    private val searchResources: SearchResources by lazy {
        ktorfit(baseUrl, BearerAuthorization(accessToken), clientConfig).create()
    }

    private val statusResources: StatusResources by lazy {
        ktorfit(baseUrl, BearerAuthorization(accessToken), clientConfig).create()
    }

    private val listsResources: ListsResources by lazy {
        ktorfit(baseUrl, BearerAuthorization(accessToken), clientConfig).create()
    }

    private val trendsResources: TrendsResources by lazy {
        ktorfit(baseUrl, BearerAuthorization(accessToken), clientConfig).create()
    }

    private val mastodonResources: MastodonResources by lazy {
        ktorfit(baseUrl, BearerAuthorization(accessToken), clientConfig).create()
    }

    suspend fun homeTimeline(
        count: Int,
        since_id: String? = null,
        max_id: String? = null,
        min_id: String? = null
    ) = timelineResources.homeTimeline(
        max_id = max_id,
        since_id = since_id,
        min_id = min_id,
        limit = count
    )

    suspend fun mentionsTimeline(
        count: Int,
        since_id: String? = null,
        max_id: String? = null,
        min_id: String? = null
    ): List<Notification> {
        return timelineResources.notification(
            max_id = max_id,
            since_id = since_id,
            limit = count,
            min_id = min_id,
            exclude_types = NotificationTypes.values().filter { it != NotificationTypes.Mention }
        )
    }

    suspend fun userTimeline(
        user_id: String,
        count: Int,
        min_id: String? = null,
        since_id: String? = null,
        max_id: String? = null,
        exclude_replies: Boolean? = null
    ): List<Status> = timelineResources.userTimeline(
        user_id = user_id,
        max_id = max_id,
        since_id = since_id,
        limit = count,
        exclude_replies = exclude_replies,
        min_id = min_id
    )

    suspend fun favorites(
        count: Int,
        since_id: String?,
        max_id: String?
    ): List<Status> {
        val response = timelineResources.favoritesList(
            max_id = max_id,
            since_id = since_id,
            limit = count
        )
        return MastodonPaging.from(response)
    }

    suspend fun listTimeline(
        list_id: String,
        count: Int,
        max_id: String?,
        since_id: String?
    ) = timelineResources.listTimeline(
        listId = list_id,
        max_id = max_id,
        since_id = since_id,
        limit = count
    )

    suspend fun lookupUser(id: String): Account {
        return lookupResources.lookupUser(id)
    }

    suspend fun lookupStatus(id: String): Status {
        return lookupResources.lookupStatus(id)
    }

    suspend fun userPinnedStatus(userId: String): List<Status> {
        return timelineResources.userTimeline(user_id = userId, pinned = true)
    }

    suspend fun showRelationship(target_id: String): List<RelationshipResponse> {
        return friendshipResources.showFriendships(listOf(target_id))
    }

    suspend fun followers(user_id: String, nextPage: String?) = accountResources.followers(
        user_id,
        max_id = nextPage
    ).let {
        MastodonPaging.from(it)
    }

    suspend fun following(user_id: String, nextPage: String?) = accountResources.following(
        user_id,
        max_id = nextPage
    ).let {
        MastodonPaging.from(it)
    }

    suspend fun block(id: String) = friendshipResources.block(id = id)

    suspend fun unblock(id: String) = friendshipResources.unblock(id = id)

    suspend fun report(id: String, scenes: List<String>?, reason: String?) =
        friendshipResources.report(
            PostReport(
                accountId = id,
                statusIds = scenes,
                comment = reason
            )
        )

    suspend fun follow(user_id: String) {
        friendshipResources.follow(user_id)
    }

    suspend fun unfollow(user_id: String) {
        friendshipResources.unfollow(user_id)
    }

    suspend fun notificationTimeline(
        count: Int,
        since_id: String? = null,
        max_id: String? = null,
        min_id: String? = null
    ): List<Notification> {
        return timelineResources.notification(
            max_id = max_id,
            since_id = since_id,
            limit = count,
            min_id = min_id
        )
    }

    suspend fun context(id: String): Context {
        return timelineResources.context(id)
    }

    suspend fun searchHashTag(
        query: String,
        offset: Int,
        count: Int
    ): List<Hashtag> {
        return searchResources.searchV2(
            query = query,
            type = SearchType.HashTags.value,
            offset = offset,
            limit = count
        ).hashtags ?: emptyList()
    }

    suspend fun searchTweets(
        query: String,
        count: Int,
        nextPage: String?
    ) = searchResources.searchV2(
        query = query,
        type = SearchType.Statuses.value,
        max_id = nextPage,
        limit = count
    )

    suspend fun searchUsers(
        query: String,
        page: Int?,
        count: Int,
        following: Boolean
    ): List<Account> {
        return searchResources.searchV2(
            query = query,
            type = SearchType.Accounts.value,
            limit = count,
            offset = (page ?: 0) * count,
            following = following
        ).accounts ?: emptyList()
    }

    suspend fun hashtagTimeline(
        query: String,
        count: Int? = null,
        since_id: String? = null,
        max_id: String? = null
    ): List<Status> = timelineResources.hashtagTimeline(
        hashtag = query,
        limit = count,
        since_id = since_id,
        max_id = max_id
    )

    suspend fun like(id: String): Status {
        return statusResources.favourite(id)
    }

    suspend fun unlike(id: String): Status {
        return statusResources.unfavourite(id).let {
            it.copy(favouritesCount = it.favouritesCount?.let { it - 1 })
        }
    }

    suspend fun retweet(id: String): Status {
        return statusResources.reblog(id)
    }

    suspend fun unRetweet(id: String): Status {
        return statusResources.unreblog(id).let {
            it.copy(favouritesCount = it.reblogsCount?.let { it - 1 })
        }
    }

    suspend fun delete(id: String): Status {
        return statusResources.delete(id)
    }

    suspend fun upload(channel: InputStream, name: String): UploadResponse {
        val data = channel.readBytes()
        val multipart = MultiPartFormDataContent(
            formData {
                append(
                    "file",
                    data,
                    Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=$name")
                    }
                )
            }
        )

        return statusResources.upload(multipart)
    }

    suspend fun compose(
        idempotencyKey: String,
        data: PostStatus
    ): Status {
        return statusResources.post(idempotencyKey, data)
    }

    suspend fun vote(id: String, choice: List<Int>): Poll {
        return statusResources.vote(id, PostVote(choices = choice.map { it.toString() }))
    }

    suspend fun emojis(): List<Emoji> = mastodonResources.emojis()

    suspend fun lists() = listsResources.lists()

    suspend fun createList(
        name: String,
        repliesPolicy: String?
    ) = listsResources.createList(PostList(name, repliesPolicy))

    suspend fun updateList(
        listId: String,
        name: String?,
        repliesPolicy: String?
    ) = listsResources.updateList(listId, PostList(name, repliesPolicy))

    suspend fun destroyList(
        listId: String
    ) {
        listsResources.deleteList(listId)
    }

    suspend fun listMembers(
        listId: String,
        count: Int,
        cursor: String?
    ) = listsResources.listMembers(listId, max_id = cursor, limit = count)
        .let {
            MastodonPaging.from(it)
        }

    suspend fun addMember(
        listId: String,
        userId: String
    ) {
        // FIXME: 2021/7/12 API exception 'Record not found' should be 'You need to follow this user first'
        listsResources.addMember(listId, PostAccounts(listOf(userId)))
    }

    suspend fun removeMember(
        listId: String,
        userId: String
    ) {
        listsResources.removeMember(listId, PostAccounts(listOf(userId)))
    }

    suspend fun trends(limit: Int?) = trendsResources.trends(limit)

    suspend fun localTimeline(
        count: Int,
        since_id: String?,
        max_id: String?
    ): List<Status> {
        return timelineResources.publicTimeline(
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
        return timelineResources.publicTimeline(
            since_id = since_id,
            max_id = max_id,
            limit = count,
            local = false,
            remote = false
        )
    }
}
