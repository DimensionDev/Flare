package dev.dimension.flare.data.datasource.misskey

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.BaseListRemoteMediator
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.mapper.render

internal class AntennasListRemoteMediator(
    private val service: MisskeyService,
    database: CacheDatabase,
    accountKey: MicroBlogKey,
) : BaseListRemoteMediator(database) {
    override val accountType = AccountType.Specific(accountKey)
    override val pagingKey = "antennas_list_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: Request,
    ): Result =
        service
            .antennasList()
            .map {
                it.render()
            }.let { antennas ->
                PagingResult(
                    data = antennas,
                )
            }
}
