package dev.dimension.flare.data.datasource.nostr

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.network.nostr.NostrService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.flow.firstOrNull

internal interface NostrCache {
    suspend fun getProfiles(pubKeys: List<String>): Map<String, UiProfile>

    suspend fun getPost(
        accountKey: MicroBlogKey,
        statusKey: MicroBlogKey,
    ): UiTimelineV2.Post?
}

internal class DatabaseNostrCache(
    private val database: CacheDatabase,
) : NostrCache {
    override suspend fun getProfiles(pubKeys: List<String>): Map<String, UiProfile> {
        if (pubKeys.isEmpty()) {
            return emptyMap()
        }
        return database
            .userDao()
            .findByKeys(pubKeys.distinct().map { MicroBlogKey(it, NostrService.NOSTR_HOST) })
            .firstOrNull()
            .orEmpty()
            .associate { it.userKey.id to it.content }
    }

    override suspend fun getPost(
        accountKey: MicroBlogKey,
        statusKey: MicroBlogKey,
    ): UiTimelineV2.Post? =
        database
            .statusDao()
            .get(
                statusKey = statusKey,
                accountType =
                    dev.dimension.flare.model.AccountType
                        .Specific(accountKey),
            ).firstOrNull()
            ?.content as? UiTimelineV2.Post
}
