package dev.dimension.flare.data.local

import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbKeywordFilter
import dev.dimension.flare.ui.model.UiKeywordFilter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

public class LocalFilterRepository(
    private val database: AppDatabase,
    private val coroutineScope: CoroutineScope,
) {
    public fun getAllFlow(): Flow<ImmutableList<UiKeywordFilter>> =
        database
            .keywordFilterDao()
            .selectAll()
            .map {
                it
                    .map {
                        it.toUiKeywordFilter()
                    }.toImmutableList()
            }

    public fun getFlow(
        forTimeline: Boolean = false,
        forNotification: Boolean = false,
        forSearch: Boolean = false,
    ): Flow<List<KeywordFilterPattern>> =
        database
            .keywordFilterDao()
            .selectNotExpiredFor(
                currentTime = Clock.System.now().toEpochMilliseconds(),
                forTimeline = if (forTimeline) 1L else 0L,
                forNotification = if (forNotification) 1L else 0L,
                forSearch = if (forSearch) 1L else 0L,
            ).map {
                it.map(DbKeywordFilter::toKeywordFilterPattern)
            }

    public fun add(
        keyword: String,
        forTimeline: Boolean,
        forNotification: Boolean,
        forSearch: Boolean,
        expiredAt: Instant?,
        isRegex: Boolean,
    ): Job =
        coroutineScope.launch {
            database.keywordFilterDao().insert(
                DbKeywordFilter(
                    keyword = keyword,
                    for_timeline = if (forTimeline) 1L else 0L,
                    for_notification = if (forNotification) 1L else 0L,
                    for_search = if (forSearch) 1L else 0L,
                    expired_at = expiredAt?.toEpochMilliseconds() ?: 0L,
                    is_regex = if (isRegex) 1L else 0L,
                ),
            )
        }

    public fun update(
        keyword: String,
        forTimeline: Boolean,
        forNotification: Boolean,
        forSearch: Boolean,
        expiredAt: Instant?,
        isRegex: Boolean,
    ): Job =
        coroutineScope.launch {
            database.keywordFilterDao().update(
                forTimeline = if (forTimeline) 1L else 0L,
                forNotification = if (forNotification) 1L else 0L,
                forSearch = if (forSearch) 1L else 0L,
                expiredAt = expiredAt?.toEpochMilliseconds() ?: 0L,
                isRegex = if (isRegex) 1L else 0L,
                keyword = keyword,
            )
        }

    public fun delete(filter: String): Job =
        coroutineScope.launch {
            database.keywordFilterDao().deleteByKeyword(filter)
        }

    public fun clear(): Job =
        coroutineScope.launch {
            database.keywordFilterDao().deleteAll()
        }
}

public data class KeywordFilterPattern(
    val keyword: String,
    val isRegex: Boolean,
    val regex: Regex? = null,
)

private fun DbKeywordFilter.toUiKeywordFilter() =
    UiKeywordFilter(
        keyword = keyword,
        forTimeline = for_timeline == 1L,
        forNotification = for_notification == 1L,
        forSearch = for_search == 1L,
        expiredAt =
            expired_at
                .takeIf { it > 0L }
                ?.let { Instant.fromEpochMilliseconds(it) },
        isRegex = is_regex == 1L,
    )

private fun DbKeywordFilter.toKeywordFilterPattern(): KeywordFilterPattern {
    val isRegex = is_regex == 1L
    return KeywordFilterPattern(
        keyword = keyword,
        isRegex = isRegex,
        regex = if (isRegex) keyword.toRegexOrNull() else null,
    )
}

private fun String.toRegexOrNull(): Regex? =
    try {
        Regex(this, setOf(RegexOption.IGNORE_CASE))
    } catch (_: IllegalArgumentException) {
        null
    }
