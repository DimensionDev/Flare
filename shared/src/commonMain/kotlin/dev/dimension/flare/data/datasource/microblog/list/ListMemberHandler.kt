package dev.dimension.flare.data.datasource.microblog.list

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.map
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.model.DbList
import dev.dimension.flare.data.database.cache.model.DbListMember
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.createPagingRemoteMediator
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalPagingApi::class)
internal class ListMemberHandler(
    private val pagingKey: String,
    private val accountKey: MicroBlogKey,
    private val loader: ListMemberLoader,
) : KoinComponent {
    private val accountType: DbAccountType = AccountType.Specific(accountKey)
    private val database: CacheDatabase by inject()
    private val memberPagingKey: String
        get() = "${pagingKey}_members"

    fun listMembers(listId: String) =
        Pager(
            config = pagingConfig,
            remoteMediator =
                createPagingRemoteMediator(
                    pagingKey = "${memberPagingKey}_$listId",
                    database = database,
                    onLoad = { pageSize, request ->
                        loader.loadMembers(
                            pageSize = pageSize,
                            request = request,
                            listId = listId,
                        )
                    },
                    onSave = { request, data ->
                        val listKey = MicroBlogKey(listId, accountKey.host)
                        if (request == PagingRequest.Refresh) {
                            database.listDao().deleteMembersByListKey(listKey)
                        }
                        database.listDao().insertAllMember(
                            data.map { item ->
                                DbListMember(
                                    listKey = listKey,
                                    memberKey = item.userKey,
                                )
                            },
                        )
                        database.userDao().insertAll(data)
                    },
                ),
            pagingSourceFactory = {
                database.listDao().getListMembers(
                    listKey = MicroBlogKey(listId, accountKey.host),
                )
            },
        ).flow.map {
            it.map {
                it.user.render(accountKey)
            }
        }

    fun listMembersListFlow(listId: String) =
        database
            .listDao()
            .getListMembersFlow(
                listKey = MicroBlogKey(listId, accountKey.host),
            ).map { members ->
                members.map { member ->
                    member.user.render(accountKey)
                }
            }

    suspend fun addMember(
        listId: String,
        userKey: MicroBlogKey,
    ) {
        val listKey = MicroBlogKey(listId, accountKey.host)
        tryRun {
            loader.addMember(listId, userKey)
        }.onSuccess { user ->
            database.connect {
                database.listDao().insertAllMember(
                    listOf(
                        DbListMember(
                            listKey = listKey,
                            memberKey = userKey,
                        ),
                    ),
                )
                database.userDao().insertAll(
                    listOf(user),
                )
            }
        }
    }

    suspend fun removeMember(
        listId: String,
        userKey: MicroBlogKey,
    ) {
        val listKey = MicroBlogKey(listId, accountKey.host)
        tryRun {
            loader.removeMember(listId, userKey)
        }.onSuccess {
            database.connect {
                database.listDao().deleteMemberFromList(
                    listKey = listKey,
                    memberKey = userKey,
                )
            }
        }
    }

    private val userListsPagingKey: String
        get() = "${pagingKey}_user_lists"

    fun userLists(userKey: MicroBlogKey) =
        Cacheable(
            fetchSource = {
                tryRun {
                    val result =
                        loader.loadUserLists(
                            pageSize = 100,
                            request = PagingRequest.Refresh,
                            userKey = userKey,
                        )
                    val data = result.data
                    database.connect {
                        database.listDao().insertAllList(
                            data.map { item ->
                                DbList(
                                    listKey = MicroBlogKey(item.id, accountKey.host),
                                    accountType = accountType,
                                    content = DbList.ListContent(item),
                                )
                            },
                        )
                        database.listDao().insertAllMember(
                            data.map { item ->
                                DbListMember(
                                    listKey = MicroBlogKey(item.id, accountKey.host),
                                    memberKey = userKey,
                                )
                            },
                        )
                    }
                }
            },
            cacheSource = {
                database
                    .listDao()
                    .getUserByKeyFlow(userKey)
                    .map {
                        it.listMemberships.map {
                            it.list.content.data
                        }
                    }
            },
        )
}
