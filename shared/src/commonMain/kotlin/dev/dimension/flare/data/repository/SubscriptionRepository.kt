package dev.dimension.flare.data.repository

import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.render.toUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

@HiddenFromObjC
public data class SubscriptionSourceInput(
    val id: Int = 0,
    val url: String,
    val title: String?,
    val icon: String?,
    val displayMode: RssDisplayMode = RssDisplayMode.FULL_CONTENT,
    val lastUpdateMillis: Long = 0,
    val type: SubscriptionType = SubscriptionType.RSS,
)

@HiddenFromObjC
public interface SubscriptionRepository {
    public fun observeAll(): Flow<List<UiRssSource>>

    public fun observe(id: Int): Flow<UiRssSource>

    public suspend fun findByUrl(url: String): List<UiRssSource>

    public suspend fun findByUrlAndType(
        url: String,
        type: SubscriptionType,
    ): List<UiRssSource>

    public suspend fun upsert(input: SubscriptionSourceInput): UiRssSource

    public suspend fun upsertAll(inputs: List<SubscriptionSourceInput>): List<UiRssSource>

    public suspend fun delete(id: Int): UiRssSource?
}

@Single(binds = [SubscriptionRepository::class])
internal class DatabaseSubscriptionRepository(
    private val appDatabase: AppDatabase,
) : SubscriptionRepository {
    override fun observeAll(): Flow<List<UiRssSource>> =
        appDatabase
            .rssSourceDao()
            .getAll()
            .map { sources -> sources.map { it.render() } }

    override fun observe(id: Int): Flow<UiRssSource> =
        appDatabase
            .rssSourceDao()
            .get(id)
            .map { it.render() }

    override suspend fun findByUrl(url: String): List<UiRssSource> =
        appDatabase
            .rssSourceDao()
            .getByUrl(url)
            .map { it.render() }

    override suspend fun findByUrlAndType(
        url: String,
        type: SubscriptionType,
    ): List<UiRssSource> =
        appDatabase
            .rssSourceDao()
            .getByUrlAndType(url, type)
            .map { it.render() }

    override suspend fun upsert(input: SubscriptionSourceInput): UiRssSource {
        appDatabase.rssSourceDao().insert(input.toDatabase())
        return input.toUiRssSource()
    }

    override suspend fun upsertAll(inputs: List<SubscriptionSourceInput>): List<UiRssSource> {
        appDatabase.rssSourceDao().insertAll(inputs.map { it.toDatabase() })
        return inputs.map { it.toUiRssSource() }
    }

    override suspend fun delete(id: Int): UiRssSource? {
        val source =
            appDatabase
                .rssSourceDao()
                .getAll()
                .first()
                .firstOrNull { it.id == id }
                ?.render()
        appDatabase.rssSourceDao().delete(id)
        return source
    }
}

private fun SubscriptionSourceInput.toDatabase(): DbRssSources =
    DbRssSources(
        id = id,
        url = url,
        title = title,
        icon = icon,
        displayMode = displayMode,
        lastUpdate = lastUpdateMillis,
        type = type,
    )

@HiddenFromObjC
public fun SubscriptionSourceInput.toUiRssSource(): UiRssSource =
    UiRssSource(
        id = id,
        url = url,
        title = title,
        favIcon = icon,
        displayMode = displayMode,
        lastUpdate = Instant.fromEpochMilliseconds(lastUpdateMillis).toUi(),
        type = type,
    )
