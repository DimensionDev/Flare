package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingState
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.network.mastodon.JoinMastodonService
import dev.dimension.flare.data.network.mastodon.MastodonInstanceService
import dev.dimension.flare.data.network.misskey.JoinMisskeyService
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.logoUrl
import dev.dimension.flare.ui.model.UiInstance
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal class RecommendInstancePagingSource : BasePagingSource<Int, UiInstance>() {
    override fun getRefreshKey(state: PagingState<Int, UiInstance>): Int? = null

    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiInstance> {
        val instances =
            coroutineScope {
                listOf(
                    async {
                        tryRun {
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
                        }.getOrDefault(emptyList())
                    },
                    async {
                        tryRun {
                            JoinMisskeyService.instances().instancesInfos.map {
                                UiInstance(
                                    name = it.name,
                                    description = it.description,
                                    iconUrl = it.meta?.iconURL,
                                    domain = it.url,
                                    type = PlatformType.Misskey,
                                    bannerUrl = it.meta?.bannerURL,
                                    usersCount =
                                        it.stats?.usersCount ?: it.nodeinfo
                                            ?.usage
                                            ?.users
                                            ?.total ?: 0,
                                )
                            }
                        }.getOrDefault(emptyList())
                    },
                    async {
                        tryRun {
                            MastodonInstanceService("https://pawoo.net/").instance().let {
                                listOf(
                                    UiInstance(
                                        name = it.domain ?: "pawoo.net",
                                        description = it.title,
                                        iconUrl =
                                            it.thumbnail?.url
                                                ?: PlatformType.Mastodon.logoUrl,
                                        domain = it.domain ?: "pawoo.net",
                                        type = PlatformType.Mastodon,
                                        bannerUrl =
                                            it.thumbnail?.url
                                                ?: PlatformType.Mastodon.logoUrl,
                                        usersCount = it.usage?.users?.activeMonth ?: 0,
                                    ),
                                )
                            }
                        }.getOrDefault(emptyList())
                    },
                ).awaitAll().flatMap { it }
            }
        val mstdnJP =
            instances.firstOrNull { it.domain == "mstdn.jp" } ?: run {
                UiInstance(
                    name = "mstdn.jp",
                    description = "mstdn.jp",
                    iconUrl = PlatformType.Mastodon.logoUrl,
                    domain = "mstdn.jp",
                    type = PlatformType.Mastodon,
                    bannerUrl = null,
                    usersCount = 0,
                )
            }
        val pawoo =
            instances.firstOrNull { it.domain == "pawoo.net" } ?: run {
                UiInstance(
                    name = "pawoo.net",
                    description = "pawoo.net",
                    iconUrl = PlatformType.Mastodon.logoUrl,
                    domain = "pawoo.net",
                    type = PlatformType.Mastodon,
                    bannerUrl = null,
                    usersCount = 0,
                )
            }
        val extra =
            listOf(
                mstdnJP,
                pawoo,
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
            )
        return LoadResult.Page(
            data = extra + (instances.sortedByDescending { it.usersCount }.filter { it !in extra }),
            prevKey = null,
            nextKey = null,
        )
    }
}
