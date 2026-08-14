package dev.dimension.flare.feature.plugin.adapter

import androidx.paging.Pager
import androidx.paging.PagingConfig
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.datasource.microblog.ArticleCapability
import dev.dimension.flare.data.datasource.microblog.DirectMessageCapability
import dev.dimension.flare.data.datasource.microblog.DirectMessageDataSource
import dev.dimension.flare.data.datasource.microblog.GalleryCapability
import dev.dimension.flare.data.datasource.microblog.ListCapability
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationCapability
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.NotificationTimelineDataSource
import dev.dimension.flare.data.datasource.microblog.RelationCapability
import dev.dimension.flare.data.datasource.microblog.datasource.ArticleDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.ListDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.NotificationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PinnableTimelineTabDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PinnableTimelineTabSection
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.handler.DirectMessageHandler
import dev.dimension.flare.data.datasource.microblog.handler.ListHandler
import dev.dimension.flare.data.datasource.microblog.handler.ListMemberHandler
import dev.dimension.flare.data.datasource.microblog.handler.NotificationHandler
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.list.ListMetaData
import dev.dimension.flare.data.datasource.microblog.list.ListMetaDataType
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessageDelta
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessageLoader
import dev.dimension.flare.data.datasource.microblog.loader.ListLoader
import dev.dimension.flare.data.datasource.microblog.loader.ListMemberLoader
import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.toPagingSource
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.manifest.toUiText
import dev.dimension.flare.feature.plugin.wire.ArticleV1
import dev.dimension.flare.feature.plugin.wire.BooleanResultV1
import dev.dimension.flare.feature.plugin.wire.CountResultV1
import dev.dimension.flare.feature.plugin.wire.DirectMessageDeleteRequestV1
import dev.dimension.flare.feature.plugin.wire.DirectMessagePageRequestV1
import dev.dimension.flare.feature.plugin.wire.DirectMessageRoomV1
import dev.dimension.flare.feature.plugin.wire.DirectMessageSendRequestV1
import dev.dimension.flare.feature.plugin.wire.DirectMessageV1
import dev.dimension.flare.feature.plugin.wire.EmptyRequestV1
import dev.dimension.flare.feature.plugin.wire.EntityKeyV1
import dev.dimension.flare.feature.plugin.wire.EntityPageRequestV1
import dev.dimension.flare.feature.plugin.wire.EntityRequestV1
import dev.dimension.flare.feature.plugin.wire.GalleryV1
import dev.dimension.flare.feature.plugin.wire.ListMemberRequestV1
import dev.dimension.flare.feature.plugin.wire.ListMutationRequestV1
import dev.dimension.flare.feature.plugin.wire.MutationRequestV1
import dev.dimension.flare.feature.plugin.wire.MutationResultV1
import dev.dimension.flare.feature.plugin.wire.NotificationPageRequestV1
import dev.dimension.flare.feature.plugin.wire.NotificationV1
import dev.dimension.flare.feature.plugin.wire.PageDirectionV1
import dev.dimension.flare.feature.plugin.wire.PageRequestV1
import dev.dimension.flare.feature.plugin.wire.PageV1
import dev.dimension.flare.feature.plugin.wire.PostV1
import dev.dimension.flare.feature.plugin.wire.ProfileV1
import dev.dimension.flare.feature.plugin.wire.RelationV1
import dev.dimension.flare.feature.plugin.wire.SemanticActionV1
import dev.dimension.flare.feature.plugin.wire.SocialListV1
import dev.dimension.flare.feature.plugin.wire.TimelineDisplayV1
import dev.dimension.flare.feature.plugin.wire.TimelineSectionV1
import dev.dimension.flare.feature.plugin.wire.requireValid
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiArticle
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asType
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

internal class PluginExtraCapabilitiesV1(
    private val plugin: RunningPluginV1,
    private val base: MicroblogDataSource,
    private val invoker: PluginAccountInvokerV1,
    private val mapper: PluginWireMapperV1,
    private val accountKey: MicroBlogKey?,
    private val operations: Map<String, Set<String>>,
    private val directions: (String, String) -> Set<PageDirectionV1>,
    private val coroutineScope: CoroutineScope?,
    private val dynamicTimelineSpec: TimelineSpec<PluginTimelineDataV1>?,
) {
    val relation: RelationCapability? by lazy {
        accountKey
            ?.takeIf { hasAll(PluginAbiV1.Capabilities.RELATION, "state", "mutate") }
            ?.let { RelationCapability(RelationAdapter(it)) }
    }

    val notification: NotificationCapability? by lazy {
        val account = accountKey ?: return@lazy null
        val page = has(PluginAbiV1.Capabilities.NOTIFICATION, "page")
        val badge = has(PluginAbiV1.Capabilities.NOTIFICATION, "badge")
        if (!page && !badge) return@lazy null
        val adapter = NotificationAdapter(account)
        NotificationCapability(
            timeline = adapter.takeIf { page },
            events = adapter.takeIf { badge },
        )
    }

    val list: ListCapability? by lazy {
        val account = accountKey ?: return@lazy null
        if (!hasAll(PluginAbiV1.Capabilities.LIST, *LIST_OPERATIONS)) return@lazy null
        ListCapability(ListAdapter(account))
    }

    val directMessage: DirectMessageCapability? by lazy {
        val account = accountKey ?: return@lazy null
        val scope = coroutineScope ?: return@lazy null
        if (!hasAll(PluginAbiV1.Capabilities.DIRECT_MESSAGE, *DIRECT_MESSAGE_OPERATIONS)) return@lazy null
        DirectMessageCapability(DirectMessageAdapter(account, scope))
    }

    val article: ArticleCapability? by lazy {
        if (!hasAll(PluginAbiV1.Capabilities.ARTICLE, "detail", "comments")) return@lazy null
        ArticleCapability(ArticleAdapter())
    }

    val gallery: GalleryCapability? by lazy {
        if (!hasAll(PluginAbiV1.Capabilities.GALLERY, "detail", "comments", "recommendations")) return@lazy null
        GalleryCapability(GalleryAdapter())
    }

    val pinnableTabs: PinnableTimelineTabDataSource? by lazy {
        val account = accountKey ?: return@lazy null
        val spec = dynamicTimelineSpec ?: return@lazy null
        if (!has(PluginAbiV1.Capabilities.TAB_CATALOG, "page")) return@lazy null
        DynamicTabCatalog(account, spec)
    }

    private inner class RelationAdapter(
        account: MicroBlogKey,
    ) : RelationDataSource {
        private val tokens = mutableMapOf<MicroBlogKey, Map<SemanticActionV1, String>>()
        private val loader =
            object : RelationLoader {
                override val supportedTypes: Set<RelationActionType> = RelationActionType.entries.toSet()

                override suspend fun relation(userKey: MicroBlogKey): UiRelation =
                    invoker
                        .invoke(
                            capabilityId = PluginAbiV1.Capabilities.RELATION,
                            operation = "state",
                            request = EntityRequestV1(userKey.toWire()),
                            requestSerializer = EntityRequestV1.serializer(),
                            responseSerializer = RelationV1.serializer(),
                            validate = RelationV1::requireValid,
                        ).also { tokens[userKey] = it.actionTokens }
                        .let(mapper::relation)

                override suspend fun follow(userKey: MicroBlogKey) = mutate(userKey, SemanticActionV1.Follow)

                override suspend fun unfollow(userKey: MicroBlogKey) = mutate(userKey, SemanticActionV1.Unfollow)

                override suspend fun block(userKey: MicroBlogKey) = mutate(userKey, SemanticActionV1.Block)

                override suspend fun unblock(userKey: MicroBlogKey) = mutate(userKey, SemanticActionV1.Unblock)

                override suspend fun mute(userKey: MicroBlogKey) = mutate(userKey, SemanticActionV1.Mute)

                override suspend fun unmute(userKey: MicroBlogKey) = mutate(userKey, SemanticActionV1.Unmute)

                private suspend fun mutate(
                    userKey: MicroBlogKey,
                    action: SemanticActionV1,
                ) {
                    val result =
                        invoker.invoke(
                            capabilityId = PluginAbiV1.Capabilities.RELATION,
                            operation = "mutate",
                            request = MutationRequestV1(userKey.toWire(), action, tokens[userKey]?.get(action)),
                            requestSerializer = MutationRequestV1.serializer(),
                            responseSerializer = MutationResultV1.serializer(),
                            validate = MutationResultV1::requireValid,
                        )
                    if (result is MutationResultV1.UpdatedRelation) {
                        tokens[userKey] = result.relation.actionTokens
                    }
                }
            }

        override val relationHandler: RelationHandler = RelationHandler(AccountType.Specific(account), loader)
        override val supportedRelationTypes: Set<RelationActionType> = loader.supportedTypes
    }

    private inner class NotificationAdapter(
        override val accountKey: MicroBlogKey,
    ) : NotificationTimelineDataSource,
        NotificationDataSource,
        MicroblogDataSource by base {
        override val supportedNotificationFilter: List<NotificationFilter> = NotificationFilter.entries

        override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> =
            pluginRemoteLoader(
                directions = directions(PluginAbiV1.Capabilities.NOTIFICATION, "page"),
                load = { pageSize, request ->
                    invoker.invoke(
                        capabilityId = PluginAbiV1.Capabilities.NOTIFICATION,
                        operation = "page",
                        request = NotificationPageRequestV1(type.toWire(), request.toWire(pageSize)),
                        requestSerializer = NotificationPageRequestV1.serializer(),
                        responseSerializer = PageV1.serializer(NotificationV1.serializer()),
                        validate = { page -> page.requireValid(NotificationV1::requireValid) },
                    )
                },
                map = mapper::notification,
            )

        override val notificationHandler: NotificationHandler =
            NotificationHandler(
                accountKey = accountKey,
                loader =
                    object : NotificationLoader {
                        override suspend fun notificationBadgeCount(): Int =
                            invoker
                                .invoke(
                                    capabilityId = PluginAbiV1.Capabilities.NOTIFICATION,
                                    operation = "badge",
                                    request = EmptyRequestV1,
                                    requestSerializer = EmptyRequestV1.serializer(),
                                    responseSerializer = CountResultV1.serializer(),
                                    validate = CountResultV1::requireValid,
                                ).value
                    },
            )
    }

    private inner class ListAdapter(
        private val account: MicroBlogKey,
    ) : ListDataSource {
        private val cached = mutableMapOf<String, UiList.List>()
        private val listLoader =
            object : ListLoader<UiList.List> {
                override suspend fun load(
                    pageSize: Int,
                    request: PagingRequest,
                ): PagingResult<UiList.List> =
                    invokePage<SocialListV1, UiList.List>(
                        capability = PluginAbiV1.Capabilities.LIST,
                        operation = "page",
                        page = request.toWire(pageSize),
                        serializer = SocialListV1.serializer(),
                        validate = SocialListV1::requireValid,
                        map = mapper::socialList,
                    ).also { result -> result.data.forEach { cached[it.id] = it } }

                override suspend fun info(listId: String): UiList.List =
                    cached[listId] ?: error("List information has not been loaded: $listId")

                override suspend fun create(metaData: ListMetaData): UiList.List =
                    invoker
                        .invoke(
                            capabilityId = PluginAbiV1.Capabilities.LIST,
                            operation = "create",
                            request = ListMutationRequestV1(title = metaData.title),
                            requestSerializer = ListMutationRequestV1.serializer(),
                            responseSerializer = SocialListV1.serializer(),
                            validate = SocialListV1::requireValid,
                        ).let(mapper::socialList)
                        .also { cached[it.id] = it }

                override suspend fun update(
                    listId: String,
                    metaData: ListMetaData,
                ): UiList.List =
                    invoker
                        .invoke(
                            capabilityId = PluginAbiV1.Capabilities.LIST,
                            operation = "update",
                            request = ListMutationRequestV1(id = listId, title = metaData.title),
                            requestSerializer = ListMutationRequestV1.serializer(),
                            responseSerializer = SocialListV1.serializer(),
                            validate = SocialListV1::requireValid,
                        ).let(mapper::socialList)
                        .also { cached[it.id] = it }

                override suspend fun delete(listId: String) {
                    invoker.invoke(
                        capabilityId = PluginAbiV1.Capabilities.LIST,
                        operation = "delete",
                        request = ListMutationRequestV1(id = listId),
                        requestSerializer = ListMutationRequestV1.serializer(),
                        responseSerializer = MutationResultV1.serializer(),
                        validate = MutationResultV1::requireValid,
                    )
                    cached.remove(listId)
                }

                override val supportedMetaData = persistentListOf(ListMetaDataType.TITLE)
            }
        private val memberLoader =
            object : ListMemberLoader {
                override suspend fun loadMembers(
                    pageSize: Int,
                    request: PagingRequest,
                    listId: String,
                ): PagingResult<UiProfile> = entityProfilePage("members", MicroBlogKey(listId, account.host), pageSize, request)

                override suspend fun addMember(
                    listId: String,
                    userKey: MicroBlogKey,
                ): UiProfile =
                    invoker
                        .invoke(
                            capabilityId = PluginAbiV1.Capabilities.LIST,
                            operation = "addMember",
                            request = ListMemberRequestV1(listId, userKey.toWire()),
                            requestSerializer = ListMemberRequestV1.serializer(),
                            responseSerializer = ProfileV1.serializer(),
                            validate = ProfileV1::requireValid,
                        ).let(mapper::profile)

                override suspend fun removeMember(
                    listId: String,
                    userKey: MicroBlogKey,
                ) {
                    invoker.invoke(
                        capabilityId = PluginAbiV1.Capabilities.LIST,
                        operation = "removeMember",
                        request = ListMemberRequestV1(listId, userKey.toWire()),
                        requestSerializer = ListMemberRequestV1.serializer(),
                        responseSerializer = MutationResultV1.serializer(),
                        validate = MutationResultV1::requireValid,
                    )
                }

                override suspend fun loadUserLists(
                    pageSize: Int,
                    request: PagingRequest,
                    userKey: MicroBlogKey,
                ): PagingResult<UiList> =
                    invokePage<SocialListV1, UiList>(
                        capability = PluginAbiV1.Capabilities.LIST,
                        operation = "memberships",
                        page = request.toWire(pageSize),
                        serializer = SocialListV1.serializer(),
                        validate = SocialListV1::requireValid,
                        map = { mapper.socialList(it) },
                        entity = userKey,
                    )
            }

        override fun listTimeline(listId: String): RemoteLoader<UiTimelineV2> =
            entityPostLoader(PluginAbiV1.Capabilities.LIST, "timeline", MicroBlogKey(listId, account.host))

        override val listHandler: ListHandler<UiList.List> = ListHandler("plugin_lists_$account", account, listLoader)
        override val listMemberHandler: ListMemberHandler = ListMemberHandler("plugin_lists_$account", account, memberLoader)
    }

    private inner class DirectMessageAdapter(
        override val accountKey: MicroBlogKey,
        scope: CoroutineScope,
    ) : DirectMessageDataSource,
        MicroblogDataSource by base {
        private val rooms = mutableMapOf<MicroBlogKey, UiDMRoom>()
        private val loader =
            object : DirectMessageLoader {
                override val platformId: String = plugin.installed.manifest.platform.id

                override suspend fun loadRooms(
                    pageSize: Int,
                    request: PagingRequest,
                ): PagingResult<UiDMRoom> =
                    invokePage<DirectMessageRoomV1, UiDMRoom>(
                        capability = PluginAbiV1.Capabilities.DIRECT_MESSAGE,
                        operation = "rooms",
                        page = request.toWire(pageSize),
                        serializer = DirectMessageRoomV1.serializer(),
                        validate = DirectMessageRoomV1::requireValid,
                        map = mapper::directMessageRoom,
                    ).also { result -> result.data.forEach { rooms[it.key] = it } }

                override suspend fun loadMessages(
                    roomKey: MicroBlogKey,
                    pageSize: Int,
                    request: PagingRequest,
                ): PagingResult<UiDMItem> {
                    val page =
                        invoker
                            .invoke(
                                capabilityId = PluginAbiV1.Capabilities.DIRECT_MESSAGE,
                                operation = "messages",
                                request = DirectMessagePageRequestV1(roomKey.toWire(), request.toWire(pageSize)),
                                requestSerializer = DirectMessagePageRequestV1.serializer(),
                                responseSerializer = PageV1.serializer(DirectMessageV1.serializer()),
                                validate = { value -> value.requireValid(DirectMessageV1::requireValid) },
                            )
                    return page.toPagingResult(mapper::directMessage)
                }

                override suspend fun fetchRoomInfo(roomKey: MicroBlogKey): UiDMRoom =
                    rooms[roomKey]
                        ?: loadRooms(100, PagingRequest.Refresh).data.firstOrNull { it.key == roomKey }
                        ?: error("Direct-message room was not found: $roomKey")

                override suspend fun sendMessage(
                    roomKey: MicroBlogKey,
                    message: String,
                ): UiDMItem =
                    invoker
                        .invoke(
                            capabilityId = PluginAbiV1.Capabilities.DIRECT_MESSAGE,
                            operation = "send",
                            request = DirectMessageSendRequestV1(roomKey.toWire(), message),
                            requestSerializer = DirectMessageSendRequestV1.serializer(),
                            responseSerializer = DirectMessageV1.serializer(),
                            validate = DirectMessageV1::requireValid,
                        ).let(mapper::directMessage)

                override suspend fun deleteMessage(
                    roomKey: MicroBlogKey,
                    messageKey: MicroBlogKey,
                ) {
                    invoker.invoke(
                        capabilityId = PluginAbiV1.Capabilities.DIRECT_MESSAGE,
                        operation = "delete",
                        request = DirectMessageDeleteRequestV1(roomKey.toWire(), messageKey.toWire()),
                        requestSerializer = DirectMessageDeleteRequestV1.serializer(),
                        responseSerializer = MutationResultV1.serializer(),
                        validate = MutationResultV1::requireValid,
                    )
                }

                override suspend fun fetchNewMessages(
                    roomKey: MicroBlogKey,
                    cursor: String?,
                ): DirectMessageDelta =
                    loadMessages(
                        roomKey,
                        100,
                        cursor?.let(PagingRequest::Prepend) ?: PagingRequest.Refresh,
                    ).data.let(::DirectMessageDelta)

                override suspend fun leaveRoom(roomKey: MicroBlogKey) {
                    invoker.invoke(
                        capabilityId = PluginAbiV1.Capabilities.DIRECT_MESSAGE,
                        operation = "leave",
                        request = EntityRequestV1(roomKey.toWire()),
                        requestSerializer = EntityRequestV1.serializer(),
                        responseSerializer = MutationResultV1.serializer(),
                        validate = MutationResultV1::requireValid,
                    )
                    rooms.remove(roomKey)
                }

                override fun createRoom(userKey: MicroBlogKey): Flow<UiState<UiDMRoom>> =
                    flow {
                        emit(UiState.Loading())
                        try {
                            val room =
                                invoker
                                    .invoke(
                                        capabilityId = PluginAbiV1.Capabilities.DIRECT_MESSAGE,
                                        operation = "create",
                                        request = EntityRequestV1(userKey.toWire()),
                                        requestSerializer = EntityRequestV1.serializer(),
                                        responseSerializer = DirectMessageRoomV1.serializer(),
                                        validate = DirectMessageRoomV1::requireValid,
                                    ).let(mapper::directMessageRoom)
                            rooms[room.key] = room
                            emit(UiState.Success(room))
                        } catch (error: Throwable) {
                            emit(UiState.Error(error))
                        }
                    }

                override suspend fun canSend(userKey: MicroBlogKey): Boolean =
                    invoker
                        .invoke(
                            capabilityId = PluginAbiV1.Capabilities.DIRECT_MESSAGE,
                            operation = "canSend",
                            request = EntityRequestV1(userKey.toWire()),
                            requestSerializer = EntityRequestV1.serializer(),
                            responseSerializer = BooleanResultV1.serializer(),
                        ).value

                override suspend fun loadBadgeCount(): Int =
                    invoker
                        .invoke(
                            capabilityId = PluginAbiV1.Capabilities.DIRECT_MESSAGE,
                            operation = "badge",
                            request = EmptyRequestV1,
                            requestSerializer = EmptyRequestV1.serializer(),
                            responseSerializer = CountResultV1.serializer(),
                            validate = CountResultV1::requireValid,
                        ).value
            }

        override val directMessageHandler: DirectMessageHandler = DirectMessageHandler(accountKey, loader, scope)
    }

    private inner class ArticleAdapter : ArticleDataSource {
        override suspend fun article(articleKey: MicroBlogKey): UiArticle =
            invoker
                .invoke(
                    capabilityId = PluginAbiV1.Capabilities.ARTICLE,
                    operation = "detail",
                    request = EntityRequestV1(articleKey.toWire()),
                    requestSerializer = EntityRequestV1.serializer(),
                    responseSerializer = ArticleV1.serializer(),
                    validate = ArticleV1::requireValid,
                ).let(mapper::article)

        override fun articleComments(articleKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
            entityPostLoader(PluginAbiV1.Capabilities.ARTICLE, "comments", articleKey)
    }

    private inner class GalleryAdapter : GalleryDataSource {
        override fun galleryDetail(
            statusKey: MicroBlogKey,
        ): Cacheable<dev.dimension.flare.data.datasource.microblog.datasource.GalleryDetail> {
            val cache = MutableStateFlow<dev.dimension.flare.data.datasource.microblog.datasource.GalleryDetail?>(null)
            return Cacheable(
                fetchSource = {
                    cache.value =
                        invoker
                            .invoke(
                                capabilityId = PluginAbiV1.Capabilities.GALLERY,
                                operation = "detail",
                                request = EntityRequestV1(statusKey.toWire()),
                                requestSerializer = EntityRequestV1.serializer(),
                                responseSerializer = GalleryV1.serializer(),
                                validate = GalleryV1::requireValid,
                            ).let(mapper::gallery)
                },
                cacheSource = { cache.filterNotNull() },
            )
        }

        override fun galleryComments(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
            entityPostLoader(PluginAbiV1.Capabilities.GALLERY, "comments", statusKey)

        override fun galleryRecommendations(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
            entityPostLoader(PluginAbiV1.Capabilities.GALLERY, "recommendations", statusKey)
    }

    private inner class DynamicTabCatalog(
        account: MicroBlogKey,
        spec: TimelineSpec<PluginTimelineDataV1>,
    ) : PinnableTimelineTabDataSource {
        private val loader =
            object : RemoteLoader<TimelineCandidate<*>> {
                override suspend fun load(
                    pageSize: Int,
                    request: PagingRequest,
                ): PagingResult<TimelineCandidate<*>> {
                    val page =
                        invoker
                            .invoke(
                                capabilityId = PluginAbiV1.Capabilities.TAB_CATALOG,
                                operation = "page",
                                request = request.toWire(pageSize),
                                requestSerializer = PageRequestV1.serializer(),
                                responseSerializer = PageV1.serializer(TimelineSectionV1.serializer()),
                                validate = { value -> value.requireValid(TimelineSectionV1::requireValid) },
                            )
                    val candidates =
                        page.items.flatMap { section ->
                            section.timelines.items.map { timeline ->
                                val data = PluginTimelineDataV1(account, timeline.id, timeline.parameters)
                                val title = timeline.title.toUiText(plugin.installed.pluginId)
                                val icon = timeline.icon.toUiIcon().asType()
                                when (timeline.display) {
                                    TimelineDisplayV1.List -> spec.candidate(data, title, icon)
                                    TimelineDisplayV1.Grid -> spec.galleryCandidate(data, title, icon)
                                }
                            }
                        }
                    return PagingResult(
                        data = candidates,
                        nextKey = page.olderCursor.takeUnless { page.endReached },
                        previousKey = page.newerCursor,
                    )
                }
            }

        override val pinnableTimelineTabs: List<PinnableTimelineTabSection> =
            listOf(
                PinnableTimelineTabSection(
                    title =
                        plugin.installed.manifest.platform.name
                            .toUiText(plugin.installed.pluginId),
                    data =
                        Pager(
                            config = PagingConfig(pageSize = 20),
                            pagingSourceFactory = loader::toPagingSource,
                        ).flow,
                ),
            )
    }

    private fun entityPostLoader(
        capability: String,
        operation: String,
        key: MicroBlogKey,
    ): RemoteLoader<UiTimelineV2> =
        pluginRemoteLoader(
            directions = directions(capability, operation),
            load = { pageSize, request ->
                invoker.invoke(
                    capabilityId = capability,
                    operation = operation,
                    request = EntityPageRequestV1(key.toWire(), request.toWire(pageSize)),
                    requestSerializer = EntityPageRequestV1.serializer(),
                    responseSerializer = PageV1.serializer(PostV1.serializer()),
                    validate = { page -> page.requireValid { it.requireValid() } },
                )
            },
            map = mapper::post,
        )

    private suspend fun entityProfilePage(
        operation: String,
        key: MicroBlogKey,
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiProfile> {
        val page =
            invoker
                .invoke(
                    capabilityId = PluginAbiV1.Capabilities.LIST,
                    operation = operation,
                    request = EntityPageRequestV1(key.toWire(), request.toWire(pageSize)),
                    requestSerializer = EntityPageRequestV1.serializer(),
                    responseSerializer = PageV1.serializer(ProfileV1.serializer()),
                    validate = { value -> value.requireValid(ProfileV1::requireValid) },
                )
        return page.toPagingResult(mapper::profile)
    }

    private suspend fun <Wire : Any, Ui : Any> invokePage(
        capability: String,
        operation: String,
        page: PageRequestV1,
        serializer: kotlinx.serialization.KSerializer<Wire>,
        validate: (Wire) -> Unit,
        map: (Wire) -> Ui,
        entity: MicroBlogKey? = null,
    ): PagingResult<Ui> {
        val response =
            if (entity == null) {
                invoker.invoke(
                    capabilityId = capability,
                    operation = operation,
                    request = page,
                    requestSerializer = PageRequestV1.serializer(),
                    responseSerializer = PageV1.serializer(serializer),
                    validate = { value -> value.requireValid(validate) },
                )
            } else {
                invoker.invoke(
                    capabilityId = capability,
                    operation = operation,
                    request = EntityPageRequestV1(entity.toWire(), page),
                    requestSerializer = EntityPageRequestV1.serializer(),
                    responseSerializer = PageV1.serializer(serializer),
                    validate = { value -> value.requireValid(validate) },
                )
            }
        return response.toPagingResult(map)
    }

    private fun has(
        capability: String,
        operation: String,
    ): Boolean = operation in operations[capability].orEmpty()

    private fun hasAll(
        capability: String,
        vararg required: String,
    ): Boolean = operations[capability].orEmpty().containsAll(required.asList())
}

@kotlinx.serialization.Serializable
internal data class PluginTimelineDataV1(
    override val accountKey: MicroBlogKey,
    val timelineId: String,
    val parameters: Map<String, String> = emptyMap(),
) : TimelineSpec.AccountData

private fun MicroBlogKey.toWire(): EntityKeyV1 = EntityKeyV1(id, host)

private fun NotificationFilter.toWire(): String? =
    when (this) {
        NotificationFilter.All -> null
        NotificationFilter.Mention -> "mention"
        NotificationFilter.Comment -> "comment"
        NotificationFilter.Like -> "like"
    }

private fun <Wire : Any, Ui : Any> PageV1<Wire>.toPagingResult(map: (Wire) -> Ui): PagingResult<Ui> =
    PagingResult(
        data = items.map(map),
        nextKey = olderCursor.takeUnless { endReached },
        previousKey = newerCursor,
    )

private val LIST_OPERATIONS =
    arrayOf("page", "create", "update", "delete", "timeline", "members", "memberships", "addMember", "removeMember")
private val DIRECT_MESSAGE_OPERATIONS = arrayOf("rooms", "messages", "send", "delete", "leave", "create", "badge", "canSend")
