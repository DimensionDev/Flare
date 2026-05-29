package dev.dimension.flare.ui.presenter.home.rss

import dev.dimension.flare.data.repository.SubscriptionRepository
import dev.dimension.flare.data.repository.SubscriptionSourceInput
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.render.toUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

internal class FakeSubscriptionRepository(
    initialSources: List<SubscriptionSourceInput> = emptyList(),
) : SubscriptionRepository {
    private var nextId = 1
    private val sources =
        MutableStateFlow(
            initialSources.map { input ->
                input.toUiRssSource(input.id.takeIf { it != 0 } ?: nextId++)
            },
        )

    val currentSources: List<UiRssSource>
        get() = sources.value

    override fun observeAll(): Flow<List<UiRssSource>> = sources

    override fun observe(id: Int): Flow<UiRssSource> =
        sources.map { items ->
            items.first { it.id == id }
        }

    override suspend fun findByUrl(url: String): List<UiRssSource> = sources.value.filter { it.url == url }

    override suspend fun findByUrlAndType(
        url: String,
        type: dev.dimension.flare.data.database.app.model.SubscriptionType,
    ): List<UiRssSource> = sources.value.filter { it.url == url && it.type == type }

    override suspend fun upsert(input: SubscriptionSourceInput): UiRssSource {
        val source = input.toUiRssSource(input.id.takeIf { it != 0 } ?: nextId++)
        sources.update { items ->
            items
                .filterNot { it.id == source.id }
                .plus(source)
        }
        return source
    }

    override suspend fun upsertAll(inputs: List<SubscriptionSourceInput>): List<UiRssSource> = inputs.map { upsert(it) }

    override suspend fun delete(id: Int): UiRssSource? {
        val deleted = sources.value.firstOrNull { it.id == id }
        sources.update { items -> items.filterNot { it.id == id } }
        return deleted
    }
}

private fun SubscriptionSourceInput.toUiRssSource(id: Int): UiRssSource =
    UiRssSource(
        id = id,
        url = url,
        title = title,
        lastUpdate = Instant.fromEpochMilliseconds(lastUpdateMillis).toUi(),
        favIcon = icon,
        displayMode = displayMode,
        type = type,
    )
