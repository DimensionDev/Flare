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
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
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
                        if (request == PagingRequest.Refresh) {
                            database.listDao().deleteByPagingKey(pagingKey)
                        }
                        database.listDao().insertAllList(
                            data.map { item ->
                                DbList(
                                    listKey = MicroBlogKey(item.id, accountKey.host),
                                    accountType = accountType,
                                    content = DbList.ListContent(item),
                                )
                            },
                        )

                        database.listDao().insertAllPaging(
                            data.map { item ->
                                DbListPaging(
                                    accountType = accountType,
                                    pagingKey = pagingKey,
                                    listKey = MicroBlogKey(item.id, accountKey.host),
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

    val cacheData by lazy {
        database.listDao().getListKeysFlow(pagingKey).map {
            it.map {
                it.list.content.data
            }
        }
    }

    fun listInfo(listId: String): CacheData<UiList> {
        val listKey = MicroBlogKey(listId, accountKey.host)
        return Cacheable(
            fetchSource = {
                val info = loader.info(listId)
                database.connect {
                    database.listDao().insertAllList(
                        listOf(
                            DbList(
                                listKey = MicroBlogKey(info.id, accountKey.host),
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
    }

    suspend fun create(metaData: ListMetaData) {
        tryRun {
            loader.create(metaData)
        }.onSuccess { result ->
            database.connect {
                database.listDao().insertAllList(
                    listOf(
                        DbList(
                            listKey = MicroBlogKey(result.id, accountKey.host),
                            accountType = accountType,
                            content = DbList.ListContent(result),
                        ),
                    ),
                )
                database.listDao().insertAllPaging(
                    listOf(
                        DbListPaging(
                            accountType = accountType,
                            pagingKey = pagingKey,
                            listKey = MicroBlogKey(result.id, accountKey.host),
                        ),
                    ),
                )
            }
        }
    }

    suspend fun update(
        listId: String,
        metaData: ListMetaData,
    ) {
        val listKey = MicroBlogKey(listId, accountKey.host)
        tryRun {
            loader.update(listId, metaData)
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

    suspend fun delete(listId: String) {
        val listKey = MicroBlogKey(listId, accountKey.host)
        tryRun {
            loader.delete(listId)
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

    suspend fun insertToDatabase(data: UiList) {
        val listKey = MicroBlogKey(data.id, accountKey.host)
        database.connect {
            database.listDao().insertAllList(
                listOf(
                    DbList(
                        listKey = listKey,
                        accountType = AccountType.Specific(accountKey),
                        content = DbList.ListContent(data),
                    ),
                ),
            )

            database.listDao().insertAllPaging(
                listOf(
                    DbListPaging(
                        accountType = AccountType.Specific(accountKey),
                        pagingKey = pagingKey,
                        listKey = listKey,
                    ),
                ),
            )
        }
    }

    suspend fun withDatabase(block: suspend (update: suspend (UiList) -> Unit) -> Unit) {
        block.invoke { data ->
            val listKey = MicroBlogKey(data.id, accountKey.host)
            database.connect {
                database.listDao().updateListContent(
                    listKey = listKey,
                    accountType = AccountType.Specific(accountKey),
                    content = DbList.ListContent(data),
                )
            }
        }
    }
}
