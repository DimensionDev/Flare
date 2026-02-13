package dev.dimension.flare.data.datasource.microblog.list

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.map
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.model.DbList
import dev.dimension.flare.data.database.cache.model.DbListPaging
import dev.dimension.flare.data.datasource.microblog.paging.createPagingRemoteMediator
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalPagingApi::class)
internal class ListHandler(
    private val pagingKey: String,
    private val accountKey: MicroBlogKey,
    private val loader: ListLoader,
) : KoinComponent {
    private val accountType: DbAccountType = AccountType.Specific(accountKey)
    private val database: CacheDatabase by inject()

    val supportedMetaData: ImmutableList<ListMetaDataType> by lazy {
        loader.supportedMetaData
    }
    val data by lazy {
        Pager(
            config = pagingConfig,
            remoteMediator =
                createPagingRemoteMediator(
                    pagingKey = pagingKey,
                    database = database,
                    onLoad = { pageSize, request ->
                        loader.load(pageSize, request)
                    },
                    onSave = { request, data ->
                        database.listDao().deleteByPagingKey(pagingKey)
                        database.listDao().insertAll(
                            data.map { item ->
                                DbList(
                                    listKey = item.key,
                                    accountType = accountType,
                                    content = DbList.ListContent(item),
                                )
                            },
                        )

                        database.listDao().insertAll(
                            data.map { item ->
                                DbListPaging(
                                    accountType = accountType,
                                    pagingKey = pagingKey,
                                    listKey = item.key,
                                )
                            },
                        )
                    },
                ),
            pagingSourceFactory = {
                database.listDao().getPagingSource(
                    pagingKey = pagingKey,
                )
            },
        ).flow.map {
            it.map {
                it.list.content.data
            }
        }
    }

    fun listInfo(listKey: MicroBlogKey): CacheData<UiList> =
        Cacheable(
            fetchSource = {
                val info = loader.info(listKey)
                database.connect {
                    database.listDao().insertAll(
                        listOf(
                            DbList(
                                listKey = info.key,
                                accountType = accountType,
                                content = DbList.ListContent(info),
                            ),
                        ),
                    )
                }
            },
            cacheSource = {
                database
                    .listDao()
                    .getList(
                        listKey = listKey,
                        accountType = accountType,
                    ).mapNotNull { dbList ->
                        dbList?.content?.data
                    }
            },
        )

    suspend fun create(metaData: ListMetaData) {
        tryRun {
            loader.create(metaData)
        }.onSuccess { result ->
            database.connect {
                database.listDao().insertAll(
                    listOf(
                        DbList(
                            listKey = result.key,
                            accountType = accountType,
                            content = DbList.ListContent(result),
                        ),
                    ),
                )
                database.listDao().insertAll(
                    listOf(
                        DbListPaging(
                            accountType = accountType,
                            pagingKey = pagingKey,
                            listKey = result.key,
                        ),
                    ),
                )
            }
        }
    }

    suspend fun update(
        listKey: MicroBlogKey,
        metaData: ListMetaData,
    ) {
        tryRun {
            loader.update(listKey, metaData)
        }.onSuccess { result ->
            database.connect {
                database.listDao().updateListContent(
                    listKey = listKey,
                    accountType = accountType,
                    content = DbList.ListContent(result),
                )
            }
        }
    }

    suspend fun delete(listKey: MicroBlogKey) {
        tryRun {
            loader.delete(listKey)
        }.onSuccess {
            database.connect {
                database.listDao().deleteByListKey(
                    listKey = listKey,
                    accountType = accountType,
                )
                database.listDao().deletePagingByListKey(
                    listKey = listKey,
                    accountType = accountType,
                )
            }
        }
    }
}
