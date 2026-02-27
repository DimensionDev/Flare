package dev.dimension.flare.data.datasource.microblog.handler

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
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
}
