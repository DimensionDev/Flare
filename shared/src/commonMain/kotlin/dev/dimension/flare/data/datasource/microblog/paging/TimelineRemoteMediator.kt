package dev.dimension.flare.data.datasource.microblog.paging

import SnowflakeIdGenerator
import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusReference
import dev.dimension.flare.data.database.cache.model.DbStatusReferenceWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatusUserReference
import dev.dimension.flare.data.database.cache.model.DbStatusUserReferenceWithUser
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlin.uuid.Uuid

@OptIn(ExperimentalPagingApi::class)
internal class TimelineRemoteMediator(
    private val loader: CacheableRemoteLoader<UiTimelineV2>,
    private val database: CacheDatabase,
) : BasePagingRemoteMediator<DbPagingTimelineWithStatus, DbPagingTimelineWithStatus>(
        database = database,
    ),
    RemoteLoader<DbPagingTimelineWithStatus> {
    override val pagingKey: String
        get() = loader.pagingKey

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<DbPagingTimelineWithStatus> {
        val result =
            timeline(
                pageSize = pageSize,
                request = request,
            )
        val data =
            result.data.map {
                mapping(
                    data = it,
                    pagingKey = pagingKey,
                )
            }
        return PagingResult(
            data = data,
            nextKey = result.nextKey,
            previousKey = result.previousKey,
        )
    }

    companion object {
        suspend fun mapping(
            data: UiTimelineV2,
            pagingKey: String,
        ): DbPagingTimelineWithStatus =
            DbPagingTimelineWithStatus(
                timeline =
                    DbPagingTimeline(
                        pagingKey = pagingKey,
                        statusKey = data.statusKey,
                        sortId = -SnowflakeIdGenerator.nextId(),
                    ),
                status =
                    DbStatusWithReference(
                        status = mappingDbStatusWithUser(data),
                        references =
                            when (data) {
                                is UiTimelineV2.Feed -> emptyList()
                                is UiTimelineV2.Message -> emptyList()
                                is UiTimelineV2.Post ->
                                    data.quote.map {
                                        mappingDbStatusReferenceWithStatus(
                                            data = it,
                                            referenceType = ReferenceType.Quote,
                                            rootStatusKey = data.statusKey,
                                        )
                                    } +
                                        data.parents.map {
                                            mappingDbStatusReferenceWithStatus(
                                                data = it,
                                                referenceType = ReferenceType.Reply,
                                                rootStatusKey = data.statusKey,
                                            )
                                        }

                                is UiTimelineV2.User -> emptyList()
                                is UiTimelineV2.UserList ->
                                    listOfNotNull(
                                        data.post?.let {
                                            mappingDbStatusReferenceWithStatus(
                                                data = it,
                                                referenceType = ReferenceType.Quote,
                                                rootStatusKey = data.statusKey,
                                            )
                                        },
                                    )
                            },
                    ),
            )

        private fun mappingDbStatusReferenceWithStatus(
            data: UiTimelineV2,
            referenceType: ReferenceType,
            rootStatusKey: MicroBlogKey,
        ) = DbStatusReferenceWithStatus(
            reference =
                DbStatusReference(
                    referenceType = referenceType,
                    statusKey = rootStatusKey,
                    referenceStatusKey = data.statusKey,
                    _id = Uuid.random().toString(),
                ),
            status = mappingDbStatusWithUser(data),
        )

        private fun mappingDbStatusWithUser(data: UiTimelineV2): DbStatusWithUser {
            val user =
                if (data is UiTimelineV2.Post) {
                    listOfNotNull(data.user)
                } else if (data is UiTimelineV2.User) {
                    listOfNotNull(data.value)
                } else if (data is UiTimelineV2.UserList) {
                    data.users
                } else {
                    emptyList()
                }
            return DbStatusWithUser(
                data =
                    DbStatus(
                        statusKey = data.statusKey,
                        content = data,
                        accountType = data.accountType as DbAccountType,
                        text = data.searchText,
                    ),
                references =
                    user.map {
                        DbStatusUserReferenceWithUser(
                            reference =
                                DbStatusUserReference(
                                    statusKey = data.statusKey,
                                    referenceUserKey = it.key,
                                    _id = Uuid.random().toString(),
                                ),
                            user =
                                it.toDbUser(),
                        )
                    },
            )
        }
    }

    suspend fun timeline(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> =
        loader.load(
            pageSize = pageSize,
            request = request,
        )

    override suspend fun onSaveCache(
        request: PagingRequest,
        data: List<DbPagingTimelineWithStatus>,
    ) {
        if (request is PagingRequest.Refresh) {
            data.groupBy { it.timeline.pagingKey }.keys.forEach { key ->
                database
                    .pagingTimelineDao()
                    .delete(pagingKey = key)
            }
        }
        saveToDatabase(database, data)
    }
}
