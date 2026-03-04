package dev.dimension.flare.data.datasource.microblog.handler

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.UpdatePostActionMenuEvent
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class PostEventHandler(
    private val accountKey: MicroBlogKey,
    private val handler: Handler,
) : KoinComponent,
    DatabaseUpdater {
    private val coroutineScope: CoroutineScope by inject()
    private val database: CacheDatabase by inject()

    interface Handler {
        suspend fun handle(
            event: PostEvent,
            updater: DatabaseUpdater,
        )
    }

    fun handleEvent(event: PostEvent) {
        coroutineScope.launch {
            val originalData =
                database
                    .statusDao()
                    .get(
                        statusKey = event.postKey,
                        accountType = AccountType.Specific(accountKey),
                    ).firstOrNull()
                    ?.content
            if (event is UpdatePostActionMenuEvent && originalData is UiTimelineV2.Post) {
                val updatedData =
                    originalData.copy(
                        actions =
                            findAndReplaceActionMenu(
                                actions = originalData.actions,
                                newActionMenu = event.nextActionMenu(),
                            ),
                    )
                database.statusDao().update(
                    statusKey = event.postKey,
                    accountType = AccountType.Specific(accountKey),
                    content = updatedData,
                )
            }
            if (event is PostEvent.PollEvent && originalData is UiTimelineV2.Post) {
                val updatedData =
                    originalData.copy(
                        poll =
                            originalData.poll?.copy(
                                ownVotes = event.options,
                                options =
                                    originalData.poll.options
                                        .mapIndexed { index, option ->
                                            if (event.options.contains(index)) {
                                                option.copy(
                                                    votesCount =
                                                        option.votesCount.plus(
                                                            1,
                                                        ),
                                                )
                                            } else {
                                                option
                                            }
                                        }.toImmutableList(),
                            ),
                    )
                database.statusDao().update(
                    statusKey = event.postKey,
                    accountType = AccountType.Specific(accountKey),
                    content = updatedData,
                )
            }
            tryRun {
                handler.handle(
                    event = event,
                    updater = this@PostEventHandler,
                )
            }.onFailure {
                // revert cache to original data if handling fails
                if (originalData != null) {
                    database.statusDao().update(
                        statusKey = event.postKey,
                        accountType = AccountType.Specific(accountKey),
                        content = originalData,
                    )
                }
            }
        }
    }

    override suspend fun deleteFromCache(postKey: MicroBlogKey) {
        database.connect {
            database.statusDao().delete(
                statusKey = postKey,
                accountType = AccountType.Specific(accountKey),
            )
            database.pagingTimelineDao().deleteStatus(
                accountKey = accountKey,
                statusKey = postKey,
            )
        }
    }

    override suspend fun updateCache(
        postKey: MicroBlogKey,
        update: suspend (UiTimelineV2) -> UiTimelineV2,
    ) {
        database.connect {
            val currentData =
                database
                    .statusDao()
                    .get(
                        statusKey = postKey,
                        accountType = AccountType.Specific(accountKey),
                    ).firstOrNull()
                    ?.content
            if (currentData != null) {
                val updatedData = update(currentData)
                database.statusDao().update(
                    statusKey = postKey,
                    accountType = AccountType.Specific(accountKey),
                    content = updatedData,
                )
            }
        }
    }

    override suspend fun updateActionMenu(
        postKey: MicroBlogKey,
        newActionMenu: ActionMenu.Item,
    ) {
        database.connect {
            val currentData =
                database
                    .statusDao()
                    .get(
                        statusKey = postKey,
                        accountType = AccountType.Specific(accountKey),
                    ).firstOrNull()
                    ?.content
            if (currentData != null && currentData is UiTimelineV2.Post) {
                val updatedData =
                    currentData.copy(
                        actions =
                            findAndReplaceActionMenu(
                                actions = currentData.actions,
                                newActionMenu = newActionMenu,
                            ),
                    )
                database.statusDao().update(
                    statusKey = postKey,
                    accountType = AccountType.Specific(accountKey),
                    content = updatedData,
                )
            }
        }
    }

    private fun findAndReplaceActionMenu(
        actions: ImmutableList<ActionMenu>,
        newActionMenu: ActionMenu.Item,
    ): ImmutableList<ActionMenu> =
        actions
            .map {
                if (it is ActionMenu.Item && it.updateKey.isNotEmpty() && it.updateKey == newActionMenu.updateKey) {
                    newActionMenu
                } else if (it is ActionMenu.Group) {
                    it.copy(
                        actions = findAndReplaceActionMenu(it.actions, newActionMenu),
                    )
                } else {
                    it
                }
            }.toImmutableList()
}
