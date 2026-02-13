package dev.dimension.flare.data.datasource.microblog.list

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.flatMap
import androidx.paging.map
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.model.DbList
import dev.dimension.flare.data.database.cache.model.DbListMember
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

    fun listMembers(listKey: MicroBlogKey) =
        Pager(
            config = pagingConfig,
            remoteMediator =
                createPagingRemoteMediator(
                    pagingKey = memberPagingKey,
                    database = database,
                    onLoad = { pageSize, request ->
                        loader.loadMembers(
                            pageSize = pageSize,
                            request = request,
                            listKey = listKey,
                        )
                    },
                    onSave = { request, data ->
                        database.listDao().insertAll(
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
                    listKey = listKey,
                )
            },
        ).flow.map {
            it.map {
                it.user.render(accountKey)
            }
        }

    fun listMembersListFlow(listKey: MicroBlogKey) =
        database
            .listDao()
            .getListMembersFlow(
                listKey = listKey,
            ).map { members ->
                members.map { member ->
                    member.user.render(accountKey)
                }
            }

    suspend fun addMember(
        listKey: MicroBlogKey,
        userKey: MicroBlogKey,
    ) {
        tryRun {
            loader.addMember(listKey, userKey)
        }.onSuccess { user ->
            database.connect {
                database.listDao().insertAll(
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
        listKey: MicroBlogKey,
        userKey: MicroBlogKey,
    ) {
        tryRun {
            loader.removeMember(listKey, userKey)
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
        Pager(
            config = pagingConfig,
            pagingSourceFactory = {
                database.listDao().getUserByKey(userKey)
            },
            remoteMediator =
                createPagingRemoteMediator(
                    pagingKey = userListsPagingKey,
                    database = database,
                    onLoad = { pageSize, request ->
                        loader.loadUserLists(
                            pageSize = pageSize,
                            request = request,
                            userKey = userKey,
                        )
                    },
                    onSave = { request, data ->
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
                                DbListMember(
                                    listKey = item.key,
                                    memberKey = userKey,
                                )
                            },
                        )
                    },
                ),
        ).flow.map {
            it.flatMap {
                it.listMemberships.map {
                    it.list.content.data
                }
            }
        }
}
