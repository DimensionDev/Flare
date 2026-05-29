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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
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
