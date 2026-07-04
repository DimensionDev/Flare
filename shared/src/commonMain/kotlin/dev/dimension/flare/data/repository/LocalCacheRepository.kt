package dev.dimension.flare.data.repository

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.contentPostOrNull
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface LocalCacheRepository {
    public suspend fun searchPosts(
        query: String,
        limit: Int,
    ): List<UiTimelineV2.Post>

    public suspend fun listViewedPosts(limit: Int): List<UiTimelineV2.Post>

    public suspend fun searchUsers(
        query: String,
        limit: Int,
    ): List<UiProfile>

    public suspend fun listViewedUsers(limit: Int): List<UiProfile>
}

@Single(binds = [LocalCacheRepository::class])
internal class DatabaseLocalCacheRepository(
    private val cacheDatabase: CacheDatabase,
) : LocalCacheRepository {
    override suspend fun searchPosts(
        query: String,
        limit: Int,
    ): List<UiTimelineV2.Post> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return emptyList()
        }
        return cacheDatabase
            .pagingTimelineDao()
            .searchCachedStatuses(
                query = normalizedQuery.toLikePattern(),
                limit = limit.normalizedLocalCacheLimit(),
            ).mapNotNull {
                it.status.data.content
                    .contentPostOrNull()
            }.distinctBy { it.platformType to it.statusKey }
    }

    override suspend fun listViewedPosts(limit: Int): List<UiTimelineV2.Post> =
        cacheDatabase
            .pagingTimelineDao()
            .getStatusHistoryPage(
                pagingKey = STATUS_HISTORY_PAGING_KEY,
                limit = limit.normalizedLocalCacheLimit(),
            ).mapNotNull {
                it.status.status.data.content
                    .contentPostOrNull()
            }.distinctBy { it.platformType to it.statusKey }

    override suspend fun searchUsers(
        query: String,
        limit: Int,
    ): List<UiProfile> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return emptyList()
        }
        return cacheDatabase
            .userDao()
            .searchUser(
                query = normalizedQuery.toLikePattern(),
                limit = limit.normalizedLocalCacheLimit(),
            ).map { it.content }
            .distinctBy { it.platformType to it.key }
    }

    override suspend fun listViewedUsers(limit: Int): List<UiProfile> =
        cacheDatabase
            .userDao()
            .getUserHistory(limit.normalizedLocalCacheLimit())
            .asSequence()
            .filter { it.data.accountType is AccountType.Specific }
            .map { it.user.content }
            .distinctBy { it.platformType to it.key }
            .toList()
}

private fun Int.normalizedLocalCacheLimit(): Int = coerceIn(1, MAX_LOCAL_CACHE_QUERY_LIMIT)

private fun String.toLikePattern(): String =
    buildString {
        append('%')
        this@toLikePattern.forEach { char ->
            when (char) {
                '\\', '%', '_' -> {
                    append('\\')
                    append(char)
                }

                else -> {
                    append(char)
                }
            }
        }
        append('%')
    }

private const val MAX_LOCAL_CACHE_QUERY_LIMIT = 100

internal const val STATUS_HISTORY_PAGING_KEY = "status_history"
