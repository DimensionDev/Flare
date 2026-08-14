package dev.dimension.flare.feature.plugin.adapter

import dev.dimension.flare.common.FileItem
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeCapability
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.DataSourceCapabilityProvider
import dev.dimension.flare.data.datasource.microblog.DataSourceCapabilitySet
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.PostCapability
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileCapability
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.SearchCapability
import dev.dimension.flare.data.datasource.microblog.TabCatalogCapability
import dev.dimension.flare.data.datasource.microblog.TimelineCapability
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.host.PluginAsset
import dev.dimension.flare.feature.plugin.host.PluginCallTimeoutV1
import dev.dimension.flare.feature.plugin.host.PluginInvocationContextV1
import dev.dimension.flare.feature.plugin.host.PluginUrlPolicy
import dev.dimension.flare.feature.plugin.host.platformUuid
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.login.accountHost
import dev.dimension.flare.feature.plugin.manifest.ProfileTabManifestV1
import dev.dimension.flare.feature.plugin.manifest.TimelineManifestV1
import dev.dimension.flare.feature.plugin.manifest.toUiText
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimeKeyV1
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimePool
import dev.dimension.flare.feature.plugin.wire.ComposeAssetV1
import dev.dimension.flare.feature.plugin.wire.ComposeConfigV1
import dev.dimension.flare.feature.plugin.wire.ComposePollV1
import dev.dimension.flare.feature.plugin.wire.ComposeRequestV1
import dev.dimension.flare.feature.plugin.wire.ComposeResultV1
import dev.dimension.flare.feature.plugin.wire.EntityKeyV1
import dev.dimension.flare.feature.plugin.wire.EntityRequestV1
import dev.dimension.flare.feature.plugin.wire.HandleRequestV1
import dev.dimension.flare.feature.plugin.wire.HashtagV1
import dev.dimension.flare.feature.plugin.wire.MutationRequestV1
import dev.dimension.flare.feature.plugin.wire.MutationResultV1
import dev.dimension.flare.feature.plugin.wire.PageDirectionV1
import dev.dimension.flare.feature.plugin.wire.PageV1
import dev.dimension.flare.feature.plugin.wire.PostV1
import dev.dimension.flare.feature.plugin.wire.ProfileTimelineRequestV1
import dev.dimension.flare.feature.plugin.wire.ProfileV1
import dev.dimension.flare.feature.plugin.wire.SearchRequestV1
import dev.dimension.flare.feature.plugin.wire.SemanticActionV1
import dev.dimension.flare.feature.plugin.wire.TimelineDisplayV1
import dev.dimension.flare.feature.plugin.wire.TimelinePageRequestV1
import dev.dimension.flare.feature.plugin.wire.VisibilityV1
import dev.dimension.flare.feature.plugin.wire.requireValid
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asType
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope

/** Lazy proxy: construction reads only the Host-owned credential envelope and never starts QuickJS. */
internal class PluginDataSourceV1 private constructor(
    private val plugin: RunningPluginV1,
    private val runtimePool: PluginRuntimePool,
    override val authenticatedAccountKey: MicroBlogKey?,
    private val origin: String,
    private val operations: Map<String, Set<String>>,
    private val invoker: PluginAccountInvokerV1,
    private val mapper: PluginWireMapperV1,
    private val timelineSpecs: Map<String, TimelineSpec<TimelineSpec.AccountResourceData>>,
    private val dynamicTimelineSpec: TimelineSpec<PluginTimelineDataV1>?,
    private val coroutineScope: CoroutineScope?,
    private val cachedComposeConfig: ComposeConfigV1?,
) : MicroblogDataSource,
    DataSourceCapabilityProvider,
    PluginNamedTimelineDataSourceV1 {
    private val accountKey: MicroBlogKey?
        get() = authenticatedAccountKey

    private val originHost: String = Url(origin).accountHost()

    private val accountType: AccountType = accountKey?.let(AccountType::Specific) ?: AccountType.GuestHost(originHost)

    private val extraCapabilities: PluginExtraCapabilitiesV1 by lazy {
        PluginExtraCapabilitiesV1(
            plugin = plugin,
            base = this,
            invoker = invoker,
            mapper = mapper,
            accountKey = accountKey,
            operations = operations,
            directions = ::directions,
            coroutineScope = coroutineScope,
            dynamicTimelineSpec = dynamicTimelineSpec,
        )
    }

    override val capabilitySet: DataSourceCapabilitySet by lazy {
        DataSourceCapabilitySet(
            timeline = capability(PluginAbiV1.Capabilities.TIMELINE)?.let { TimelineCapability(this) },
            search = capability(PluginAbiV1.Capabilities.SEARCH)?.let { SearchCapability(this) },
            profile =
                capability(PluginAbiV1.Capabilities.PROFILE)
                    ?.takeIf { "byId" in it || "byHandle" in it }
                    ?.let { ProfileCapability(this, userDataSource) },
            post =
                capability(PluginAbiV1.Capabilities.POST)
                    ?.takeIf { "detail" in it }
                    ?.let { PostCapability(this, postDataSource) },
            compose =
                accountKey
                    ?.takeIf { hasOperation(PluginAbiV1.Capabilities.COMPOSE, "publish") }
                    ?.let { ComposeCapabilityAdapter() }
                    ?.let(::ComposeCapability),
            relation = extraCapabilities.relation,
            notification = extraCapabilities.notification,
            list = extraCapabilities.list,
            directMessage = extraCapabilities.directMessage,
            article = extraCapabilities.article,
            gallery = extraCapabilities.gallery,
            tabCatalog =
                if (
                    accountKey != null &&
                    (
                        plugin.installed.manifest.platform.timelines
                            .isNotEmpty() || extraCapabilities.pinnableTabs != null
                    )
                ) {
                    TabCatalogCapability(
                        configuration =
                            TabConfiguration().takeIf {
                                plugin.installed.manifest.platform.timelines
                                    .isNotEmpty()
                            },
                        pinnable = extraCapabilities.pinnableTabs,
                    )
                } else {
                    null
                },
        )
    }

    private val userDataSource: UserDataSource by lazy {
        object : UserDataSource {
            override val userHandler: UserHandler =
                UserHandler(
                    host = originHost,
                    loader =
                        object : UserLoader {
                            override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
                                requireOperation(PluginAbiV1.Capabilities.PROFILE, "byHandle")
                                return invoker
                                    .invoke(
                                        capabilityId = PluginAbiV1.Capabilities.PROFILE,
                                        operation = "byHandle",
                                        request = HandleRequestV1(uiHandle.raw, uiHandle.normalizedHost),
                                        requestSerializer = HandleRequestV1.serializer(),
                                        responseSerializer = ProfileV1.serializer(),
                                        validate = ProfileV1::requireValid,
                                    ).let(mapper::profile)
                            }

                            override suspend fun userById(id: String): UiProfile {
                                requireOperation(PluginAbiV1.Capabilities.PROFILE, "byId")
                                return invoker
                                    .invoke(
                                        capabilityId = PluginAbiV1.Capabilities.PROFILE,
                                        operation = "byId",
                                        request = EntityRequestV1(EntityKeyV1(id, originHost)),
                                        requestSerializer = EntityRequestV1.serializer(),
                                        responseSerializer = ProfileV1.serializer(),
                                        validate = ProfileV1::requireValid,
                                    ).let(mapper::profile)
                            }
                        },
                )
        }
    }

    private val postDataSource: PostDataSource by lazy {
        object : PostDataSource {
            override val postHandler: PostHandler =
                PostHandler(
                    accountType = accountType,
                    loader =
                        object : PostLoader {
                            override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 {
                                requireOperation(PluginAbiV1.Capabilities.POST, "detail")
                                return invoker
                                    .invoke(
                                        capabilityId = PluginAbiV1.Capabilities.POST,
                                        operation = "detail",
                                        request = EntityRequestV1(statusKey.toWire()),
                                        requestSerializer = EntityRequestV1.serializer(),
                                        responseSerializer = PostV1.serializer(),
                                        validate = PostV1::requireValid,
                                    ).let(mapper::post)
                            }

                            override suspend fun deleteStatus(statusKey: MicroBlogKey) {
                                requireOperation(PluginAbiV1.Capabilities.POST, "delete")
                                invoker.invoke(
                                    capabilityId = PluginAbiV1.Capabilities.POST,
                                    operation = "delete",
                                    request = EntityRequestV1(statusKey.toWire()),
                                    requestSerializer = EntityRequestV1.serializer(),
                                    responseSerializer = MutationResultV1.serializer(),
                                    validate = MutationResultV1::requireValid,
                                )
                            }
                        },
                )
            override val postEventHandler: PostEventHandler =
                PostEventHandler(
                    accountType,
                    object : PostEventHandler.Handler {
                        override suspend fun handle(
                            event: PostEvent,
                            updater: dev.dimension.flare.data.datasource.microblog.DatabaseUpdater,
                        ) {
                            if (event !is PostEvent.Semantic) return
                            requireOperation(PluginAbiV1.Capabilities.POST, "mutate")
                            val result =
                                invoker.invoke(
                                    capabilityId = PluginAbiV1.Capabilities.POST,
                                    operation = "mutate",
                                    request =
                                        MutationRequestV1(
                                            key = event.postKey.toWire(),
                                            action = event.action.toWire(),
                                            actionToken = event.actionToken,
                                        ),
                                    requestSerializer = MutationRequestV1.serializer(),
                                    responseSerializer = MutationResultV1.serializer(),
                                    validate = MutationResultV1::requireValid,
                                )
                            when (result) {
                                is MutationResultV1.UpdatedPost -> {
                                    val updated = mapper.post(result.post)
                                    updater.updateCache(event.postKey) { updated }
                                }

                                MutationResultV1.Deleted -> {
                                    updater.deleteFromCache(event.postKey)
                                }

                                is MutationResultV1.Invalidate -> {
                                    result.keys.forEach { updater.deleteFromCache(it.toKey()) }
                                }

                                MutationResultV1.NoChange,
                                is MutationResultV1.UpdatedProfile,
                                is MutationResultV1.UpdatedRelation,
                                -> {
                                }
                            }
                        }
                    },
                )
        }
    }

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> {
        val timeline =
            plugin.installed.manifest.platform.timelines
                .firstOrNull(TimelineManifestV1::defaultForNewAccount)
                ?: plugin.installed.manifest.platform.timelines
                    .firstOrNull { it.id.equals("home", ignoreCase = true) }
                ?: plugin.installed.manifest.platform.timelines
                    .firstOrNull()
                ?: return notSupported()
        return timeline(timeline.id, timeline.parameters)
    }

    override fun timeline(
        timelineId: String,
        parameters: Map<String, String>,
    ): RemoteLoader<UiTimelineV2> {
        val declared =
            plugin.installed.manifest.platform.timelines
                .singleOrNull { it.id == timelineId } ?: return notSupported()
        if (!hasOperation(PluginAbiV1.Capabilities.TIMELINE, "page")) return notSupported()
        return postPageLoader(PluginAbiV1.Capabilities.TIMELINE, "page") { pageSize, request ->
            invoker.invoke(
                capabilityId = PluginAbiV1.Capabilities.TIMELINE,
                operation = "page",
                request = TimelinePageRequestV1(timelineId, request.toWire(pageSize), declared.parameters + parameters),
                requestSerializer = TimelinePageRequestV1.serializer(),
                responseSerializer = PageV1.serializer(PostV1.serializer()),
                validate = { page -> page.requireValid { it.requireValid() } },
            )
        }
    }

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> {
        if (!hasOperation(PluginAbiV1.Capabilities.PROFILE, "timeline")) return notSupported()
        val manifestTab =
            plugin.installed.manifest.platform.profileTabs.firstOrNull {
                mediaOnly == (it.display == TimelineDisplayV1.Grid)
            }
        return profileTimelineLoader(userKey, manifestTab)
    }

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> {
        if (!hasOperation(PluginAbiV1.Capabilities.POST, "context")) return notSupported()
        return postPageLoader(PluginAbiV1.Capabilities.POST, "context") { pageSize, request ->
            invoker.invoke(
                capabilityId = PluginAbiV1.Capabilities.POST,
                operation = "context",
                request =
                    dev.dimension.flare.feature.plugin.wire.EntityPageRequestV1(
                        key = statusKey.toWire(),
                        page = request.toWire(pageSize),
                    ),
                requestSerializer =
                    dev.dimension.flare.feature.plugin.wire.EntityPageRequestV1
                        .serializer(),
                responseSerializer = PageV1.serializer(PostV1.serializer()),
                validate = { page -> page.requireValid { it.requireValid() } },
            )
        }
    }

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = searchPosts("posts", query)

    override fun searchUser(query: String): RemoteLoader<UiProfile> = searchProfiles("profiles", query)

    override fun discoverUsers(): RemoteLoader<UiProfile> = searchProfiles("discoverProfiles", "")

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = searchPosts("discoverPosts", "")

    override fun discoverHashtags(): RemoteLoader<UiHashtag> {
        val operation = "discoverHashtags"
        if (!hasOperation(PluginAbiV1.Capabilities.SEARCH, operation)) return notSupported()
        return pluginRemoteLoader(
            directions = directions(PluginAbiV1.Capabilities.SEARCH, operation),
            load = { pageSize, request ->
                invoker.invoke(
                    capabilityId = PluginAbiV1.Capabilities.SEARCH,
                    operation = operation,
                    request = SearchRequestV1("", request.toWire(pageSize)),
                    requestSerializer = SearchRequestV1.serializer(),
                    responseSerializer = PageV1.serializer(HashtagV1.serializer()),
                    validate = { page -> page.requireValid(HashtagV1::requireValid) },
                )
            },
            map = mapper::hashtag,
        )
    }

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = profilePage("following", userKey)

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = profilePage("followers", userKey)

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> =
        plugin.installed.manifest.platform.profileTabs
            .map { tab ->
                ProfileTab(
                    id = "plugin:${plugin.installed.pluginId}:${tab.id}",
                    name = tab.title.toUiText(plugin.installed.pluginId),
                    displayType =
                        when (tab.display) {
                            TimelineDisplayV1.List -> ProfileTab.DisplayType.Timeline
                            TimelineDisplayV1.Grid -> ProfileTab.DisplayType.Gallery
                        },
                    loader = profileTimelineLoader(userKey, tab),
                )
            }.toImmutableList()

    private fun profileTimelineLoader(
        userKey: MicroBlogKey,
        tab: ProfileTabManifestV1?,
    ): RemoteLoader<UiTimelineV2> =
        postPageLoader(PluginAbiV1.Capabilities.PROFILE, "timeline") { pageSize, request ->
            invoker.invoke(
                capabilityId = PluginAbiV1.Capabilities.PROFILE,
                operation = "timeline",
                request =
                    ProfileTimelineRequestV1(
                        profile = userKey.toWire(),
                        tabId = tab?.id,
                        page = request.toWire(pageSize),
                        parameters = tab?.parameters.orEmpty(),
                    ),
                requestSerializer = ProfileTimelineRequestV1.serializer(),
                responseSerializer = PageV1.serializer(PostV1.serializer()),
                validate = { page -> page.requireValid { it.requireValid() } },
            )
        }

    private fun profilePage(
        operation: String,
        userKey: MicroBlogKey,
    ): RemoteLoader<UiProfile> {
        if (!hasOperation(PluginAbiV1.Capabilities.PROFILE, operation)) return notSupported()
        return pluginRemoteLoader(
            directions = directions(PluginAbiV1.Capabilities.PROFILE, operation),
            load = { pageSize, request ->
                invoker.invoke(
                    capabilityId = PluginAbiV1.Capabilities.PROFILE,
                    operation = operation,
                    request =
                        dev.dimension.flare.feature.plugin.wire.EntityPageRequestV1(
                            key = userKey.toWire(),
                            page = request.toWire(pageSize),
                        ),
                    requestSerializer =
                        dev.dimension.flare.feature.plugin.wire.EntityPageRequestV1
                            .serializer(),
                    responseSerializer = PageV1.serializer(ProfileV1.serializer()),
                    validate = { page -> page.requireValid(ProfileV1::requireValid) },
                )
            },
            map = mapper::profile,
        )
    }

    private fun searchPosts(
        operation: String,
        query: String,
    ): RemoteLoader<UiTimelineV2> {
        if (!hasOperation(PluginAbiV1.Capabilities.SEARCH, operation)) return notSupported()
        return postPageLoader(PluginAbiV1.Capabilities.SEARCH, operation) { pageSize, request ->
            invoker.invoke(
                capabilityId = PluginAbiV1.Capabilities.SEARCH,
                operation = operation,
                request = SearchRequestV1(query, request.toWire(pageSize)),
                requestSerializer = SearchRequestV1.serializer(),
                responseSerializer = PageV1.serializer(PostV1.serializer()),
                validate = { page -> page.requireValid { it.requireValid() } },
            )
        }
    }

    private fun searchProfiles(
        operation: String,
        query: String,
    ): RemoteLoader<UiProfile> {
        if (!hasOperation(PluginAbiV1.Capabilities.SEARCH, operation)) return notSupported()
        return pluginRemoteLoader(
            directions = directions(PluginAbiV1.Capabilities.SEARCH, operation),
            load = { pageSize, request ->
                invoker.invoke(
                    capabilityId = PluginAbiV1.Capabilities.SEARCH,
                    operation = operation,
                    request = SearchRequestV1(query, request.toWire(pageSize)),
                    requestSerializer = SearchRequestV1.serializer(),
                    responseSerializer = PageV1.serializer(ProfileV1.serializer()),
                    validate = { page -> page.requireValid(ProfileV1::requireValid) },
                )
            },
            map = mapper::profile,
        )
    }

    private fun postPageLoader(
        capabilityId: String,
        operation: String,
        load: suspend (pageSize: Int, request: PagingRequest) -> PageV1<PostV1>,
    ): RemoteLoader<UiTimelineV2> = pluginRemoteLoader(directions(capabilityId, operation), load, mapper::post)

    private inner class ComposeCapabilityAdapter :
        ComposeDataSource,
        AuthenticatedMicroblogDataSource by AuthenticatedDelegate() {
        override suspend fun compose(
            data: ComposeData,
            progress: () -> Unit,
        ) {
            val assets = linkedMapOf<String, PluginAsset>()
            val wireAssets =
                data.medias.map { media ->
                    val handle = "asset_${platformUuid()}"
                    assets[handle] = media.file.toPluginAsset()
                    ComposeAssetV1(
                        handle = handle,
                        fileName = media.file.name,
                        mimeType = media.file.mimeType,
                        description = media.altText,
                    )
                }
            validateComposeData(data, assets, cachedComposeConfig)
            progress()
            val result =
                invoker.invoke(
                    capabilityId = PluginAbiV1.Capabilities.COMPOSE,
                    operation = "publish",
                    request =
                        ComposeRequestV1(
                            text = data.content,
                            visibility = data.visibility.toWire(),
                            languages = data.language,
                            assets = wireAssets,
                            sensitive = data.sensitive,
                            spoilerText = data.spoilerText,
                            replyTo =
                                data.referenceStatus
                                    ?.composeStatus
                                    ?.statusKey
                                    ?.toWire(),
                            poll =
                                data.poll?.let {
                                    ComposePollV1(
                                        options = it.options,
                                        expiresInSeconds = it.expiredAfter,
                                        multiple = it.multiple,
                                    )
                                },
                        ),
                    requestSerializer = ComposeRequestV1.serializer(),
                    responseSerializer = ComposeResultV1.serializer(),
                    timeout = PluginCallTimeoutV1.Extended,
                    assets = assets,
                    validate = ComposeResultV1::requireValid,
                )
            mapper.post(result.post)
            progress()
        }

        override fun composeConfig(type: ComposeType): ComposeConfig = cachedComposeConfig.toHostComposeConfig()
    }

    private inner class AuthenticatedDelegate :
        AuthenticatedMicroblogDataSource,
        MicroblogDataSource by this@PluginDataSourceV1 {
        override val accountKey: MicroBlogKey = requireNotNull(this@PluginDataSourceV1.accountKey)
    }

    private inner class TabConfiguration : TimelineTabConfigurationDataSource {
        override val defaultTabs: ImmutableList<TimelineCandidate<*>> =
            plugin.installed.manifest.platform.timelines
                .filter(TimelineManifestV1::defaultForNewAccount)
                .mapNotNull(::candidate)
                .toImmutableList()

        override val builtInTimelineTabs: ImmutableList<TimelineCandidate<*>> =
            plugin.installed.manifest.platform.timelines
                .mapNotNull(::candidate)
                .toImmutableList()

        override val shortcuts: ImmutableList<dev.dimension.flare.data.model.tab.ShortcutSpec> = persistentListOf()

        private fun candidate(timeline: TimelineManifestV1): TimelineCandidate<*>? {
            val key = requireNotNull(accountKey)
            val spec = timelineSpecs[timeline.id] ?: return null
            val data = TimelineSpec.AccountResourceData(key, timeline.id)
            val title = timeline.title.toUiText(plugin.installed.pluginId)
            val icon = timeline.icon.toUiIcon().asType()
            return when (timeline.display) {
                TimelineDisplayV1.List -> spec.candidate(data, title, icon)
                TimelineDisplayV1.Grid -> spec.galleryCandidate(data, title, icon)
            }
        }
    }

    private fun capability(id: String): Set<String>? = operations[id]?.takeIf(Set<String>::isNotEmpty)

    private fun hasOperation(
        capabilityId: String,
        operation: String,
    ): Boolean = operation in operations[capabilityId].orEmpty()

    private fun requireOperation(
        capabilityId: String,
        operation: String,
    ) {
        require(hasOperation(capabilityId, operation)) { "Plugin operation is unavailable: $capabilityId/$operation" }
    }

    private fun directions(
        capabilityId: String,
        operation: String,
    ): Set<PageDirectionV1> =
        plugin.installed.manifest.platform.capabilities[capabilityId]
            ?.operations
            ?.get(operation)
            ?.directions
            .orEmpty()

    internal companion object {
        fun authenticated(
            plugin: RunningPluginV1,
            runtimePool: PluginRuntimePool,
            context: PlatformDataSourceContext,
            timelineSpecs: Map<String, TimelineSpec<TimelineSpec.AccountResourceData>>,
            dynamicTimelineSpec: TimelineSpec<PluginTimelineDataV1>?,
            coroutineScope: CoroutineScope,
        ): PluginDataSourceV1 {
            val credentialAccess = PlatformDataSourceCredentialAccessV1(plugin, context)
            val account = credentialAccess.current()
            val operations = account.effectiveCapabilities(plugin)
            val runtimeKey =
                PluginRuntimeKeyV1.account(
                    plugin.installed.pluginId,
                    plugin.installed.packageHash,
                    account.snapshot.origin,
                    account.snapshot.accountId,
                )
            val invoker =
                PluginAccountInvokerV1(
                    plugin = plugin,
                    runtimePool = runtimePool,
                    runtimeKey = runtimeKey,
                    accountKey = context.accountKey,
                    context = { locale, assets ->
                        plugin.accountInvocationContext(credentialAccess.current(), locale, credentialAccess, assets)
                    },
                )
            return create(
                plugin = plugin,
                runtimePool = runtimePool,
                accountKey = context.accountKey,
                origin = account.snapshot.origin,
                operations = operations,
                invoker = invoker,
                timelineSpecs = timelineSpecs,
                dynamicTimelineSpec = dynamicTimelineSpec,
                coroutineScope = coroutineScope,
                composeConfig =
                    mergePluginComposeConfigV1(
                        plugin.installed.manifest.platform.composeDefaults,
                        account.snapshot.composeConfig,
                    ),
            )
        }

        fun guest(
            plugin: RunningPluginV1,
            runtimePool: PluginRuntimePool,
            origin: String,
            locale: String,
            timelineSpecs: Map<String, TimelineSpec<TimelineSpec.AccountResourceData>>,
            dynamicTimelineSpec: TimelineSpec<PluginTimelineDataV1>?,
        ): PluginDataSourceV1 {
            require(
                plugin.installed.manifest.platform.guest
                    ?.enabled == true,
            ) { "Plugin guest access is unavailable" }
            val canonicalOrigin = PluginUrlPolicy.requireOrigin(origin)
            val runtimeKey =
                PluginRuntimeKeyV1.guest(
                    plugin.installed.pluginId,
                    plugin.installed.packageHash,
                    canonicalOrigin,
                )
            val invoker =
                PluginAccountInvokerV1(
                    plugin = plugin,
                    runtimePool = runtimePool,
                    runtimeKey = runtimeKey,
                    accountKey = null,
                    context = { invocationLocale, _ ->
                        PluginInvocationContextV1.guest(
                            pluginId = plugin.installed.pluginId,
                            platformId = plugin.installed.manifest.platform.id,
                            packageHash = plugin.installed.packageHash,
                            origin = canonicalOrigin,
                            locale = invocationLocale.ifBlank { locale },
                        )
                    },
                )
            return create(
                plugin = plugin,
                runtimePool = runtimePool,
                accountKey = null,
                origin = canonicalOrigin,
                operations =
                    plugin.installed.manifest.platform.capabilities
                        .mapValues { it.value.operations.keys },
                invoker = invoker,
                timelineSpecs = timelineSpecs,
                dynamicTimelineSpec = dynamicTimelineSpec,
                coroutineScope = null,
                composeConfig = null,
            )
        }

        private fun create(
            plugin: RunningPluginV1,
            runtimePool: PluginRuntimePool,
            accountKey: MicroBlogKey?,
            origin: String,
            operations: Map<String, Set<String>>,
            invoker: PluginAccountInvokerV1,
            timelineSpecs: Map<String, TimelineSpec<TimelineSpec.AccountResourceData>>,
            dynamicTimelineSpec: TimelineSpec<PluginTimelineDataV1>?,
            coroutineScope: CoroutineScope?,
            composeConfig: ComposeConfigV1?,
        ): PluginDataSourceV1 =
            PluginDataSourceV1(
                plugin = plugin,
                runtimePool = runtimePool,
                authenticatedAccountKey = accountKey,
                origin = origin,
                operations = operations,
                invoker = invoker,
                mapper =
                    PluginWireMapperV1(
                        pluginId = plugin.installed.pluginId,
                        platformId = plugin.installed.manifest.platform.id,
                        accountKey = accountKey,
                        originHost = Url(origin).accountHost(),
                        profileAvailable = operations[PluginAbiV1.Capabilities.PROFILE].orEmpty().any { it == "byId" || it == "byHandle" },
                        postDetailAvailable = "detail" in operations[PluginAbiV1.Capabilities.POST].orEmpty(),
                        postMutationAvailable = "mutate" in operations[PluginAbiV1.Capabilities.POST].orEmpty(),
                        composeAvailable = accountKey != null && "publish" in operations[PluginAbiV1.Capabilities.COMPOSE].orEmpty(),
                    ),
                timelineSpecs = timelineSpecs,
                dynamicTimelineSpec = dynamicTimelineSpec,
                coroutineScope = coroutineScope,
                cachedComposeConfig = composeConfig,
            )
    }
}

internal interface PluginNamedTimelineDataSourceV1 {
    fun timeline(
        timelineId: String,
        parameters: Map<String, String> = emptyMap(),
    ): RemoteLoader<UiTimelineV2>
}

private fun MicroBlogKey.toWire(): EntityKeyV1 = EntityKeyV1(id, host)

private fun EntityKeyV1.toKey(): MicroBlogKey = MicroBlogKey(id, host)

private fun dev.dimension.flare.data.datasource.microblog.SemanticPostAction.toWire(): SemanticActionV1 =
    when (this) {
        dev.dimension.flare.data.datasource.microblog.SemanticPostAction.Favourite -> SemanticActionV1.Favourite
        dev.dimension.flare.data.datasource.microblog.SemanticPostAction.Unfavourite -> SemanticActionV1.Unfavourite
        dev.dimension.flare.data.datasource.microblog.SemanticPostAction.Repost -> SemanticActionV1.Repost
        dev.dimension.flare.data.datasource.microblog.SemanticPostAction.Unrepost -> SemanticActionV1.Unrepost
        dev.dimension.flare.data.datasource.microblog.SemanticPostAction.Bookmark -> SemanticActionV1.Bookmark
        dev.dimension.flare.data.datasource.microblog.SemanticPostAction.Unbookmark -> SemanticActionV1.Unbookmark
    }

private suspend fun FileItem.toPluginAsset(): PluginAsset {
    val item = this
    val itemSize = size()
    return object : PluginAsset {
        override val size: Long = itemSize
        override val fileName: String? = item.name
        override val mimeType: String? = item.mimeType

        override fun openSource(): okio.Source = item.openSource()
    }
}

private fun UiTimelineV2.Post.Visibility.toWire(): VisibilityV1 =
    when (this) {
        UiTimelineV2.Post.Visibility.Public -> VisibilityV1.Public
        UiTimelineV2.Post.Visibility.Home -> VisibilityV1.Unlisted
        UiTimelineV2.Post.Visibility.Followers -> VisibilityV1.Followers
        UiTimelineV2.Post.Visibility.Specified -> VisibilityV1.Direct
        UiTimelineV2.Post.Visibility.Channel -> VisibilityV1.Public
    }

private fun VisibilityV1.toHost(): UiTimelineV2.Post.Visibility =
    when (this) {
        VisibilityV1.Public -> UiTimelineV2.Post.Visibility.Public
        VisibilityV1.Unlisted -> UiTimelineV2.Post.Visibility.Home
        VisibilityV1.Followers -> UiTimelineV2.Post.Visibility.Followers
        VisibilityV1.Direct -> UiTimelineV2.Post.Visibility.Specified
    }

private fun ComposeConfigV1?.toHostComposeConfig(): ComposeConfig {
    val config = this ?: return ComposeConfig()
    return ComposeConfig(
        text = config.text?.let { ComposeConfig.Text(it.maxLength) },
        media =
            config.media?.let {
                ComposeConfig.Media(
                    maxCount = it.maxCount,
                    canSensitive = it.canSensitive,
                    altTextMaxLength = it.altTextMaxLength,
                    allowMediaOnly = true,
                    minCountForNew = it.minCountForNew,
                    supportedMimeTypes = it.supportedMimeTypes.takeIf(Set<String>::isNotEmpty),
                    compression = ComposeConfig.Media.Compression(maxSizeBytes = it.maxBytes),
                )
            },
        poll = config.poll?.let { ComposeConfig.Poll(it.maxOptions) },
        contentWarning = ComposeConfig.ContentWarning.takeIf { config.contentWarning },
        visibility =
            config.visibility?.let {
                ComposeConfig.Visibility(
                    allowedValues = it.allowed.mapTo(linkedSetOf(), VisibilityV1::toHost),
                    defaultValue = it.default.toHost(),
                )
            },
        language = config.language?.let { ComposeConfig.Language(it.maxCount) },
    )
}

internal fun mergePluginComposeConfigV1(
    manifest: ComposeConfigV1?,
    instance: ComposeConfigV1?,
): ComposeConfigV1? {
    if (manifest == null) return instance?.also(ComposeConfigV1::requireValid)
    if (instance == null) return manifest.also(ComposeConfigV1::requireValid)
    val media =
        when {
            manifest.media == null -> {
                instance.media
            }

            instance.media == null -> {
                manifest.media
            }

            else -> {
                val minCountForNew = maxOf(manifest.media.minCountForNew, instance.media.minCountForNew)
                val maxCount = minOf(manifest.media.maxCount, instance.media.maxCount)
                require(minCountForNew <= maxCount) { "Plugin compose media constraints do not overlap" }
                ComposeConfigV1.MediaConfigV1(
                    minCountForNew = minCountForNew,
                    maxCount = maxCount,
                    maxBytes = minOf(manifest.media.maxBytes, instance.media.maxBytes),
                    supportedMimeTypes = intersectMimeConstraints(manifest.media.supportedMimeTypes, instance.media.supportedMimeTypes),
                    altTextMaxLength = minOf(manifest.media.altTextMaxLength, instance.media.altTextMaxLength),
                    canSensitive = manifest.media.canSensitive && instance.media.canSensitive,
                )
            }
        }
    val visibility =
        when {
            manifest.visibility == null -> {
                instance.visibility
            }

            instance.visibility == null -> {
                manifest.visibility
            }

            else -> {
                val allowed = manifest.visibility.allowed intersect instance.visibility.allowed
                require(allowed.isNotEmpty()) { "Plugin compose visibility constraints do not overlap" }
                ComposeConfigV1.VisibilityConfigV1(
                    allowed = allowed,
                    default =
                        manifest.visibility.default.takeIf(allowed::contains)
                            ?: instance.visibility.default.takeIf(allowed::contains)
                            ?: allowed.first(),
                )
            }
        }
    return ComposeConfigV1(
        text =
            when {
                manifest.text == null -> instance.text
                instance.text == null -> manifest.text
                else -> ComposeConfigV1.TextConfigV1(minOf(manifest.text.maxLength, instance.text.maxLength))
            },
        media = media,
        visibility = visibility,
        contentWarning = manifest.contentWarning && instance.contentWarning,
        poll =
            when {
                manifest.poll == null -> instance.poll
                instance.poll == null -> manifest.poll
                else -> ComposeConfigV1.PollConfigV1(minOf(manifest.poll.maxOptions, instance.poll.maxOptions))
            },
        language =
            when {
                manifest.language == null -> instance.language
                instance.language == null -> manifest.language
                else -> ComposeConfigV1.LanguageConfigV1(minOf(manifest.language.maxCount, instance.language.maxCount))
            },
    ).also(ComposeConfigV1::requireValid)
}

private fun intersectMimeConstraints(
    first: Set<String>,
    second: Set<String>,
): Set<String> {
    val normalizedFirst = first.mapTo(linkedSetOf(), String::lowercase)
    val normalizedSecond = second.mapTo(linkedSetOf(), String::lowercase)
    if (normalizedFirst.isEmpty()) return normalizedSecond
    if (normalizedSecond.isEmpty()) return normalizedFirst
    return normalizedFirst
        .flatMapTo(linkedSetOf()) { firstType ->
            normalizedSecond.mapNotNull { secondType -> intersectMimeType(firstType, secondType) }
        }.also {
            require(it.isNotEmpty()) { "Plugin compose MIME constraints do not overlap" }
        }
}

private fun intersectMimeType(
    first: String,
    second: String,
): String? =
    when {
        first == second -> first
        first.endsWith("/*") && second.startsWith(first.removeSuffix("*")) -> second
        second.endsWith("/*") && first.startsWith(second.removeSuffix("*")) -> first
        else -> null
    }

private fun validateComposeData(
    data: ComposeData,
    assets: Map<String, PluginAsset>,
    config: ComposeConfigV1?,
) {
    data.referenceStatus
        ?.composeStatus
        ?.statusKey
        ?.let { EntityKeyV1(it.id, it.host).requireValid() }
    require(data.localOnly.not()) { "Plugin compose does not support local-only posts" }
    require(data.language.size <= 32 && data.language.all { it.length in 1..64 }) { "Invalid compose languages" }
    val poll = data.poll
    require((poll == null) || (poll.options.size in 2..100)) { "Invalid compose poll" }
    if (config == null) return

    config.text?.let { require(data.content.length <= it.maxLength) { "Compose text is too long" } }
    config.visibility?.let { require(data.visibility.toWire() in it.allowed) { "Compose visibility is not allowed" } }
    require(config.contentWarning || (data.spoilerText.isNullOrEmpty())) { "Content warnings are not supported" }
    config.poll?.let { require((poll == null) || (poll.options.size <= it.maxOptions)) { "Too many poll options" } }
    require((config.poll != null) || (poll == null)) { "Polls are not supported" }
    config.language?.let { require(data.language.size <= it.maxCount) { "Too many compose languages" } }

    val media = config.media
    require((media != null) || assets.isEmpty()) { "Media is not supported" }
    media ?: return
    val minimum = if (data.referenceStatus == null) media.minCountForNew else 0
    require(assets.size in minimum..media.maxCount) { "Invalid compose media count" }
    require(media.canSensitive || (!data.sensitive)) { "Sensitive media is not supported" }
    val supported = media.supportedMimeTypes.mapTo(linkedSetOf(), String::lowercase)
    data.medias.zip(assets.values).forEach { (item, asset) ->
        require(asset.size <= media.maxBytes) { "Compose media is too large" }
        val altText = item.altText
        require((altText == null) || (altText.length <= media.altTextMaxLength)) { "Media description is too long" }
        val mimeType =
            asset.mimeType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
        require(supported.isEmpty() || ((mimeType != null) && supported.any { it.matchesMimeType(mimeType) })) {
            "Compose media type is not supported"
        }
    }
}

private fun String.matchesMimeType(actual: String): Boolean = (this == actual) || (endsWith("/*") && actual.startsWith(removeSuffix("*")))

internal fun dev.dimension.flare.feature.plugin.wire.HostIconV1.toUiIcon(): dev.dimension.flare.ui.model.UiIcon =
    when (this) {
        dev.dimension.flare.feature.plugin.wire.HostIconV1.Home -> dev.dimension.flare.ui.model.UiIcon.Home
        dev.dimension.flare.feature.plugin.wire.HostIconV1.Notification -> dev.dimension.flare.ui.model.UiIcon.Notification
        dev.dimension.flare.feature.plugin.wire.HostIconV1.Search -> dev.dimension.flare.ui.model.UiIcon.Search
        dev.dimension.flare.feature.plugin.wire.HostIconV1.Profile -> dev.dimension.flare.ui.model.UiIcon.Profile
        dev.dimension.flare.feature.plugin.wire.HostIconV1.Local -> dev.dimension.flare.ui.model.UiIcon.Local
        dev.dimension.flare.feature.plugin.wire.HostIconV1.World -> dev.dimension.flare.ui.model.UiIcon.World
        dev.dimension.flare.feature.plugin.wire.HostIconV1.Featured -> dev.dimension.flare.ui.model.UiIcon.Featured
        dev.dimension.flare.feature.plugin.wire.HostIconV1.Bookmark -> dev.dimension.flare.ui.model.UiIcon.Bookmark
        dev.dimension.flare.feature.plugin.wire.HostIconV1.Heart -> dev.dimension.flare.ui.model.UiIcon.Heart
        dev.dimension.flare.feature.plugin.wire.HostIconV1.List -> dev.dimension.flare.ui.model.UiIcon.List
        dev.dimension.flare.feature.plugin.wire.HostIconV1.Messages -> dev.dimension.flare.ui.model.UiIcon.Messages
        dev.dimension.flare.feature.plugin.wire.HostIconV1.Channel -> dev.dimension.flare.ui.model.UiIcon.Channel
        dev.dimension.flare.feature.plugin.wire.HostIconV1.Like -> dev.dimension.flare.ui.model.UiIcon.Like
        dev.dimension.flare.feature.plugin.wire.HostIconV1.Repost -> dev.dimension.flare.ui.model.UiIcon.Retweet
        dev.dimension.flare.feature.plugin.wire.HostIconV1.Reply -> dev.dimension.flare.ui.model.UiIcon.Reply
        dev.dimension.flare.feature.plugin.wire.HostIconV1.Delete -> dev.dimension.flare.ui.model.UiIcon.Delete
        dev.dimension.flare.feature.plugin.wire.HostIconV1.Follow -> dev.dimension.flare.ui.model.UiIcon.Follow
        dev.dimension.flare.feature.plugin.wire.HostIconV1.Block -> dev.dimension.flare.ui.model.UiIcon.Block
        dev.dimension.flare.feature.plugin.wire.HostIconV1.Mute -> dev.dimension.flare.ui.model.UiIcon.Mute
        dev.dimension.flare.feature.plugin.wire.HostIconV1.Info -> dev.dimension.flare.ui.model.UiIcon.Info
    }
