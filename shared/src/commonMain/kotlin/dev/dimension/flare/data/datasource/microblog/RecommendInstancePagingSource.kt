package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.network.mastodon.JoinMastodonService
import dev.dimension.flare.data.network.misskey.JoinMisskeyService
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.logoUrl
import dev.dimension.flare.ui.model.UiInstance

internal class RecommendInstancePagingSource : PagingSource<Int, UiInstance>() {
    override fun getRefreshKey(state: PagingState<Int, UiInstance>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UiInstance> =
        try {
            val instances =
                runCatching {
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
                }.getOrDefault(emptyList()) +
                    runCatching {
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
            val bsky =
                listOf(
                    UiInstance(
                        name = "Bluesky",
                        description =
                            "The web. Email. RSS feeds. XMPP chats. " +
                                "What all these technologies had in common is they allowed people to freely interact " +
                                "and create content, without a single intermediary.",
                        iconUrl = PlatformType.Bluesky.logoUrl,
                        domain = "bsky.social",
                        type = PlatformType.Bluesky,
                        bannerUrl = null,
                        usersCount = 0,
                    ),
                    UiInstance(
                        name = "X",
                        description =
                            "From breaking news and entertainment to sports and politics," +
                                " get the full story with all the live commentary.",
                        iconUrl = PlatformType.xQt.logoUrl,
                        domain = "x.com",
                        type = PlatformType.xQt,
                        bannerUrl = null,
                        usersCount = 0,
                    ),
                )
            LoadResult.Page(
                data = bsky + instances.sortedByDescending { it.usersCount },
                prevKey = null,
                nextKey = null,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
}
