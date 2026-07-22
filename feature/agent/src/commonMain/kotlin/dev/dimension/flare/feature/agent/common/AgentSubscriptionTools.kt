package dev.dimension.flare.feature.agent.common

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.subscription.SubscriptionDataSource
import dev.dimension.flare.data.datasource.subscription.SubscriptionSourceDetection
import dev.dimension.flare.data.network.rss.DocumentData
import dev.dimension.flare.data.repository.SubscriptionSourceInput
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.contentPostOrNull
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

internal class ListSubscriptionSourcesTool(
    private val session: AgentToolSession,
) : SimpleTool<ListSubscriptionSourcesTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "List Flare subscription sources across all supported subscription types, including RSS and subscription-backed platform timelines.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Subscription type names or aliases to filter by. Leave empty to list every type.")
        val types: List<String> = emptyList(),
        @property:LLMDescription("Optional title, URL, or host substring to filter sources.")
        val query: String = "",
    )

    override suspend fun execute(args: Args): String {
        val dataSource = session.subscriptionDataSource ?: return noSubscriptionDataSourceMessage()
        val typeFilter = args.types.toSubscriptionTypeFilter()
        val query = args.query.trim()
        val sources =
            dataSource
                .listSources()
                .filter { source -> typeFilter.matches(source.type) }
                .filter { source ->
                    query.isBlank() ||
                        source.url.contains(query, ignoreCase = true) ||
                        source.host.contains(query, ignoreCase = true) ||
                        source.title.orEmpty().contains(query, ignoreCase = true)
                }
        return sources.toSubscriptionSourcesToolText(
            title = "Subscription sources",
            emptyMessage = "No subscription sources matched the request.",
        )
    }

    companion object {
        const val NAME = "list_subscription_sources"
    }
}

internal class DetectSubscriptionSourceTool(
    private val session: AgentToolSession,
) : SimpleTool<DetectSubscriptionSourceTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Detect whether a URL or host is an RSS feed, a webpage with RSS/Atom feeds, or a supported subscription instance such as Mastodon.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("URL, RSS feed URL, rsshub:// URL, or instance host to inspect.")
        val url: String = "",
    )

    override suspend fun execute(args: Args): String {
        val dataSource = session.subscriptionDataSource ?: return noSubscriptionDataSourceMessage()
        val input = args.url.trim()
        if (input.isBlank()) {
            return "Subscription source detection requires a URL or host."
        }
        return dataSource.detectSource(input).toToolText()
    }

    companion object {
        const val NAME = "detect_subscription_source"
    }
}

internal class LoadSubscriptionTimelineTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadSubscriptionTimelineTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Load a subscription timeline by saved source id or explicit SubscriptionType plus URL/host. " +
                "RSS sources return feed articles; platform subscription timelines can return posts.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Saved subscription source id from list_subscription_sources. Use this when available.")
        val sourceId: Int = 0,
        @property:LLMDescription("Subscription type, such as RSS, MASTODON_PUBLIC, MASTODON_LOCAL, or MASTODON_TRENDS.")
        val type: String = "",
        @property:LLMDescription("RSS URL or platform instance host. Required when sourceId is blank.")
        val url: String = "",
        @property:LLMDescription("Maximum number of timeline items to return. Defaults to 20 and is capped at 50.")
        val maxItems: Int = DEFAULT_SUBSCRIPTION_TOOL_ITEMS,
    )

    override suspend fun execute(args: Args): String {
        val dataSource = session.subscriptionDataSource ?: return noSubscriptionDataSourceMessage()
        val target =
            session.resolveSubscriptionTimelineTarget(args)
                ?: return session.subscriptionSourceSelectionMessage(
                    action = "load",
                )
        val pageSize = args.maxItems.coerceIn(1, MAX_SUBSCRIPTION_TOOL_ITEMS)
        val items =
            dataSource
                .createTimelineLoader(type = target.type, url = target.url)
                .load(pageSize = pageSize, request = PagingRequest.Refresh)
                .data
                .take(pageSize)
        session.messagePartStore.addPosts(items.mapNotNull { it.contentPostOrNull() })
        session.subscriptionItemStore.addFeeds(items.filterIsInstance<UiTimelineV2.Feed>())

        return buildString {
            appendLine("Subscription timeline")
            appendLine("sourceId: ${target.id ?: ""}")
            appendLine("type: ${target.type.name}")
            appendLine("url: ${target.url}")
            target.title?.let { appendLine("title: $it") }
            appendLine()
            append(
                items.toSubscriptionTimelineToolText(
                    emptyMessage = "No timeline items were returned.",
                    maxItems = pageSize,
                ),
            )
        }.take(MAX_SUBSCRIPTION_TOOL_RESULT_LENGTH)
    }

    companion object {
        const val NAME = "load_subscription_timeline"
    }
}

internal class SaveSubscriptionSourceTool(
    private val session: AgentToolSession,
) : SimpleTool<SaveSubscriptionSourceTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Save or update a Flare subscription source after explicit confirmation. " +
                "Use detect_subscription_source first when URL/host type is unclear. " +
                "When the user asks to add, save, or update a subscription, call this tool with confirmed=false first " +
                "so the app can show a structured confirmation button. Do not ask for confirmation only in prose.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Existing subscription source id to update. Leave 0 to create a new source.")
        val sourceId: Int = 0,
        @property:LLMDescription(
            "Subscription type, such as RSS, MASTODON_PUBLIC, MASTODON_LOCAL, or MASTODON_TRENDS. Leave blank to detect.",
        )
        val type: String = "",
        @property:LLMDescription("RSS URL or platform instance host to save.")
        val url: String = "",
        @property:LLMDescription("Optional display title. Leave blank to use detected or default title.")
        val title: String = "",
        @property:LLMDescription("Optional icon URL. Leave blank to use detected icon.")
        val icon: String = "",
        @property:LLMDescription("RSS display mode: FULL_CONTENT, DESCRIPTION_ONLY, or OPEN_IN_BROWSER. Ignored for non-RSS types.")
        val displayMode: String = "",
        @property:LLMDescription(
            "Set false for the first save/update request to generate the app confirmation button. Set true only after the latest user message explicitly confirms the exact subscription source to save.",
        )
        val confirmed: Boolean = false,
    )

    override suspend fun execute(args: Args): String {
        val dataSource = session.subscriptionDataSource ?: return noSubscriptionDataSourceMessage()
        val candidates =
            session.resolveSubscriptionSaveCandidates(args)
                ?: return "Subscription source saving requires a URL or host."
        if (candidates.isEmpty()) {
            return "No saveable subscription source was detected."
        }
        if (candidates.size > 1) {
            return session.subscriptionSaveSelectionMessage(candidates)
        }
        val candidate = candidates.single()
        if (!args.confirmed) {
            val requestId = "subscription-save:${candidate.type.name}:${candidate.url}"
            val prompt = candidate.saveConfirmationMessage(requestId)
            session.inputRequestStore.set(
                AgentPendingInputRequest(
                    requestId = requestId,
                    options =
                        listOf(
                            AgentPendingInputRequest.Option(
                                id = "confirm",
                                value =
                                    buildString {
                                        appendLine("event=subscription_save_confirmed")
                                        appendSubscriptionSaveArgs(candidate, confirmed = true)
                                    },
                            ),
                        ),
                    allowFreeText = true,
                ),
            )
            return prompt
        }

        val saved =
            dataSource.saveSource(
                SubscriptionSourceInput(
                    id = args.sourceId.coerceAtLeast(0),
                    url = candidate.url,
                    title = candidate.title,
                    icon = candidate.icon,
                    displayMode = candidate.displayMode,
                    lastUpdateMillis = 0,
                    type = candidate.type,
                ),
            )
        return buildString {
            appendLine("Subscription source saved.")
            append(saved.toSubscriptionSourceToolText())
        }.trim()
    }

    companion object {
        const val NAME = "save_subscription_source"
    }
}

internal class DeleteSubscriptionSourceTool(
    private val session: AgentToolSession,
) : SimpleTool<DeleteSubscriptionSourceTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Delete a saved Flare subscription source after explicit confirmation. " +
                "Use list_subscription_sources first when the source is ambiguous. " +
                "When the user asks to remove or delete a subscription, call this tool with confirmed=false first " +
                "so the app can show a structured confirmation button. Do not ask for confirmation only in prose.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("Saved subscription source id from list_subscription_sources.")
        val sourceId: Int = 0,
        @property:LLMDescription("Subscription type to narrow URL/host matching when sourceId is blank.")
        val type: String = "",
        @property:LLMDescription("RSS URL or platform instance host to delete when sourceId is blank.")
        val url: String = "",
        @property:LLMDescription(
            "Set false for the first delete request to generate the app confirmation button. Set true only after the latest user message explicitly confirms the exact subscription source to delete.",
        )
        val confirmed: Boolean = false,
    )

    override suspend fun execute(args: Args): String {
        val dataSource = session.subscriptionDataSource ?: return noSubscriptionDataSourceMessage()
        val source =
            session.resolveSubscriptionSource(args.sourceId, args.type, args.url)
                ?: return session.subscriptionSourceSelectionMessage(
                    action = "delete",
                )
        if (!args.confirmed) {
            val requestId = "subscription-delete:${source.id}"
            val prompt = source.deleteConfirmationMessage(requestId)
            session.inputRequestStore.set(
                AgentPendingInputRequest(
                    requestId = requestId,
                    options =
                        listOf(
                            AgentPendingInputRequest.Option(
                                id = "confirm",
                                value =
                                    buildString {
                                        appendLine("event=subscription_delete_confirmed")
                                        appendSubscriptionSourceArgs(source, action = "delete", confirmed = true)
                                    },
                            ),
                        ),
                    allowFreeText = true,
                ),
            )
            return prompt
        }

        val deleted = dataSource.deleteSource(source.id)
        return if (deleted == null) {
            "Subscription source was not found or was already deleted: ${source.id}."
        } else {
            buildString {
                appendLine("Subscription source deleted.")
                append(deleted.toSubscriptionSourceToolText())
            }.trim()
        }
    }

    companion object {
        const val NAME = "delete_subscription_source"
    }
}

internal class LoadRssArticleTool(
    private val session: AgentToolSession,
) : SimpleTool<LoadRssArticleTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description =
            "Load readable article content for an actual RSS feed article URL or rssArticleRef returned by load_subscription_timeline.",
    ) {
    @Serializable
    internal data class Args(
        @property:LLMDescription("rssArticleRef returned by load_subscription_timeline, such as [[rss:https://example.com/article]].")
        val articleRef: String = "",
        @property:LLMDescription("Article URL from an RSS feed item. Prefer articleRef when available.")
        val articleUrl: String = "",
        @property:LLMDescription("Maximum text characters to return. Defaults to 4000 and is capped at 8000.")
        val maxTextLength: Int = DEFAULT_RSS_ARTICLE_TEXT_LENGTH,
    )

    override suspend fun execute(args: Args): String {
        val dataSource = session.subscriptionDataSource ?: return noSubscriptionDataSourceMessage()
        val feed = session.subscriptionItemStore.findFeed(args.articleRef, args.articleUrl)
        val url = feed?.url ?: args.articleUrl.trim()
        if (url.isBlank()) {
            return "RSS article loading requires an rssArticleRef or articleUrl."
        }

        val document =
            dataSource.loadRssArticle(
                url = url,
                descriptionHtml = feed?.descriptionHtml?.takeIf { feed.agentRssDisplayMode() == RssDisplayMode.DESCRIPTION_ONLY },
                descriptionTitle = feed?.title,
            )
        return document.toRssArticleToolText(
            url = url,
            articleRef = feed?.agentRssArticleMarker(),
            maxTextLength = args.maxTextLength.coerceIn(1, MAX_RSS_ARTICLE_TEXT_LENGTH),
        )
    }

    companion object {
        const val NAME = "load_rss_article"
    }
}

internal class AgentSubscriptionItemStore {
    private val mutex = Mutex()
    private val feeds = mutableListOf<UiTimelineV2.Feed>()

    suspend fun addFeeds(items: List<UiTimelineV2.Feed>) {
        mutex.withLock {
            feeds += items
        }
    }

    suspend fun findFeed(
        articleRef: String,
        articleUrl: String,
    ): UiTimelineV2.Feed? =
        mutex.withLock {
            val normalizedRef = articleRef.normalizedSubscriptionRef()
            val normalizedUrl = articleUrl.trim()
            feeds
                .asReversed()
                .distinctBy { it.url }
                .firstOrNull { feed ->
                    (normalizedRef.isNotBlank() && feed.agentRssArticleRef().normalizedSubscriptionRef() == normalizedRef) ||
                        (normalizedRef.isNotBlank() && feed.agentRssArticleMarker().normalizedSubscriptionRef() == normalizedRef) ||
                        (normalizedUrl.isNotBlank() && feed.url == normalizedUrl)
                }
        }
}

private data class SubscriptionTimelineTarget(
    val id: Int?,
    val type: SubscriptionType,
    val url: String,
    val title: String?,
)

private data class SubscriptionSaveCandidate(
    val type: SubscriptionType,
    val url: String,
    val title: String?,
    val icon: String?,
    val displayMode: RssDisplayMode,
)

private data class SubscriptionTypeFilter(
    val all: Boolean,
    val types: Set<SubscriptionType>,
) {
    fun matches(type: SubscriptionType): Boolean = all || type in types
}

private suspend fun AgentToolSession.resolveSubscriptionTimelineTarget(
    args: LoadSubscriptionTimelineTool.Args,
): SubscriptionTimelineTarget? {
    resolveSubscriptionSource(args.sourceId, args.type, args.url)?.let { source ->
        return source.toTimelineTarget()
    }

    val requestedType = args.type.toSubscriptionTypeOrNull()
    val requestedUrl = args.url.trim()
    if (requestedType != null && requestedUrl.isNotBlank()) {
        return SubscriptionTimelineTarget(
            id = null,
            type = requestedType,
            url = requestedType.normalizeSubscriptionUrl(requestedUrl),
            title = null,
        )
    }

    val dataSource = subscriptionDataSource ?: return null
    if (requestedUrl.isNotBlank()) {
        val existing =
            dataSource
                .listSources()
                .filter { it.url == requestedUrl || it.host == requestedUrl || it.url == requestedUrl.toWebUrlOrOriginal() }
                .let { sources ->
                    requestedType?.let { type -> sources.filter { it.type == type } } ?: sources
                }
        existing.singleOrNull()?.let {
            return it.toTimelineTarget()
        }

        val detected =
            runCatching {
                dataSource.detectSource(requestedUrl)
            }.getOrNull()
        val targets =
            when (detected) {
                is SubscriptionSourceDetection.RssFeed -> {
                    listOf(
                        SubscriptionTimelineTarget(
                            id = null,
                            type = SubscriptionType.RSS,
                            url = detected.url,
                            title = detected.title,
                        ),
                    )
                }

                is SubscriptionSourceDetection.RssSources -> {
                    detected.sources.map { it.toTimelineTarget() }
                }

                is SubscriptionSourceDetection.SubscriptionInstance -> {
                    detected.availableTimelines.map { type ->
                        SubscriptionTimelineTarget(
                            id = null,
                            type = type,
                            url = detected.host,
                            title = "${detected.instanceName ?: detected.host} - ${type.name}",
                        )
                    }
                }

                SubscriptionSourceDetection.RssHub,
                null,
                -> {
                    emptyList()
                }
            }
        return targets.singleOrNull()
    }

    return dataSource.listSources().singleOrNull()?.toTimelineTarget()
}

private suspend fun AgentToolSession.resolveSubscriptionSource(
    sourceId: Int,
    typeName: String,
    url: String,
): UiRssSource? {
    val dataSource = subscriptionDataSource ?: return null
    val sources = dataSource.listSources()
    if (sourceId > 0) {
        return sources.firstOrNull { it.id == sourceId }
    }
    val requestedType = typeName.toSubscriptionTypeOrNull()
    val requestedUrl = url.trim()
    if (requestedUrl.isBlank()) {
        return sources.singleOrNull()
    }
    return sources
        .filter { source ->
            requestedType == null || source.type == requestedType
        }.filter { source ->
            source.url == requestedUrl ||
                source.host == requestedUrl ||
                source.url == requestedUrl.toWebUrlOrOriginal() ||
                source.url == source.type.normalizeSubscriptionUrl(requestedUrl)
        }.singleOrNull()
}

private suspend fun AgentToolSession.resolveSubscriptionSaveCandidates(
    args: SaveSubscriptionSourceTool.Args,
): List<SubscriptionSaveCandidate>? {
    val dataSource = subscriptionDataSource ?: return emptyList()
    val inputUrl = args.url.trim()
    if (inputUrl.isBlank()) {
        return null
    }
    val displayMode = args.displayMode.toRssDisplayModeOrNull() ?: RssDisplayMode.FULL_CONTENT
    val requestedType = args.type.toSubscriptionTypeOrNull()
    if (args.type.isNotBlank() && requestedType == null) {
        return emptyList()
    }
    if (requestedType != null) {
        val normalizedUrl = requestedType.normalizeSubscriptionUrl(inputUrl)
        val detectedRss =
            if (requestedType == SubscriptionType.RSS) {
                runCatching {
                    dataSource.detectSource(inputUrl) as? SubscriptionSourceDetection.RssFeed
                }.getOrNull()
            } else {
                null
            }
        return listOf(
            SubscriptionSaveCandidate(
                type = requestedType,
                url = detectedRss?.url ?: normalizedUrl,
                title =
                    args.title
                        .trim()
                        .ifBlank {
                            detectedRss?.title ?: "$normalizedUrl - ${requestedType.name}"
                        },
                icon = args.icon.trim().ifBlank { detectedRss?.icon },
                displayMode = displayMode,
            ),
        )
    }

    return when (val detected = dataSource.detectSource(inputUrl)) {
        is SubscriptionSourceDetection.RssFeed -> {
            listOf(
                SubscriptionSaveCandidate(
                    type = SubscriptionType.RSS,
                    url = detected.url,
                    title = args.title.trim().ifBlank { detected.title },
                    icon = args.icon.trim().ifBlank { detected.icon },
                    displayMode = displayMode,
                ),
            )
        }

        is SubscriptionSourceDetection.RssSources -> {
            detected.sources.map { source ->
                SubscriptionSaveCandidate(
                    type = SubscriptionType.RSS,
                    url = source.url,
                    title = args.title.trim().ifBlank { source.title.orEmpty() },
                    icon = args.icon.trim().ifBlank { source.favIcon },
                    displayMode = displayMode,
                )
            }
        }

        is SubscriptionSourceDetection.SubscriptionInstance -> {
            detected.availableTimelines.map { type ->
                SubscriptionSaveCandidate(
                    type = type,
                    url = detected.host,
                    title = args.title.trim().ifBlank { "${detected.instanceName ?: detected.host} - ${type.name}" },
                    icon = args.icon.trim().ifBlank { detected.icon },
                    displayMode = RssDisplayMode.FULL_CONTENT,
                )
            }
        }

        SubscriptionSourceDetection.RssHub -> {
            emptyList()
        }
    }
}

private suspend fun AgentToolSession.subscriptionSourceSelectionMessage(action: String): String {
    val dataSource = subscriptionDataSource ?: return noSubscriptionDataSourceMessage()
    val sources = dataSource.listSources()
    if (sources.isEmpty()) {
        return "No saved subscription sources are available."
    }
    val options = sources.take(SUBSCRIPTION_SELECTION_OPTION_LIMIT)
    val request =
        AgentPendingInputRequest(
            requestId = "subscription-source:$action:${options.joinToString { it.id.toString() }}",
            options =
                options.map { source ->
                    AgentPendingInputRequest.Option(
                        id = "subscription:${source.id}",
                        value =
                            buildString {
                                appendLine("event=subscription_source_selected")
                                appendSubscriptionSourceArgs(source, action = action, confirmed = false)
                            },
                    )
                },
            allowFreeText = true,
        )
    inputRequestStore.set(request)
    return buildString {
        appendLine("event=subscription_source_selection_required")
        appendLine("action=$action")
        appendLine("inputRequestId=${request.requestId}")
        appendLine("inputRequestOptions:")
        options.forEach { source ->
            appendLine("- optionId=subscription:${source.id}")
            appendLine("  optionKind=subscription_source")
            appendLine("  sourceId=${source.id}")
            appendLine("  type=${source.type.name}")
            appendLine("  url=${source.url}")
            appendLine("  title=${source.title.orEmpty()}")
            appendLine("  host=${source.host}")
        }
    }.trim()
}

private suspend fun AgentToolSession.subscriptionSaveSelectionMessage(candidates: List<SubscriptionSaveCandidate>): String {
    val options = candidates.take(SUBSCRIPTION_SELECTION_OPTION_LIMIT)
    val request =
        AgentPendingInputRequest(
            requestId = "subscription-save-choice:${options.joinToString { it.type.name + ':' + it.url }}",
            options =
                options.mapIndexed { index, candidate ->
                    AgentPendingInputRequest.Option(
                        id = "subscription-save:${index + 1}",
                        value =
                            buildString {
                                appendLine("event=subscription_save_candidate_selected")
                                appendSubscriptionSaveArgs(candidate, confirmed = false)
                            },
                    )
                },
            allowFreeText = true,
        )
    inputRequestStore.set(request)
    return buildString {
        appendLine("event=subscription_save_selection_required")
        appendLine("inputRequestId=${request.requestId}")
        appendLine("inputRequestOptions:")
        options.forEachIndexed { index, candidate ->
            appendLine("- optionId=subscription-save:${index + 1}")
            appendLine("  optionKind=subscription_save_candidate")
            appendLine("  type=${candidate.type.name}")
            appendLine("  url=${candidate.url}")
            appendLine("  title=${candidate.title.orEmpty()}")
            appendLine("  icon=${candidate.icon.orEmpty()}")
            appendLine("  displayMode=${candidate.displayMode.name}")
        }
    }.trim()
}

private fun SubscriptionSourceDetection.toToolText(): String =
    when (this) {
        SubscriptionSourceDetection.RssHub -> {
            "Detected subscription source\nkind: RSSHub\nmessage: Provide the resolved RSSHub feed URL before saving."
        }

        is SubscriptionSourceDetection.RssFeed -> {
            buildString {
                appendLine("Detected subscription source")
                appendLine("kind: RSS feed")
                appendLine("type: ${SubscriptionType.RSS.name}")
                appendLine("url: $url")
                appendLine("title: $title")
                appendLine("icon: ${icon.orEmpty()}")
            }.trim()
        }

        is SubscriptionSourceDetection.RssSources -> {
            sources.toSubscriptionSourcesToolText(
                title = "Detected RSS/Atom feeds",
                emptyMessage = "No RSS/Atom feeds were detected.",
            )
        }

        is SubscriptionSourceDetection.SubscriptionInstance -> {
            buildString {
                appendLine("Detected subscription source")
                appendLine("kind: subscription instance")
                appendLine("host: $host")
                appendLine("instanceName: ${instanceName.orEmpty()}")
                appendLine("icon: ${icon.orEmpty()}")
                appendLine("availableTypes: ${availableTimelines.joinToString { it.name }}")
                availableTimelines.forEach { type ->
                    appendLine("- type=${type.name}, typeName=${type.name}, url=$host")
                }
            }.trim()
        }
    }

private fun List<UiRssSource>.toSubscriptionSourcesToolText(
    title: String,
    emptyMessage: String,
): String {
    val sources = this
    return buildString {
        appendLine(title)
        if (sources.isEmpty()) {
            appendLine(emptyMessage)
            return@buildString
        }
        sources.take(MAX_SUBSCRIPTION_TOOL_ITEMS).forEachIndexed { index, source ->
            appendLine()
            appendLine("Source #${index + 1}")
            append(source.toSubscriptionSourceToolText())
        }
    }.trim()
}

private fun UiRssSource.toSubscriptionSourceToolText(): String =
    buildString {
        appendLine("sourceId: $id")
        appendLine("type: ${type.name}")
        appendLine("typeName: ${type.name}")
        appendLine("url: $url")
        appendLine("host: $host")
        appendLine("title: ${title.orEmpty()}")
        appendLine("icon: ${favIcon.orEmpty()}")
        appendLine("displayMode: ${displayMode.name}")
        appendLine("lastUpdate: ${lastUpdate.value}")
    }

private fun List<UiTimelineV2>.toSubscriptionTimelineToolText(
    emptyMessage: String,
    maxItems: Int,
): String {
    val items = this
    return buildString {
        appendLine("Items")
        if (items.isEmpty()) {
            appendLine(emptyMessage)
            return@buildString
        }
        items.take(maxItems).forEachIndexed { index, item ->
            appendLine()
            appendLine("Item #${index + 1}")
            append(item.toSubscriptionTimelineItemToolText())
        }
    }.trim()
}

private fun UiTimelineV2.toSubscriptionTimelineItemToolText(): String =
    when (this) {
        is UiTimelineV2.Feed -> toSubscriptionFeedToolText()
        is UiTimelineV2.TimelinePostItem -> displayPost.toSubscriptionPostToolText()
        is UiTimelineV2.Post -> toSubscriptionPostToolText()
        is UiTimelineV2.User -> "itemType: user\nsearchText: ${searchText.orEmpty().take(MAX_SUBSCRIPTION_ITEM_TEXT_LENGTH)}"
        is UiTimelineV2.UserList -> "itemType: user_list\nsearchText: ${searchText.orEmpty().take(MAX_SUBSCRIPTION_ITEM_TEXT_LENGTH)}"
        is UiTimelineV2.Message -> "itemType: message\nsearchText: ${searchText.orEmpty().take(MAX_SUBSCRIPTION_ITEM_TEXT_LENGTH)}"
    }

private fun UiTimelineV2.Feed.toSubscriptionFeedToolText(): String =
    buildString {
        appendLine("itemType: feed")
        appendLine("rssArticleRef: ${agentRssArticleMarker()}")
        appendLine("title: ${title.orEmpty()}")
        appendLine("url: $url")
        appendLine("sourceName: ${source.name}")
        appendLine("sourceIcon: ${source.icon.orEmpty()}")
        appendLine("createdAt: ${createdAt.value}")
        appendLine("displayMode: ${agentRssDisplayMode().name}")
        appendLine("description: ${description.orEmpty().take(MAX_SUBSCRIPTION_ITEM_TEXT_LENGTH)}")
        media?.let { image ->
            appendLine("imageUrl: ${image.url}")
            appendLine("imageDescription: ${image.description.orEmpty()}")
        }
    }

private fun UiTimelineV2.Post.toSubscriptionPostToolText(): String =
    buildString {
        appendLine("itemType: post")
        appendLine("attachmentRef: ${agentAttachmentMarker()}")
        appendLine("platform: ${platformType.name}")
        appendLine("statusKey: $statusKey")
        appendLine("createdAt: ${createdAt.value}")
        appendLine("authorName: ${user?.name?.raw.orEmpty()}")
        appendLine("authorHandle: ${user?.handle?.raw.orEmpty()}")
        appendLine("content: ${content.original.raw.take(MAX_SUBSCRIPTION_ITEM_TEXT_LENGTH)}")
    }

private fun DocumentData.toRssArticleToolText(
    url: String,
    articleRef: String?,
    maxTextLength: Int,
): String =
    buildString {
        appendLine("RSS article")
        articleRef?.let { appendLine("rssArticleRef: $it") }
        appendLine("url: $url")
        appendLine("title: $title")
        appendLine("siteName: ${siteName.orEmpty()}")
        appendLine("byline: ${byline.orEmpty()}")
        appendLine("publishedTime: ${publishedTime.orEmpty()}")
        appendLine("excerpt: ${excerpt.orEmpty()}")
        appendLine()
        appendLine("text:")
        append(textContent.take(maxTextLength))
    }.take(MAX_SUBSCRIPTION_TOOL_RESULT_LENGTH)

private fun UiRssSource.toTimelineTarget(): SubscriptionTimelineTarget =
    SubscriptionTimelineTarget(
        id = id,
        type = type,
        url = url,
        title = title,
    )

private fun SubscriptionSaveCandidate.saveConfirmationMessage(requestId: String): String =
    buildString {
        appendLine("event=subscription_save_confirmation_required")
        appendLine("inputRequestId=$requestId")
        appendLine("inputRequestOptions:")
        appendLine("- optionId=confirm")
        appendLine("  optionKind=confirmation")
        appendLine("type=${type.name}")
        appendLine("url=$url")
        appendLine("title=${title.orEmpty()}")
        appendLine("icon=${icon.orEmpty()}")
        if (type == SubscriptionType.RSS) {
            appendLine("displayMode=${displayMode.name}")
        }
    }.trim()

private fun UiRssSource.deleteConfirmationMessage(requestId: String): String =
    buildString {
        appendLine("event=subscription_delete_confirmation_required")
        appendLine("inputRequestId=$requestId")
        appendLine("inputRequestOptions:")
        appendLine("- optionId=confirm")
        appendLine("  optionKind=confirmation")
        appendLine("sourceId=$id")
        appendLine("type=${type.name}")
        appendLine("url=$url")
        appendLine("title=${title.orEmpty()}")
        appendLine("icon=${favIcon.orEmpty()}")
    }.trim()

private fun StringBuilder.appendSubscriptionSourceArgs(
    source: UiRssSource,
    action: String,
    confirmed: Boolean,
) {
    appendLine("action=$action")
    appendLine("sourceId=${source.id}")
    appendLine("type=${source.type.name}")
    appendLine("url=${source.url}")
    appendLine("confirmed=$confirmed")
}

private fun StringBuilder.appendSubscriptionSaveArgs(
    candidate: SubscriptionSaveCandidate,
    confirmed: Boolean,
) {
    appendLine("type=${candidate.type.name}")
    appendLine("url=${candidate.url}")
    appendLine("title=${candidate.title.orEmpty()}")
    appendLine("icon=${candidate.icon.orEmpty()}")
    appendLine("displayMode=${candidate.displayMode.name}")
    appendLine("confirmed=$confirmed")
}

private fun UiTimelineV2.Feed.agentRssArticleRef(): String = url

private fun UiTimelineV2.Feed.agentRssArticleMarker(): String = "[[rss:${agentRssArticleRef()}]]"

private fun UiTimelineV2.Feed.agentRssDisplayMode(): RssDisplayMode =
    when (val event = clickEvent) {
        is ClickEvent.Deeplink -> {
            when (val route = DeeplinkRoute.parse(event.url)) {
                is DeeplinkRoute.OpenLinkDirectly -> {
                    RssDisplayMode.OPEN_IN_BROWSER
                }

                is DeeplinkRoute.Rss.Detail -> {
                    if (route.descriptionHtml != null || route.title != null) {
                        RssDisplayMode.DESCRIPTION_ONLY
                    } else {
                        RssDisplayMode.FULL_CONTENT
                    }
                }

                else -> {
                    RssDisplayMode.FULL_CONTENT
                }
            }
        }

        ClickEvent.Noop -> {
            RssDisplayMode.FULL_CONTENT
        }
    }

private fun String.normalizedSubscriptionRef(): String =
    trim()
        .removePrefix("[[rss:")
        .removeSuffix("]]")

private fun List<String>.toSubscriptionTypeFilter(): SubscriptionTypeFilter {
    val requested = mapNotNull { it.toSubscriptionTypeOrNull() }.toSet()
    return SubscriptionTypeFilter(
        all = isEmpty(),
        types = requested,
    )
}

private fun String.toSubscriptionTypeOrNull(): SubscriptionType? {
    val key = subscriptionKey()
    if (key.isBlank()) {
        return null
    }
    return SubscriptionType.entries.firstOrNull { type ->
        key == type.name.subscriptionKey() || key in type.aliasKeys()
    }
}

private fun SubscriptionType.aliasKeys(): Set<String> =
    when (this) {
        SubscriptionType.RSS -> {
            setOf("feed", "feeds", "atom", "rdf", "rssfeed", "rsssource", "rss订阅")
        }

        SubscriptionType.MASTODON_TRENDS -> {
            setOf("mastodontrends", "mastodontrend", "trends", "trend", "trending", "hot", "热门", "趋势")
        }

        SubscriptionType.MASTODON_PUBLIC -> {
            setOf("mastodonpublic", "public", "federated", "federation", "global", "联邦", "公共")
        }

        SubscriptionType.MASTODON_LOCAL -> {
            setOf("mastodonlocal", "local", "instance", "本地", "实例")
        }
    }.map { it.subscriptionKey() }.toSet()

private fun String.subscriptionKey(): String =
    trim()
        .lowercase()
        .replace("-", "")
        .replace("_", "")
        .replace(" ", "")
        .replace(".", "")

private fun String.toRssDisplayModeOrNull(): RssDisplayMode? =
    when (subscriptionKey()) {
        "fullcontent", "full", "readability", "正文", "全文" -> RssDisplayMode.FULL_CONTENT
        "descriptiononly", "description", "summary", "摘要" -> RssDisplayMode.DESCRIPTION_ONLY
        "openinbrowser", "browser", "link", "浏览器", "打开链接" -> RssDisplayMode.OPEN_IN_BROWSER
        else -> null
    }

private fun SubscriptionType.normalizeSubscriptionUrl(input: String): String =
    when (this) {
        SubscriptionType.RSS -> input.toWebUrlOrOriginal()
        else -> input.toSubscriptionHost()
    }

private fun String.toWebUrlOrOriginal(): String {
    val value = trim()
    return when {
        value.isBlank() -> value
        value.startsWith("http://", ignoreCase = true) -> value.replaceFirst("http://", "https://", ignoreCase = true)
        value.startsWith("https://", ignoreCase = true) -> value
        value.contains("/") -> value
        else -> "https://$value"
    }
}

private fun String.toSubscriptionHost(): String {
    val value = trim()
    if (value.isBlank()) {
        return value
    }
    val webUrl =
        when {
            value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true) -> value
            else -> "https://$value"
        }
    return runCatching {
        io.ktor.http
            .Url(webUrl)
            .host
    }.getOrElse {
        value
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
    }
}

private fun noSubscriptionDataSourceMessage(): String = "Subscription tools are unavailable in this agent session."

private const val DEFAULT_SUBSCRIPTION_TOOL_ITEMS = 20
private const val MAX_SUBSCRIPTION_TOOL_ITEMS = 50
private const val MAX_SUBSCRIPTION_ITEM_TEXT_LENGTH = 1200
private const val MAX_SUBSCRIPTION_TOOL_RESULT_LENGTH = 24000
private const val SUBSCRIPTION_SELECTION_OPTION_LIMIT = 12
private const val DEFAULT_RSS_ARTICLE_TEXT_LENGTH = 4000
private const val MAX_RSS_ARTICLE_TEXT_LENGTH = 8000
