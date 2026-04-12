package dev.dimension.flare.data.datasource.microblog.handler

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.map
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.mapper.upsertUsers
import dev.dimension.flare.data.database.cache.model.DbList
import dev.dimension.flare.data.database.cache.model.DbListMember
import dev.dimension.flare.data.datasource.microblog.loader.ListMemberLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.createPagingRemoteMediator
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import kotlinx.coroutines.flow.Flow
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

    fun listMembers(listId: String): Flow<PagingData<UiProfile>> {
        val listKey = MicroBlogKey(listId, accountKey.host)
        return Pager(
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
                        database.connect {
                            if (request == PagingRequest.Refresh) {
                                database.listDao().deleteMembersByListKey(listKey)
                            }
                            database.listDao().insertAllMember(
                                data.map { item ->
                                    DbListMember(
                                        listKey = listKey,
                                        memberKey = item.key,
                                    )
                                },
                            )
                            database.upsertUsers(data.map { it.toDbUser() })
                        }
                    },
                ),
            pagingSourceFactory = {
                database.listDao().getListMembers(
                    listKey = listKey,
                )
            },
        ).flow.map {
            it.map {
                it.user.content
            }
        }
    }

    fun listMembersListFlow(listId: String) =
        database
            .listDao()
            .getListMembersFlow(
                listKey = MicroBlogKey(listId, accountKey.host),
            ).map { members ->
                members.map { member ->
                    member.user.content
                }
            }

    suspend fun addMember(
        listId: String,
        userKey: MicroBlogKey,
    ) {
        val listKey = MicroBlogKey(listId, accountKey.host)
        database.connect {
            database.listDao().insertAllMember(
                listOf(
                    DbListMember(
                        listKey = listKey,
                        memberKey = userKey,
                    ),
                ),
            )
        }
        tryRun {
            loader.addMember(listId, userKey)
        }.onSuccess { user ->
            database.connect {
                database.upsertUsers(
                    listOf(user.toDbUser()),
                )
            }
        }.onFailure {
            database.connect {
                database.listDao().deleteMemberFromList(
                    listKey = listKey,
                    memberKey = userKey,
                )
            }
        }
    }

    suspend fun removeMember(
        listId: String,
        userKey: MicroBlogKey,
    ) {
        val listKey = MicroBlogKey(listId, accountKey.host)
        database.connect {
            database.listDao().deleteMemberFromList(
                listKey = listKey,
                memberKey = userKey,
            )
        }
        tryRun {
            loader.removeMember(listId, userKey)
        }.onFailure {
            database.connect {
                database.listDao().insertAllMember(
                    listOf(
                        DbListMember(
                            listKey = listKey,
                            memberKey = userKey,
                        ),
                    ),
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
