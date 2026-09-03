package dev.dimension.flare.data.database.cache

import dev.dimension.flare.data.database.cache.dao.DbTimelinePageIdentity
import dev.dimension.flare.data.database.cache.dao.DbTimelinePageIdentityRoot

/**
 * Loads one fixed-width identity row per timeline item. Dependency revisions are maintained on the
 * timeline row by batched cache writes and database triggers, so this path never materializes
 * referenced status rows.
 */
internal suspend fun CacheDatabase.loadTimelinePageIdentities(
    pagingKey: String,
    offset: Int,
    limit: Int,
): List<DbTimelinePageIdentity> =
    pagingTimelineDao()
        .getTimelinePageIdentityRoots(
            pagingKey = pagingKey,
            offset = offset,
            limit = limit,
        ).toIdentities()

private fun List<DbTimelinePageIdentityRoot>.toIdentities(): List<DbTimelinePageIdentity> =
    map { root ->
        DbTimelinePageIdentity(
            statusId = root.statusId,
            sortId = root.sortId,
            rootContentHash = root.rootContentHash,
            messageRenderHash = root.messageRenderHash,
            statusReferenceHash = root.statusReferenceHash,
            presentationReferenceHash = root.presentationReferenceHash,
            dependencyCount = 0,
            dependencyRevision = root.dependencyRevision,
        )
    }
