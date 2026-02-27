package dev.dimension.flare.data.datasource.microblog.handler

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbUserRelation
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiRelation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class RelationHandler(
    val dataSource: RelationLoader,
) : KoinComponent {
    private val database: CacheDatabase by inject()
    private val coroutineScope: CoroutineScope by inject()

    fun relation(userKey: MicroBlogKey) =
        Cacheable(
            fetchSource = {
                val result = dataSource.relation(userKey)
                database.userDao().insertUserRelation(
                    DbUserRelation(
                        accountKey = dataSource.accountKey,
                        userKey = userKey,
                        relation = result,
                    ),
                )
            },
            cacheSource = {
                database
                    .userDao()
                    .getUserRelation(
                        accountKey = dataSource.accountKey,
                        userKey = userKey,
                    ).mapNotNull { it?.relation }
            },
        )

    fun follow(userKey: MicroBlogKey) =
        coroutineScope.launch {
            tryRun {
                updateRelation(
                    userKey = userKey,
                    update = { relation ->
                        relation.copy(
                            following = true,
                        )
                    },
                )
                dataSource.follow(userKey)
            }.onFailure {
                updateRelation(
                    userKey = userKey,
                    update = { relation ->
                        relation.copy(
                            following = false,
                        )
                    },
                )
            }
        }

    fun unfollow(userKey: MicroBlogKey) =
        coroutineScope.launch {
            tryRun {
                updateRelation(
                    userKey = userKey,
                    update = { relation ->
                        relation.copy(
                            following = false,
                        )
                    },
                )
                dataSource.unfollow(userKey)
            }.onFailure {
                updateRelation(
                    userKey = userKey,
                    update = { relation ->
                        relation.copy(
                            following = true,
                        )
                    },
                )
            }
        }

    fun block(userKey: MicroBlogKey) =
        coroutineScope.launch {
            tryRun {
                updateRelation(
                    userKey = userKey,
                    update = { relation ->
                        relation.copy(
                            blocking = true,
                        )
                    },
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

    fun unblock(userKey: MicroBlogKey) =
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

    fun mute(userKey: MicroBlogKey) =
        coroutineScope.launch {
            tryRun {
                updateRelation(
                    userKey = userKey,
                    update = { relation ->
                        relation.copy(
                            muted = true,
                        )
                    },
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

    fun unmute(userKey: MicroBlogKey) =
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

    suspend fun approveFollowRequest(userKey: MicroBlogKey) {
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

    suspend fun rejectFollowRequest(userKey: MicroBlogKey) {
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
        update: (UiRelation) -> UiRelation,
    ) {
        val currentRelation =
            database
                .userDao()
                .getUserRelation(
                    accountKey = dataSource.accountKey,
                    userKey = userKey,
                ).mapNotNull { it?.relation }
                .firstOrNull() ?: return
        val newRelation = update(currentRelation)
        database.userDao().insertUserRelation(
            DbUserRelation(
                accountKey = dataSource.accountKey,
                userKey = userKey,
                relation = newRelation,
            ),
        )
    }
}
