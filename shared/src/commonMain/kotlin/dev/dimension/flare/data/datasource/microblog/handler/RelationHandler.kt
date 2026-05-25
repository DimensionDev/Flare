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

internal class RelationHandler(
    val accountType: AccountType,
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

    fun follow(
        userKey: MicroBlogKey,
        requestFollow: Boolean = false,
    ) = coroutineScope.launch {
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

    fun unfollow(userKey: MicroBlogKey) =
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
    ): UiRelation? {
        val currentRelation =
            database
                .userDao()
                .getUserRelation(
                    accountType = accountType as DbAccountType,
                    userKey = userKey,
                ).mapNotNull { it?.relation }
                .firstOrNull() ?: return null
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
}
