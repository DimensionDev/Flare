package dev.dimension.flare.data.datasource.microblog.paging

import SnowflakeIdGenerator
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusReference
import dev.dimension.flare.data.database.cache.model.DbStatusReferenceWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatusUserReference
import dev.dimension.flare.data.database.cache.model.DbStatusUserReferenceWithUser
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.toImmutableList
import kotlin.uuid.Uuid

internal object TimelinePagingMapper {
    suspend fun toDb(
        data: UiTimelineV2,
        pagingKey: String,
    ): DbPagingTimelineWithStatus =
        DbPagingTimelineWithStatus(
            timeline =
                DbPagingTimeline(
                    pagingKey = pagingKey,
                    statusKey = data.statusKey,
                    sortId = -SnowflakeIdGenerator.nextId(),
                ),
            status =
                DbStatusWithReference(
                    status = uiTimelineToDbStatusWithUser(data),
                    references =
                        when (data) {
                            is UiTimelineV2.Feed -> emptyList()
                            is UiTimelineV2.Message -> emptyList()
                            is UiTimelineV2.Post ->
                                data.quote.map {
                                    uiTimelineToDbStatusReferenceWithStatus(
                                        data = it,
                                        referenceType = ReferenceType.Quote,
                                        rootStatusKey = data.statusKey,
                                    )
                                } +
                                    data.parents.map {
                                        uiTimelineToDbStatusReferenceWithStatus(
                                            data = it,
                                            referenceType = ReferenceType.Reply,
                                            rootStatusKey = data.statusKey,
                                        )
                                    } +
                                    listOfNotNull(
                                        data.internalRepost?.let {
                                            uiTimelineToDbStatusReferenceWithStatus(
                                                data = it,
                                                referenceType = ReferenceType.Retweet,
                                                rootStatusKey = data.statusKey,
                                            )
                                        },
                                    )

                            is UiTimelineV2.User -> emptyList()
                            is UiTimelineV2.UserList ->
                                listOfNotNull(
                                    data.post?.let {
                                        uiTimelineToDbStatusReferenceWithStatus(
                                            data = it,
                                            referenceType = ReferenceType.Quote,
                                            rootStatusKey = data.statusKey,
                                        )
                                    },
                                )
                        },
                ),
        )

    fun toUi(
        item: DbPagingTimelineWithStatus,
        pagingKey: String,
        useDbKeyInItemKey: Boolean,
    ): UiTimelineV2 {
        val root = dbStatusWithUserToUiTimeline(item.status.status, pagingKey, useDbKeyInItemKey)
        val references =
            item.status.references.mapNotNull { reference ->
                reference.status?.let {
                    reference.reference.referenceType to
                        dbStatusWithUserToUiTimeline(
                            it,
                            pagingKey,
                            useDbKeyInItemKey,
                        )
                }
            }
        return when (root) {
            is UiTimelineV2.Feed -> root
            is UiTimelineV2.Message -> root
            is UiTimelineV2.Post -> {
                val resolvedRoot =
                    root.resolveReferences(
                        references = references,
                    )
                val repost =
                    (references.find { it.first == ReferenceType.Retweet }?.second as? UiTimelineV2.Post)
                        ?: resolvedRoot.internalRepost
                val resolvedRepost =
                    repost?.resolveReferences(
                        references = references,
                    )
                if (resolvedRepost != null) {
                    resolvedRepost.copy(
                        internalRepost = resolvedRepost,
                        statusKey = resolvedRoot.statusKey,
                        message = resolvedRoot.message,
                    )
                } else {
                    resolvedRoot
                }
            }
            is UiTimelineV2.User -> root
            is UiTimelineV2.UserList ->
                root.copy(
                    post =
                        root.post?.let { post ->
                            references.map { it.second }.find { it.statusKey == post.statusKey } as? UiTimelineV2.Post ?: post
                        },
                )
        }
    }

    private fun UiTimelineV2.Post.resolveReferences(references: List<Pair<ReferenceType, UiTimelineV2>>): UiTimelineV2.Post =
        copy(
            parents =
                parents
                    .map { parent ->
                        references
                            .find { it.first == ReferenceType.Reply && it.second.statusKey == parent.statusKey }
                            ?.second as? UiTimelineV2.Post ?: parent
                    }.toImmutableList(),
            quote =
                quote
                    .map { quote ->
                        references
                            .find { it.first == ReferenceType.Quote && it.second.statusKey == quote.statusKey }
                            ?.second as? UiTimelineV2.Post ?: quote
                    }.toImmutableList(),
            internalRepost =
                internalRepost?.let { repost ->
                    references
                        .find { it.first == ReferenceType.Retweet && it.second.statusKey == repost.statusKey }
                        ?.second as? UiTimelineV2.Post ?: repost
                },
        )

    private fun uiTimelineToDbStatusReferenceWithStatus(
        data: UiTimelineV2,
        referenceType: ReferenceType,
        rootStatusKey: MicroBlogKey,
    ) = DbStatusReferenceWithStatus(
        reference =
            DbStatusReference(
                referenceType = referenceType,
                statusKey = rootStatusKey,
                referenceStatusKey = data.statusKey,
                _id = Uuid.random().toString(),
            ),
        status = uiTimelineToDbStatusWithUser(data),
    )

    private fun uiTimelineToDbStatusWithUser(data: UiTimelineV2): DbStatusWithUser {
        val user =
            if (data is UiTimelineV2.Post) {
                listOfNotNull(data.user)
            } else if (data is UiTimelineV2.User) {
                listOfNotNull(data.value)
            } else if (data is UiTimelineV2.UserList) {
                data.users
            } else {
                emptyList()
            }
        return DbStatusWithUser(
            data =
                DbStatus(
                    statusKey = data.statusKey,
                    content = data,
                    accountType = data.accountType as DbAccountType,
                    text = data.searchText,
                ),
            references =
                user.map {
                    DbStatusUserReferenceWithUser(
                        reference =
                            DbStatusUserReference(
                                statusKey = data.statusKey,
                                referenceUserKey = it.key,
                                _id = Uuid.random().toString(),
                            ),
                        user =
                            it.toDbUser(),
                    )
                },
        )
    }

    private fun dbStatusWithUserToUiTimeline(
        data: DbStatusWithUser,
        pagingKey: String,
        useDbKeyInItemKey: Boolean,
    ): UiTimelineV2 {
        val root = data.data.content
        val users = data.references.mapNotNull { it.user?.content }
        return when (root) {
            is UiTimelineV2.Feed -> root
            is UiTimelineV2.Message ->
                root.copy(
                    user = users.find { root.user?.key == it.key } ?: root.user,
                    extraKey = if (useDbKeyInItemKey) pagingKey else null,
                )
            is UiTimelineV2.Post ->
                root.copy(
                    user = users.find { root.user?.key == it.key } ?: root.user,
                    extraKey = if (useDbKeyInItemKey) pagingKey else null,
                )
            is UiTimelineV2.User ->
                root.copy(
                    value = users.find { root.value.key == it.key } ?: root.value,
                    extraKey = if (useDbKeyInItemKey) pagingKey else null,
                )
            is UiTimelineV2.UserList ->
                root.copy(
                    users =
                        root.users
                            .map { user ->
                                users.find { user.key == it.key } ?: user
                            }.toImmutableList(),
                    extraKey = if (useDbKeyInItemKey) pagingKey else null,
                )
        }
    }
}
