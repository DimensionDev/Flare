package dev.dimension.flare.data.datasource.vvo

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.network.vvo.model.VVOResponse
import dev.dimension.flare.data.platform.VVO_PLATFORM_ID
import dev.dimension.flare.data.platform.VvoPlatformSpec
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiText
import kotlinx.coroutines.CancellationException

internal class VVOGroupPagingSource(
    private val service: VVOService,
    private val accountKey: MicroBlogKey,
) : PagingSource<Int, TimelineCandidate<*>>() {
    override fun getRefreshKey(state: PagingState<Int, TimelineCandidate<*>>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TimelineCandidate<*>> =
        try {
            service.ensureGroupLogin(accountKey)
            val groups = service.getGroupList().requireGroupData(accountKey).groups
            LoadResult.Page(
                data =
                    groups.distinctBy { it.gid }.map { group ->
                        check(group.gid.isNotBlank() && group.name.isNotBlank()) { "Invalid Weibo group" }
                        VvoPlatformSpec.groupTimelineSpec.candidate(
                            data = TimelineSpec.AccountResourceData(accountKey, group.gid),
                            title = UiText.Raw(group.name),
                        )
                    },
                prevKey = null,
                nextKey = null,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            LoadResult.Error(error)
        }
}

internal suspend fun VVOService.ensureGroupLogin(accountKey: MicroBlogKey) {
    if (config().data?.login != true) {
        throw LoginExpiredException(accountKey = accountKey, platformId = VVO_PLATFORM_ID)
    }
}

internal fun <T : Any> VVOResponse<T>.requireGroupData(accountKey: MicroBlogKey): T {
    if (ok == -100L) {
        throw LoginExpiredException(accountKey = accountKey, platformId = VVO_PLATFORM_ID)
    }
    check(ok == 1L) { "Weibo group request failed" }
    return checkNotNull(data) { "Invalid Weibo group response" }
}
