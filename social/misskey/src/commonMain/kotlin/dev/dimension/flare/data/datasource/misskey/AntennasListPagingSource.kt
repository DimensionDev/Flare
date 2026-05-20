package dev.dimension.flare.data.datasource.misskey

import dev.dimension.flare.common.tryRun
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.mapper.render

public class AntennasListPagingSource(
    private val service: MisskeyService,
) : RemoteLoader<UiList.Antenna> {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiList.Antenna> {
        if (request is PagingRequest.Prepend || request is PagingRequest.Append) {
            return PagingResult(
                endOfPaginationReached = true,
            )
        }
        val data =
            tryRun {
                service.antennasList().map {
                    it.render()
                }
            }.getOrThrow()
        return PagingResult(
            endOfPaginationReached = true,
            data = data,
        )
    }
}
