package dev.dimension.flare.data.datasource.microblog.handler

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class PostHandler(
    val accountType: AccountType,
    val loader: PostLoader,
) : KoinComponent {
    private val database: CacheDatabase by inject()
    private val coroutineScope: CoroutineScope by inject()

    fun post(postKey: MicroBlogKey): Cacheable<UiTimelineV2> {
        val pagingKey = "post_only_$postKey"
        return Cacheable(
            fetchSource = {
                val result = loader.status(postKey)
                database.connect {
                    val item =
                        TimelinePagingMapper.toDb(
                            result,
                            pagingKey,
                        )
                    saveToDatabase(database, listOf(item))
                }
            },
            cacheSource = {
                val dbAccountType = accountType as DbAccountType
                database
                    .statusDao()
                    .getWithReferences(postKey, dbAccountType)
                    .combine(database.pagingTimelineDao().get(pagingKey, accountType = dbAccountType)) { status, paging ->
                        when {
                            paging != null -> TimelinePagingMapper.toUi(paging, pagingKey, false)
                            status != null ->
                                TimelinePagingMapper.toUi(
                                    DbPagingTimelineWithStatus(
                                        timeline =
                                            DbPagingTimeline(
                                                pagingKey = pagingKey,
                                                statusKey = postKey,
                                                sortId = 0,
                                            ),
                                        status = status,
                                    ),
                                    pagingKey,
                                    false,
                                )
                            else -> null
                        }
                    }.distinctUntilChanged()
                    .mapNotNull { it }
            },
        )
    }

    fun delete(postKey: MicroBlogKey) {
        coroutineScope.launch {
            tryRun {
                loader.deleteStatus(postKey)
            }.onSuccess {
                database.connect {
                    database.statusDao().delete(
                        statusKey = postKey,
                        accountType = accountType as DbAccountType,
                    )
                    database.statusReferenceDao().delete(postKey)
                    database.pagingTimelineDao().deleteStatus(
                        accountType = accountType as DbAccountType,
                        statusKey = postKey,
                    )
                }
            }
        }
    }
}
