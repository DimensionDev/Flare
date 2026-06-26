package dev.dimension.flare.data.datasource.microblog.handler

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbUserRelation
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import dev.dimension.flare.di.koinInject
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public class RelationHandler(
    public val accountType: AccountType,
    public val dataSource: RelationLoader,
)  {
    private val database: CacheDatabase by koinInject()
    private val coroutineScope: CoroutineScope by koinInject()

    public fun relation(userKey: MicroBlogKey): Cacheable<UiRelation> =
        Cacheable(
            fetchSource = {
                val result = dataSource.relation(userKey)
                database.userDao().insertUserRelation(
                    DbUserRelation(
                        accountType = accountType as DbAccountType,
                        userKey = userKey,
                        relation = result,
                    ),
                )
            },
            cacheSource = {
                database
                    .userDao()
                    .getUserRelation(
                        accountType = accountType as DbAccountType,
                        userKey = userKey,
                    ).mapNotNull { it?.relation }
            },
        )

    public fun follow(
        userKey: MicroBlogKey,
        requestFollow: Boolean = false,
    ): Job =
        coroutineScope.launch {
            var previousRelation: UiRelation? = null
            tryRun {
                previousRelation =
                    updateRelation(
                        userKey = userKey,
                        update = { relation ->
                            relation.copy(
                                following = !requestFollow,
                                hasPendingFollowRequestFromYou = requestFollow,
                            )
                        },
                    )
                dataSource.follow(userKey)
            }.onFailure {
                previousRelation?.let { relation ->
                    setRelation(
                        userKey = userKey,
                        relation = relation,
                    )
                }
            }
        }

    public fun unfollow(userKey: MicroBlogKey): Job =
        coroutineScope.launch {
            var previousRelation: UiRelation? = null
            tryRun {
                previousRelation =
                    updateRelation(
                        userKey = userKey,
                        update = { relation ->
                            relation.copy(
                                following = false,
                                hasPendingFollowRequestFromYou = false,
                            )
                        },
                    )
                dataSource.unfollow(userKey)
            }.onFailure {
                previousRelation?.let { relation ->
                    setRelation(
                        userKey = userKey,
                        relation = relation,
                    )
                }
            }
        }

    public fun block(userKey: MicroBlogKey): Job =
        coroutineScope.launch {
            tryRun {
                updateRelation(
                    userKey = userKey,
                    defaultRelation = UiRelation(),
                    update = { relation ->
                        relation.copy(
                            blocking = true,
                        )
                    },
                )
                deleteUserFromLocalCaches(
                    userKey = userKey,
                    deleteUserHistory = true,
                )
                dataSource.block(userKey)
            }.onFailure {
                updateRelation(
                    userKey = userKey,
                    update = { relation ->
                        relation.copy(
                            blocking = false,
                        )
                    },
                )
            }
        }

    public fun unblock(userKey: MicroBlogKey): Job =
        coroutineScope.launch {
            tryRun {
                updateRelation(
                    userKey = userKey,
                    update = { relation ->
                        relation.copy(
                            blocking = false,
                        )
                    },
                )
                dataSource.unblock(userKey)
            }.onFailure {
                updateRelation(
                    userKey = userKey,
                    update = { relation ->
                        relation.copy(
                            blocking = true,
                        )
                    },
                )
            }
        }

    public fun mute(userKey: MicroBlogKey): Job =
        coroutineScope.launch {
            tryRun {
                updateRelation(
                    userKey = userKey,
                    defaultRelation = UiRelation(),
                    update = { relation ->
                        relation.copy(
                            muted = true,
                        )
                    },
                )
                deleteUserFromLocalCaches(
                    userKey = userKey,
                    deleteUserHistory = false,
                )
                dataSource.mute(userKey)
            }.onFailure {
                updateRelation(
                    userKey = userKey,
                    update = { relation ->
                        relation.copy(
                            muted = false,
                        )
                    },
                )
            }
        }

    public fun unmute(userKey: MicroBlogKey): Job =
        coroutineScope.launch {
            tryRun {
                updateRelation(
                    userKey = userKey,
                    update = { relation ->
                        relation.copy(
                            muted = false,
                        )
                    },
                )
                dataSource.unmute(userKey)
            }.onFailure {
                updateRelation(
                    userKey = userKey,
                    update = { relation ->
                        relation.copy(
                            muted = true,
                        )
                    },
                )
            }
        }

    public suspend fun approveFollowRequest(userKey: MicroBlogKey) {
        updateRelation(
            userKey = userKey,
            update = { relation ->
                relation.copy(
                    hasPendingFollowRequestToYou = false,
                    isFans = true,
                )
            },
        )
    }

    public suspend fun rejectFollowRequest(userKey: MicroBlogKey) {
        updateRelation(
            userKey = userKey,
            update = { relation ->
                relation.copy(
                    hasPendingFollowRequestToYou = false,
                    isFans = false,
                )
            },
        )
    }

    private suspend fun updateRelation(
        userKey: MicroBlogKey,
        defaultRelation: UiRelation? = null,
        update: (UiRelation) -> UiRelation,
    ): UiRelation? {
        val currentRelation =
            database
                .userDao()
                .getUserRelation(
                    accountType = accountType as DbAccountType,
                    userKey = userKey,
                ).firstOrNull()
                ?.relation ?: defaultRelation ?: return null
        val newRelation = update(currentRelation)
        setRelation(
            userKey = userKey,
            relation = newRelation,
        )
        return currentRelation
    }

    private suspend fun setRelation(
        userKey: MicroBlogKey,
        relation: UiRelation,
    ) {
        database.userDao().insertUserRelation(
            DbUserRelation(
                accountType = accountType as DbAccountType,
                userKey = userKey,
                relation = relation,
            ),
        )
    }

    private suspend fun deleteUserFromLocalCaches(
        userKey: MicroBlogKey,
        deleteUserHistory: Boolean,
    ) {
        val dbAccountType = accountType as DbAccountType
        database.connect {
            val timelines =
                database
                    .pagingTimelineDao()
                    .getByAccountTypeWithStatus(dbAccountType)
                    .filter { it.containsUser(userKey) }
                    .map { it.timeline }
            if (timelines.isNotEmpty()) {
                database.pagingTimelineDao().delete(timelines)
            }
            if (deleteUserHistory) {
                database.userDao().deleteHistory(
                    accountType = dbAccountType,
                    userKey = userKey,
                )
            }
        }
    }

    private fun DbPagingTimelineWithStatus.containsUser(userKey: MicroBlogKey): Boolean =
        status.status.data.content
            .containsUser(userKey) ||
            status.references.any { reference ->
                reference.status
                    ?.data
                    ?.content
                    ?.containsUser(userKey) == true
            }

    private fun UiTimelineV2.containsUser(userKey: MicroBlogKey): Boolean =
        when (this) {
            is UiTimelineV2.Feed -> {
                false
            }

            is UiTimelineV2.Message -> {
                user?.key == userKey
            }

            is UiTimelineV2.Post -> {
                user?.key == userKey ||
                    message?.user?.key == userKey ||
                    quote.any { it.containsUser(userKey) } ||
                    parents.any { it.containsUser(userKey) } ||
                    internalRepost?.containsUser(userKey) == true
            }

            is UiTimelineV2.User -> {
                value.key == userKey || message?.user?.key == userKey
            }

            is UiTimelineV2.UserList -> {
                users.any { it.key == userKey } ||
                    message?.user?.key == userKey ||
                    post?.containsUser(userKey) == true
            }
        }
}
