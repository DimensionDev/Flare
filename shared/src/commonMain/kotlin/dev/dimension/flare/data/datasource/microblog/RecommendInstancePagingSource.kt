package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.JoinMastodonService
import dev.dimension.flare.data.network.misskey.JoinMisskeyService
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiInstance

internal class RecommendInstancePagingSource : PagingSource<Int, UiInstance>() {
    override fun getRefreshKey(state: PagingState<Int, UiInstance>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiInstance> {
        return try {
            val instances = runCatching {
                JoinMastodonService.servers().map {
                    UiInstance(
                        name = it.domain,
                        description = it.description,
                        iconUrl = null,
                        domain = it.domain,
                        type = PlatformType.Mastodon,
                        bannerUrl = it.proxiedThumbnail,
                        usersCount = it.totalUsers,
                    )
                }
            }.getOrDefault(emptyList()) + runCatching {
                JoinMisskeyService.instances().instancesInfos.map {
                    UiInstance(
                        name = it.name,
                        description = it.description,
                        iconUrl = it.meta.iconURL,
                        domain = it.url,
                        type = PlatformType.Misskey,
                        bannerUrl = it.meta.bannerURL,
                        usersCount = it.stats.usersCount,
                    )
                }
            }.getOrDefault(emptyList())
            LoadResult.Page(
                data = instances.sortedByDescending { it.usersCount },
                prevKey = null,
                nextKey = null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}