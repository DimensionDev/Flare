package dev.dimension.flare.data.datasource.nostr

import dev.dimension.flare.data.datasource.microblog.MicroblogCacheLookup
import dev.dimension.flare.data.network.nostr.NostrService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2

internal interface NostrCache {
    suspend fun getProfiles(pubKeys: List<String>): Map<String, UiProfile>

    suspend fun getPost(
        accountKey: MicroBlogKey,
        statusKey: MicroBlogKey,
    ): UiTimelineV2.Post?
}

internal class SharedNostrCache(
    private val cacheLookup: MicroblogCacheLookup,
) : NostrCache {
    override suspend fun getProfiles(pubKeys: List<String>): Map<String, UiProfile> {
        if (pubKeys.isEmpty()) {
            return emptyMap()
        }
        return cacheLookup
            .findProfiles(
                pubKeys
                    .distinct()
                    .map { MicroBlogKey(it, NostrService.NOSTR_HOST) },
            ).mapKeys { it.key.id }
    }

    override suspend fun getPost(
        accountKey: MicroBlogKey,
        statusKey: MicroBlogKey,
    ): UiTimelineV2.Post? =
        cacheLookup.findPost(
            accountType = AccountType.Specific(accountKey),
            statusKey = statusKey,
        )
}
