package dev.dimension.flare.data.datasource.microblog.handler

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CoroutineScope
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
                database
                    .pagingTimelineDao()
                    .get(pagingKey, accountType = accountType as DbAccountType)
                    .distinctUntilChanged()
                    .mapNotNull {
                        it?.let {
                            TimelinePagingMapper.toUi(it, pagingKey, false)
                        }
                    }
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
