package dev.dimension.flare.data.datasource.subscription

import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.rss.RssTimelineRemoteMediator
import dev.dimension.flare.data.model.tab.RssTimelineData
import dev.dimension.flare.data.model.tab.SubscriptionTimelineData
import dev.dimension.flare.data.network.FaviconService
import dev.dimension.flare.data.network.rss.DocumentData
import dev.dimension.flare.data.network.rss.Readability
import dev.dimension.flare.data.network.rss.RssService
import dev.dimension.flare.data.platform.RssTimelineSpecs
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.data.repository.SubscriptionRepository
import dev.dimension.flare.data.repository.SubscriptionSourceInput
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.title
import dev.dimension.flare.ui.render.toUi
import io.ktor.http.Url
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

@HiddenFromObjC
public interface SubscriptionDataSource {
    public suspend fun listSources(): List<UiRssSource>

    public suspend fun detectSource(input: String): SubscriptionSourceDetection

    public fun createTimelineLoader(
        type: SubscriptionType,
        url: String,
    ): CacheableRemoteLoader<UiTimelineV2>

    public suspend fun saveSource(input: SubscriptionSourceInput): UiRssSource

    public suspend fun deleteSource(id: Int): UiRssSource?

    public suspend fun loadRssArticle(
        url: String,
        descriptionHtml: String? = null,
        descriptionTitle: String? = null,
    ): DocumentData
}

@HiddenFromObjC
public sealed interface SubscriptionSourceDetection {
    @HiddenFromObjC
    public data object RssHub : SubscriptionSourceDetection

    @HiddenFromObjC
    public data class RssFeed(
        val title: String,
        val url: String,
        val icon: String?,
    ) : SubscriptionSourceDetection

    @HiddenFromObjC
    public data class RssSources(
        val sources: List<UiRssSource>,
    ) : SubscriptionSourceDetection

    @HiddenFromObjC
    public data class SubscriptionInstance(
        val host: String,
        val instanceName: String?,
        val icon: String?,
        val availableTimelines: List<SubscriptionType>,
    ) : SubscriptionSourceDetection
}

@HiddenFromObjC
public object KoinSubscriptionDataSource :
    SubscriptionDataSource,
    KoinComponent {
    private val subscriptionRepository: SubscriptionRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val platformRegistry: PlatformRegistry by inject()
    private val readability = Readability()

    override suspend fun listSources(): List<UiRssSource> = subscriptionRepository.observeAll().first()

    override suspend fun detectSource(input: String): SubscriptionSourceDetection {
        val url = input.trim()
        require(url.isNotBlank()) {
            "Subscription URL or host is required."
        }
        if (url.startsWith("rsshub://", ignoreCase = true)) {
            return SubscriptionSourceDetection.RssHub
        }

        val actualUrl = url.toWebUrl()
        val feed =
            runCatching {
                RssService.fetch(actualUrl)
            }
        if (feed.isSuccess) {
            return SubscriptionSourceDetection.RssFeed(
                title = feed.getOrThrow().title.trim(),
                url = actualUrl,
                icon =
                    runCatching {
                        RssService.fetchIcon(actualUrl)
                    }.getOrNull(),
            )
        }

        val linkSources =
            runCatching {
                coroutineScope {
                    RssService
                        .detectLinkSources(actualUrl)
                        .map { sourceUrl ->
                            async {
                                UiRssSource(
                                    id = 0,
                                    url = sourceUrl,
                                    title = RssService.fetch(sourceUrl).title.trim(),
                                    lastUpdate = Instant.DISTANT_PAST.toUi(),
                                    favIcon =
                                        runCatching {
                                            RssService.fetchIcon(sourceUrl)
                                        }.getOrNull(),
                                    displayMode = RssDisplayMode.FULL_CONTENT,
                                )
                            }
                        }.awaitAll()
                }
            }.getOrNull()

        if (!linkSources.isNullOrEmpty()) {
            return SubscriptionSourceDetection.RssSources(linkSources)
        }

        val host =
            runCatching {
                Url(actualUrl).host
            }.getOrNull() ?: throw IllegalArgumentException("Invalid URL: $input")
        return detectSubscriptionInstance(host)
            ?: throw IllegalArgumentException("URL is not a valid RSS feed or supported subscription instance: $input")
    }

    override fun createTimelineLoader(
        type: SubscriptionType,
        url: String,
    ): CacheableRemoteLoader<UiTimelineV2> =
        when (type) {
            SubscriptionType.RSS -> {
                RssTimelineRemoteMediator(
                    url = url,
                    fetchSource = {
                        subscriptionRepository.findByUrl(it).firstOrNull()
                    },
                )
            }

            else -> {
                platformRegistry
                    .requireSubscriptionTimelineSpec(type)
                    .createLoader(
                        host = url,
                        locale = Locale.language,
                    )
            }
        }

    override suspend fun saveSource(input: SubscriptionSourceInput): UiRssSource = subscriptionRepository.upsert(input)

    override suspend fun deleteSource(id: Int): UiRssSource? {
        val source = subscriptionRepository.delete(id)
        source?.let {
            settingsRepository.removeHomeTimelineTabBySourceId(it.timelineSourceId())
        }
        return source
    }

    override suspend fun loadRssArticle(
        url: String,
        descriptionHtml: String?,
        descriptionTitle: String?,
    ): DocumentData =
        if (descriptionHtml != null) {
            DocumentData(
                title = descriptionTitle ?: "",
                content = descriptionHtml,
                textContent = descriptionHtml,
                length = null,
                excerpt = null,
                byline = null,
                dir = null,
                siteName = null,
                lang = null,
                publishedTime = null,
            )
        } else {
            readability.parse(url).first().getOrThrow()
        }

    private suspend fun detectSubscriptionInstance(host: String): SubscriptionSourceDetection.SubscriptionInstance? =
        coroutineScope {
            val timelines =
                platformRegistry
                    .subscriptionTimelineSpecs
                    .map { spec ->
                        async {
                            if (spec.isAvailable(host, Locale.language)) {
                                spec.type
                            } else {
                                null
                            }
                        }
                    }.awaitAll()
                    .filterNotNull()

            if (timelines.isEmpty()) {
                return@coroutineScope null
            }

            SubscriptionSourceDetection.SubscriptionInstance(
                host = host,
                instanceName = host,
                icon =
                    runCatching {
                        FaviconService.fetchIcon("https://$host/")
                    }.getOrNull(),
                availableTimelines = timelines,
            )
        }
}

private fun String.toWebUrl(): String =
    when {
        startsWith("http://", ignoreCase = true) -> replaceFirst("http://", "https://", ignoreCase = true)
        startsWith("https://", ignoreCase = true) -> this
        else -> "https://$this"
    }

private fun UiRssSource.timelineSourceId(): String =
    when (type) {
        SubscriptionType.RSS -> {
            RssTimelineSpecs.rss
                .itemId(RssTimelineData(url))
        }

        else -> {
            RssTimelineSpecs.subscription
                .itemId(
                    SubscriptionTimelineData(
                        subscriptionUrl = url,
                        subscriptionType = type,
                    ),
                )
        }
    }
