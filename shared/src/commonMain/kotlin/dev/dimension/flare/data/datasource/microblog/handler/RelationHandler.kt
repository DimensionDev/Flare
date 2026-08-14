package dev.dimension.flare.data.datasource.microblog.handler

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbUserRelation
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.native.HiddenFromObjC

// ponytail: bounded hydration avoids Room's 25-statement LRU; use flat SQL if one page ever fans out enough to hit it.
private const val CACHE_CLEANUP_PAGE_SIZE = 100

@HiddenFromObjC
public class RelationHandler(
    public val accountType: AccountType,
    public val dataSource: RelationLoader,
) {
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
                    awaitInvalidation = true,
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
                    awaitInvalidation = true,
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
        awaitInvalidation: Boolean = false,
        update: (UiRelation) -> UiRelation,
    ): UiRelation? {
        val relationFlow =
            database
                .userDao()
                .getUserRelation(
                    accountType = accountType as DbAccountType,
                    userKey = userKey,
                )
        val currentRelation = relationFlow.firstOrNull()?.relation ?: defaultRelation ?: return null
        val newRelation = update(currentRelation)
        val writeRelation: suspend () -> Unit = {
            setRelation(
                userKey = userKey,
                relation = newRelation,
            )
        }
        if (awaitInvalidation) {
            relationFlow.writeAndAwaitInvalidation(
                expectedRelation = newRelation,
                write = writeRelation,
            )
        } else {
            writeRelation()
        }
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

    private suspend fun Flow<DbUserRelation?>.writeAndAwaitInvalidation(
        expectedRelation: UiRelation,
        write: suspend () -> Unit,
    ) = coroutineScope {
        // Room dispatches table invalidation asynchronously after a write. Keep an observer active
        // across the write so cache cleanup cannot acquire the writer before the relation is visible.
        val initialEmission = CompletableDeferred<Unit>()
        val updatedEmission = CompletableDeferred<Unit>()
        var hasInitialEmission = false
        val observer =
            launch(start = CoroutineStart.UNDISPATCHED) {
                collect { value ->
                    if (!hasInitialEmission) {
                        hasInitialEmission = true
                        initialEmission.complete(Unit)
                    } else if (value?.relation == expectedRelation) {
                        updatedEmission.complete(Unit)
                    }
                }
            }
        try {
            initialEmission.await()
            write()
            updatedEmission.await()
        } finally {
            observer.cancelAndJoin()
        }
    }

    private suspend fun deleteUserFromLocalCaches(
        userKey: MicroBlogKey,
        deleteUserHistory: Boolean,
    ) {
        val dbAccountType = accountType as DbAccountType
        val timelines = mutableListOf<DbPagingTimeline>()
        var afterId: String? = null
        do {
            val page =
                database
                    .pagingTimelineDao()
                    .getByAccountTypeWithStatus(
                        accountType = dbAccountType,
                        afterId = afterId,
                        limit = CACHE_CLEANUP_PAGE_SIZE,
                    )
            page
                .filter { it.containsUser(userKey) }
                .mapTo(timelines) { it.timeline }
            afterId = page.lastOrNull()?.timeline?._id
        } while (page.size == CACHE_CLEANUP_PAGE_SIZE)

        database.connect {
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
            } ||
            presentationReferences.any { reference ->
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
                user?.key == userKey
            }

            is UiTimelineV2.TimelinePostItem -> {
                post.containsUser(userKey) ||
                    presentation.message?.user?.key == userKey ||
                    presentation.inlineParents.any { it.containsUser(userKey) } ||
                    presentation.quotes.any { it.containsUser(userKey) } ||
                    presentation.repost?.containsUser(userKey) == true
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
