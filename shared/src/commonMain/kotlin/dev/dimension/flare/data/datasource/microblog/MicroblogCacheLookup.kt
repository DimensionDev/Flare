package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.annotation.Single

public interface MicroblogCacheLookup {
    public suspend fun findProfiles(keys: Collection<MicroBlogKey>): Map<MicroBlogKey, UiProfile>

    public suspend fun findPost(
        accountType: AccountType,
        statusKey: MicroBlogKey,
    ): UiTimelineV2.Post?
}

@Single(binds = [MicroblogCacheLookup::class])
internal class DatabaseMicroblogCacheLookup(
    private val database: CacheDatabase,
) : MicroblogCacheLookup {
    override suspend fun findProfiles(keys: Collection<MicroBlogKey>): Map<MicroBlogKey, UiProfile> {
        if (keys.isEmpty()) {
            return emptyMap()
        }
        return database
            .userDao()
            .findByKeys(keys.distinct())
            .firstOrNull()
            .orEmpty()
            .associate { it.userKey to it.content }
    }

    override suspend fun findPost(
        accountType: AccountType,
        statusKey: MicroBlogKey,
    ): UiTimelineV2.Post? =
        database
            .statusDao()
            .get(
                statusKey = statusKey,
                accountType = accountType as DbAccountType,
            ).firstOrNull()
            ?.content as? UiTimelineV2.Post
}
