package dev.dimension.flare.data.datasource.microblog.handler

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbUserRelation
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiRelation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class RelationHandler(
    public val accountType: AccountType,
    public val dataSource: RelationLoader,
) : KoinComponent {
    private val database: CacheDatabase by inject()
    private val coroutineScope: CoroutineScope by inject()

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

    public fun follow(userKey: MicroBlogKey): kotlinx.coroutines.Job =
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

    public fun unfollow(userKey: MicroBlogKey): kotlinx.coroutines.Job =
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

    public fun block(userKey: MicroBlogKey): kotlinx.coroutines.Job =
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

    public fun unblock(userKey: MicroBlogKey): kotlinx.coroutines.Job =
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

    public fun mute(userKey: MicroBlogKey): kotlinx.coroutines.Job =
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

    public fun unmute(userKey: MicroBlogKey): kotlinx.coroutines.Job =
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
        update: (UiRelation) -> UiRelation,
    ) {
        val currentRelation =
            database
                .userDao()
                .getUserRelation(
                    accountType = accountType as DbAccountType,
                    userKey = userKey,
                ).mapNotNull { it?.relation }
                .firstOrNull() ?: return
        val newRelation = update(currentRelation)
        database.userDao().insertUserRelation(
            DbUserRelation(
                accountType = accountType as DbAccountType,
                userKey = userKey,
                relation = newRelation,
            ),
        )
    }
}
