package dev.dimension.flare.feature.plugin.adapter

import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.model.tab.TimelineLoaderFactory
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.toUiTimelineTabItem
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.feature.plugin.host.PluginCallTimeoutV1
import dev.dimension.flare.feature.plugin.host.PluginInvocationContextV1
import dev.dimension.flare.feature.plugin.host.PluginUrlPolicy
import dev.dimension.flare.feature.plugin.host.platformUuid
import dev.dimension.flare.feature.plugin.lifecycle.PluginRunningSnapshotV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateIssueV1
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.login.PluginCookieCheckResultV1
import dev.dimension.flare.feature.plugin.login.PluginFormLoginCoordinatorV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthCallbackCoordinatorV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthLoginCoordinatorV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthStartV1
import dev.dimension.flare.feature.plugin.login.PluginWebCookieLoginCoordinatorV1
import dev.dimension.flare.feature.plugin.login.PluginWebCookieRequestV1
import dev.dimension.flare.feature.plugin.login.PluginWebCookieSessionV1
import dev.dimension.flare.feature.plugin.login.accountHost
import dev.dimension.flare.feature.plugin.login.parsePluginOAuthCallback
import dev.dimension.flare.feature.plugin.manifest.DeepLinkManifestV1
import dev.dimension.flare.feature.plugin.manifest.DeepLinkPathSegmentV1
import dev.dimension.flare.feature.plugin.manifest.DeepLinkTargetTypeV1
import dev.dimension.flare.feature.plugin.manifest.LoginFieldTypeV1
import dev.dimension.flare.feature.plugin.manifest.LoginInteractionV1
import dev.dimension.flare.feature.plugin.manifest.LoginMethodManifestV1
import dev.dimension.flare.feature.plugin.manifest.PluginTextV1
import dev.dimension.flare.feature.plugin.manifest.TimelineManifestV1
import dev.dimension.flare.feature.plugin.manifest.toUiText
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimeKeyV1
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimePool
import dev.dimension.flare.feature.plugin.wire.CookieSnapshotV1
import dev.dimension.flare.feature.plugin.wire.CookieValueV1
import dev.dimension.flare.feature.plugin.wire.DetectorMatchV1
import dev.dimension.flare.feature.plugin.wire.DetectorRequestV1
import dev.dimension.flare.feature.plugin.wire.DetectorResultV1
import dev.dimension.flare.feature.plugin.wire.LoginSuccessV1
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.ComposeInitialTextContext
import dev.dimension.flare.model.InitialText
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformMetadata
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformSpecSource
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.model.resolveReplyParticipantInitialText
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.login.LoginAction
import dev.dimension.flare.ui.presenter.login.LoginContext
import dev.dimension.flare.ui.presenter.login.LoginCookieProbe
import dev.dimension.flare.ui.presenter.login.LoginCookieSnapshot
import dev.dimension.flare.ui.presenter.login.LoginEffect
import dev.dimension.flare.ui.presenter.login.LoginField
import dev.dimension.flare.ui.presenter.login.LoginFieldType
import dev.dimension.flare.ui.presenter.login.LoginFlowState
import dev.dimension.flare.ui.presenter.login.LoginMethodHandler
import dev.dimension.flare.ui.presenter.login.LoginMethodSpec
import dev.dimension.flare.ui.presenter.login.LoginMethodType
import dev.dimension.flare.ui.presenter.login.LoginPlatformProvider
import dev.dimension.flare.ui.presenter.login.NodeDetection
import dev.dimension.flare.ui.presenter.login.PlatformDetector
import dev.dimension.flare.ui.presenter.login.requireReloginAccount
import dev.dimension.flare.ui.route.DeeplinkRoute
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlin.native.HiddenFromObjC

/** Captures the immutable process snapshot; [load] does no I/O and executes no JavaScript. */
@HiddenFromObjC
public class PluginPlatformSpecSourceV1(
    private val running: PluginRunningSnapshotV1,
    private val runtimePool: PluginRuntimePool,
    private val oauth: PluginOAuthLoginCoordinatorV1,
    private val form: PluginFormLoginCoordinatorV1,
    private val webCookie: PluginWebCookieLoginCoordinatorV1,
    private val coroutineScope: CoroutineScope,
    private val onIssue: (PluginStateIssueV1) -> Unit = {},
) : PlatformSpecSource {
    override fun load(occupiedPlatformIds: Set<String>): List<PlatformSpec> {
        val occupied = occupiedPlatformIds.mapTo(mutableSetOf(), String::lowercase)
        return running.plugins.values
            .sortedBy { it.installed.pluginId }
            .mapNotNull { plugin ->
                val platformId = plugin.installed.manifest.platform.id
                if (!occupied.add(platformId.lowercase())) {
                    onIssue(
                        PluginStateIssueV1(
                            code = "platform.conflict",
                            pluginId = plugin.installed.pluginId,
                            message = "Platform ID is already registered: $platformId",
                        ),
                    )
                    null
                } else {
                    runCatching {
                        PluginPlatformSpecV1(
                            plugin = plugin,
                            runtimePool = runtimePool,
                            oauth = oauth,
                            form = form,
                            webCookie = webCookie,
                            coroutineScope = coroutineScope,
                        )
                    }.getOrElse { error ->
                        onIssue(
                            PluginStateIssueV1(
                                code = "platform.invalid",
                                pluginId = plugin.installed.pluginId,
                                message = error.message ?: "Plugin platform is invalid",
                            ),
                        )
                        null
                    }
                }
            }
    }
}

private class PluginPlatformSpecV1(
    private val plugin: RunningPluginV1,
    private val runtimePool: PluginRuntimePool,
    private val oauth: PluginOAuthLoginCoordinatorV1,
    private val form: PluginFormLoginCoordinatorV1,
    private val webCookie: PluginWebCookieLoginCoordinatorV1,
    private val coroutineScope: CoroutineScope,
) : PlatformSpec,
    LoginPlatformProvider {
    private val accountService: AccountService by koinInject()
    private val oauthCallbacks: PluginOAuthCallbackCoordinatorV1 by koinInject()
    private val manifest = plugin.installed.manifest
    private val platform = manifest.platform

    override val platformId: String = platform.id
    override val metadata: PlatformMetadata = plugin.platformMetadata()
    override val order: Int = 1_000 + (platform.detector?.priority ?: 0)
    override val isDefaultGuest: Boolean = false

    private val specs: Map<String, TimelineSpec<TimelineSpec.AccountResourceData>> =
        platform.timelines.associate { timeline ->
            timeline.id to timeline.toTimelineSpec()
        }

    private val dynamicTimelineSpec: TimelineSpec<PluginTimelineDataV1>? =
        platform.capabilities[dev.dimension.flare.feature.plugin.abi.PluginAbiV1.Capabilities.TAB_CATALOG]
            ?.operations
            ?.takeIf { "page" in it }
            ?.let {
                TimelineSpec(
                    id = canonicalTimelineSpecId("catalog"),
                    title = platform.name.toUiText(manifest.id),
                    icon =
                        dev.dimension.flare.ui.model.UiIcon.World
                            .asType(),
                    serializer = PluginTimelineDataV1.serializer(),
                    targetId = { data ->
                        "${data.accountKey}:${data.timelineId}:${data.parameters.entries.sortedBy { it.key }}"
                    },
                    loaderFactory =
                        TimelineLoaderFactory { data, context ->
                            context.accountServiceFlow(AccountType.Specific(data.accountKey)).map { service ->
                                (service as? PluginNamedTimelineDataSourceV1)?.timeline(data.timelineId, data.parameters) ?: notSupported()
                            }
                        },
                )
            }

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        buildList {
            addAll(specs.values)
            addAll(listOfNotNull(dynamicTimelineSpec))

            val legacyCandidates =
                buildList<Pair<String, TimelineSpec<out TimelineSpec.Data>>> {
                    platform.timelines.forEach { timeline ->
                        add(legacyTimelineSpecId(timeline.id) to specs.getValue(timeline.id))
                    }
                    dynamicTimelineSpec?.let { add(legacyTimelineSpecId("catalog") to it) }
                }
            legacyCandidates
                .groupBy { it.first }
                .values
                .filter { it.size == 1 }
                .forEach { candidates ->
                    val (legacyId, spec) = candidates.single()
                    add(spec.withId(legacyId))
                }
        }.toImmutableList()

    override fun resolveInitialText(context: ComposeInitialTextContext): InitialText? = context.resolveReplyParticipantInitialText()

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> =
        platform.deepLinks.map { it.toPlatformDeepLink(accountKey) }.toImmutableList()

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
        PluginDataSourceV1.authenticated(plugin, runtimePool, context, specs, dynamicTimelineSpec, coroutineScope)

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource =
        PluginDataSourceV1.guest(
            plugin = plugin,
            runtimePool = runtimePool,
            origin = host.toHttpsOrigin(),
            locale = locale,
            timelineSpecs = specs,
            dynamicTimelineSpec = dynamicTimelineSpec,
        )

    override val detector: PlatformDetector =
        object : PlatformDetector {
            override val priority: Int = platform.detector?.priority ?: Int.MIN_VALUE

            override suspend fun detect(host: String): NodeDetection? {
                if (platform.detector == null) return null
                val origin = host.toHttpsOrigin()
                val result =
                    runtimePool.invoke(
                        plugin = plugin,
                        key = PluginRuntimeKeyV1.detector(manifest.id, plugin.installed.packageHash, platformUuid()),
                        context =
                            PluginInvocationContextV1.detector(
                                pluginId = manifest.id,
                                platformId = platformId,
                                packageHash = plugin.installed.packageHash,
                                candidateOrigin = origin,
                                locale = Locale.language,
                            ),
                        method = "detector.detect",
                        request = DetectorRequestV1(origin),
                        requestSerializer = DetectorRequestV1.serializer(),
                        responseSerializer = DetectorResultV1.serializer(),
                        timeout = PluginCallTimeoutV1.Normal,
                        validate = DetectorResultV1::requireValid,
                    )
                if (result.match == DetectorMatchV1.None) return null
                val canonicalOrigin = PluginUrlPolicy.requireOrigin(result.canonicalOrigin)
                require(canonicalOrigin == origin) { "Detector changed the selected instance origin" }
                return NodeDetection(
                    host = Url(canonicalOrigin).accountHost(),
                    software = result.software ?: platform.name.fallback,
                    compatibleMode = result.compatibleMode,
                )
            }
        }

    override val methods: List<LoginMethodSpec> =
        platform.loginMethods.map { method ->
            LoginMethodSpec(
                type = method.interaction.toLoginMethodType(),
                title = method.title.toUiText(manifest.id),
            )
        }

    override fun agreementUrl(host: String): String? = null

    override suspend fun recommendInstances(): List<RecommendedInstance> = emptyList()

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata {
        val origin = host.toHttpsOrigin()
        val detected =
            if (platform.detector == null) {
                null
            } else {
                val result =
                    runtimePool
                        .invoke(
                            plugin = plugin,
                            key = PluginRuntimeKeyV1.detector(manifest.id, plugin.installed.packageHash, platformUuid()),
                            context =
                                PluginInvocationContextV1.detector(
                                    pluginId = manifest.id,
                                    platformId = platformId,
                                    packageHash = plugin.installed.packageHash,
                                    candidateOrigin = origin,
                                    locale = Locale.language,
                                ),
                            method = "detector.detect",
                            request = DetectorRequestV1(origin),
                            requestSerializer = DetectorRequestV1.serializer(),
                            responseSerializer = DetectorResultV1.serializer(),
                            validate = DetectorResultV1::requireValid,
                        )
                require(PluginUrlPolicy.requireOrigin(result.canonicalOrigin) == origin) {
                    "Detector changed the selected instance origin"
                }
                result.instance.takeUnless { result.match == DetectorMatchV1.None }
            }
        val domain = detected?.domain ?: Url(origin).accountHost()
        val compose = platform.composeDefaults
        return UiInstanceMetadata(
            instance =
                UiInstance(
                    name = detected?.title ?: domain,
                    description = detected?.description,
                    iconUrl = detected?.iconUrl,
                    domain = domain,
                    platformId = platformId,
                    bannerUrl = detected?.bannerUrl,
                    usersCount = detected?.usersCount ?: 0,
                    platformDisplayName = metadata.displayName,
                    platformIcon = metadata.icon,
                ),
            rules = persistentMapOf(),
            configuration =
                UiInstanceMetadata.Configuration(
                    registration = UiInstanceMetadata.Configuration.Registration(detected?.registrationEnabled ?: true),
                    statuses =
                        UiInstanceMetadata.Configuration.Statuses(
                            maxCharacters = compose?.text?.maxLength?.toLong() ?: 500,
                            maxMediaAttachments = compose?.media?.maxCount?.toLong() ?: 0,
                        ),
                    mediaAttachment =
                        UiInstanceMetadata.Configuration.MediaAttachment(
                            imageSizeLimit = compose?.media?.maxBytes ?: 0,
                            descriptionLimit = compose?.media?.altTextMaxLength?.toLong() ?: 0,
                            supportedMimeTypes =
                                compose
                                    ?.media
                                    ?.supportedMimeTypes
                                    .orEmpty()
                                    .toImmutableList(),
                        ),
                    poll =
                        UiInstanceMetadata.Configuration.Poll(
                            maxOptions = compose?.poll?.maxOptions?.toLong() ?: 0,
                            maxCharactersPerOption = 0,
                            minExpiration = 0,
                            maxExpiration = 0,
                        ),
                ),
        )
    }

    override fun createHandler(context: LoginContext): LoginMethodHandler {
        val candidates = platform.loginMethods.filter { it.interaction.toLoginMethodType() == context.methodType }
        val method = candidates.singleOrNull() ?: error("Plugin login method is ambiguous or unavailable: ${context.methodType}")
        return PluginLoginMethodHandlerV1(
            plugin = plugin,
            method = method,
            context = context,
            oauth = oauth,
            oauthCallbacks = oauthCallbacks,
            form = form,
            webCookie = webCookie,
            accountService = accountService,
            coroutineScope = coroutineScope,
        )
    }

    private fun TimelineManifestV1.toTimelineSpec(): TimelineSpec<TimelineSpec.AccountResourceData> =
        TimelineSpec(
            id = canonicalTimelineSpecId("timeline:$id"),
            title = title.toUiText(manifest.id),
            icon = icon.toUiIcon().asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            targetId = { "${it.accountKey}:$id" },
            loaderFactory =
                TimelineLoaderFactory { data, context ->
                    context.accountServiceFlow(AccountType.Specific(data.accountKey)).map { service ->
                        (service as? PluginNamedTimelineDataSourceV1)?.timeline(id, parameters) ?: notSupported()
                    }
                },
        )

    private fun canonicalTimelineSpecId(kind: String): String =
        "plugin:${manifest.id}:$platformId:schema${platform.timelineSchemaVersion}:$kind"

    private fun legacyTimelineSpecId(id: String): String =
        "plugin_${manifest.id.replace('.', '_')}_${platformId}${timelineSchemaSuffix()}_$id"

    private fun TimelineSpec<out TimelineSpec.Data>.withId(id: String): TimelineSpec<out TimelineSpec.Data> = copy(id = id)

    private fun timelineSchemaSuffix(): String =
        platform.timelineSchemaVersion
            .takeIf { it != 1 }
            ?.let { "_schema$it" }
            .orEmpty()

    private fun DeepLinkManifestV1.toPlatformDeepLink(accountKey: MicroBlogKey): PlatformDeepLink<PluginDeepLinkArgumentsV1> {
        val origin =
            if (origin ==
                dev.dimension.flare.feature.plugin.abi.PluginAbiV1.ACCOUNT_ORIGIN
            ) {
                "https://${accountKey.host}"
            } else {
                origin
            }
        val captureNames = path.filterIsInstance<DeepLinkPathSegmentV1.Capture>().map(DeepLinkPathSegmentV1.Capture::name)
        val pattern =
            origin +
                path.joinToString(separator = "/", prefix = "/") { segment ->
                    when (segment) {
                        is DeepLinkPathSegmentV1.Literal -> segment.value
                        is DeepLinkPathSegmentV1.Capture -> "{${segment.name}}"
                    }
                }
        return PlatformDeepLink(
            uriPattern = pattern,
            serializer = PluginDeepLinkArgumentsSerializerV1(captureNames),
            matcher = { arguments -> arguments.values.values.all { it.length in 1..MAX_DEEP_LINK_CAPTURE_LENGTH } },
            callback = { arguments ->
                val value = target.value?.render(arguments.values)
                when (target.type) {
                    DeepLinkTargetTypeV1.Profile -> {
                        DeeplinkRoute.Profile.UserNameWithHost(
                            accountType = AccountType.Specific(accountKey),
                            userName = requireNotNull(value),
                            host = accountKey.host,
                        )
                    }

                    DeepLinkTargetTypeV1.Post -> {
                        DeeplinkRoute.Status.Detail(
                            statusKey = MicroBlogKey(requireNotNull(value), accountKey.host),
                            accountType = AccountType.Specific(accountKey),
                        )
                    }

                    DeepLinkTargetTypeV1.Timeline -> {
                        val timelineId = requireNotNull(value)
                        val timeline = requireNotNull(platform.timelines.firstOrNull { it.id == timelineId })
                        val spec = requireNotNull(specs[timelineId])
                        val data = TimelineSpec.AccountResourceData(accountKey, timelineId)
                        val title = timeline.title.toUiText(manifest.id)
                        val icon = timeline.icon.toUiIcon().asType()
                        val candidate =
                            when (timeline.display) {
                                dev.dimension.flare.feature.plugin.wire.TimelineDisplayV1.List -> spec.candidate(data, title, icon)
                                dev.dimension.flare.feature.plugin.wire.TimelineDisplayV1.Grid -> spec.galleryCandidate(data, title, icon)
                            }
                        DeeplinkRoute.Timeline.Source(candidate.toUiTimelineTabItem().loaderKey)
                    }

                    DeepLinkTargetTypeV1.Browser -> {
                        DeeplinkRoute.OpenLinkDirectly(pattern.render(arguments.values))
                    }
                }
            },
        )
    }
}

internal class PluginLoginMethodHandlerV1(
    private val plugin: RunningPluginV1,
    private val method: LoginMethodManifestV1,
    private val context: LoginContext,
    private val oauth: PluginOAuthLoginCoordinatorV1,
    private val oauthCallbacks: PluginOAuthCallbackCoordinatorV1,
    private val form: PluginFormLoginCoordinatorV1,
    private val webCookie: PluginWebCookieLoginCoordinatorV1,
    private val accountService: AccountService,
    private val coroutineScope: CoroutineScope,
) : LoginMethodHandler {
    private val values = method.fields.associate { it.id to "" }.toMutableMap()
    private val mutableState = MutableStateFlow(state())
    private val mutableEffects = MutableSharedFlow<LoginEffect>(extraBufferCapacity = 1)
    private var cookieSession: PluginWebCookieSessionV1? = null
    private var oauthFlowId: String? = null

    override val state: StateFlow<LoginFlowState> = mutableState
    override val effects: Flow<LoginEffect> = mutableEffects

    override fun updateField(
        id: String,
        value: String,
    ) {
        if (id !in values || value.length > 16 * 1_024) return
        values[id] = value
        mutableState.value = state()
    }

    override suspend fun perform(actionId: String) {
        if (actionId != LOGIN_ACTION || mutableState.value.loading) return
        mutableState.value = state(loading = true)
        try {
            when (method.interaction) {
                LoginInteractionV1.OAuth -> {
                    oauthFlowId?.let { oauthCallbacks.unregister(it) }
                    oauthFlowId = null
                    when (
                        val result =
                            oauth.begin(
                                plugin = plugin,
                                methodId = method.id,
                                origin = context.host.toHttpsOrigin(),
                                locale = Locale.language,
                                expectedAccountId = context.reloginTarget?.accountKey?.id,
                            )
                    ) {
                        is PluginOAuthStartV1.ExternalBrowser -> {
                            oauthFlowId = result.flowId
                            oauthCallbacks.register(result.flowId) { success ->
                                if (oauthFlowId == result.flowId) {
                                    oauthFlowId = null
                                    complete(success)
                                }
                            }
                            mutableEffects.emit(LoginEffect.OpenUrl(result.url))
                            mutableState.value = state()
                        }

                        is PluginOAuthStartV1.Success -> {
                            complete(result.value)
                        }
                    }
                }

                LoginInteractionV1.Password,
                LoginInteractionV1.CredentialImport,
                LoginInteractionV1.Form,
                -> {
                    complete(
                        form.login(
                            plugin = plugin,
                            methodId = method.id,
                            origin = context.host.toHttpsOrigin(),
                            locale = Locale.language,
                            values = values,
                            expectedAccountId = context.reloginTarget?.accountKey?.id,
                        ),
                    )
                }

                LoginInteractionV1.WebCookie -> {
                    cookieSession?.close()
                    val session =
                        webCookie.begin(
                            plugin = plugin,
                            methodId = method.id,
                            origin = context.host.toHttpsOrigin(),
                            locale = Locale.language,
                            expectedAccountId = context.reloginTarget?.accountKey?.id,
                        )
                    cookieSession = session
                    mutableEffects.emit(
                        LoginEffect.OpenWebCookieLogin(
                            url = session.request.startUrl,
                            probes =
                                session.request.probes.map { probe ->
                                    LoginCookieProbe(
                                        url = probe.url,
                                        names = probe.cookies.map { it.name },
                                    )
                                },
                        ),
                    )
                    mutableState.value = state()
                }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            mutableState.value = state(error = error.message ?: "Plugin login failed")
        }
    }

    override suspend fun resume(value: String) {
        if (method.interaction != LoginInteractionV1.OAuth || mutableState.value.loading) return
        val expectedFlowId = oauthFlowId ?: return
        mutableState.value = state(loading = true)
        try {
            require(parsePluginOAuthCallback(value).flowId == expectedFlowId) {
                "OAuth callback belongs to another login flow"
            }
            if (!oauthCallbacks.handle(value, Locale.language)) {
                throw IllegalArgumentException("Unsupported OAuth callback")
            }
            if (oauthFlowId != null) mutableState.value = state()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            mutableState.value = state(error = error.message ?: "Plugin login failed")
        }
    }

    override fun canResume(value: String): Boolean =
        method.interaction == LoginInteractionV1.OAuth &&
            oauthFlowId?.let { expected ->
                runCatching { parsePluginOAuthCallback(value).flowId == expected }.getOrDefault(false)
            } == true

    override suspend fun checkCookies(snapshot: LoginCookieSnapshot): Boolean {
        val session = cookieSession ?: error("Cookie login has not started")
        val result =
            session.check(
                CookieSnapshotV1(
                    cookies =
                        snapshot.values.map { cookie ->
                            CookieValueV1(
                                sourceUrl = cookie.sourceUrl,
                                name = cookie.name,
                                value = cookie.value,
                            )
                        },
                ),
            )
        if (result is PluginCookieCheckResultV1.Success) complete(result.value)
        return result is PluginCookieCheckResultV1.Success
    }

    override fun clear() {
        values.keys.forEach { values[it] = "" }
        closeCookieSession()
        closeOAuthFlow()
        mutableState.value = state()
    }

    override fun close() {
        closeCookieSession()
        closeOAuthFlow()
    }

    private suspend fun complete(success: LoginSuccessV1) {
        val accountKey = MicroBlogKey(success.accountId, Url(success.origin).accountHost())
        context.requireReloginAccount(accountKey)
        accountService.addPluginAccount(plugin, success)
        mutableState.value = state()
        context.onSuccess()
    }

    private fun closeCookieSession(): Job? {
        val session = cookieSession ?: return null
        cookieSession = null
        return coroutineScope.launch { session.close() }
    }

    private fun closeOAuthFlow(): Job? {
        val flowId = oauthFlowId ?: return null
        oauthFlowId = null
        return coroutineScope.launch { oauthCallbacks.unregister(flowId) }
    }

    private fun state(
        loading: Boolean = false,
        error: String? = null,
    ): LoginFlowState =
        LoginFlowState(
            fields =
                method.fields.map { field ->
                    LoginField(
                        id = field.id,
                        type = field.type.toLoginFieldType(),
                        label = field.label.toUiText(plugin.installed.pluginId),
                        placeholder = field.placeholder?.toUiText(plugin.installed.pluginId),
                        value = values[field.id].orEmpty(),
                    )
                },
            actions =
                listOf(
                    LoginAction(
                        id = LOGIN_ACTION,
                        label = UiStrings.Next,
                        enabled = !loading && method.fields.filter { it.required }.all { !values[it.id].isNullOrBlank() },
                    ),
                ),
            loading = loading,
            error = error,
        )
}

private data class PluginDeepLinkArgumentsV1(
    val values: Map<String, String>,
)

private class PluginDeepLinkArgumentsSerializerV1(
    private val names: List<String>,
) : KSerializer<PluginDeepLinkArgumentsV1> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("PluginDeepLinkArgumentsV1") {
            names.forEach { element<String>(it) }
        }

    override fun deserialize(decoder: Decoder): PluginDeepLinkArgumentsV1 {
        val values = linkedMapOf<String, String>()
        decoder.decodeStructure(descriptor) {
            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                values[names[index]] = decodeStringElement(descriptor, index)
            }
        }
        require(values.keys.containsAll(names)) { "Deep Link is missing captured values" }
        return PluginDeepLinkArgumentsV1(values)
    }

    override fun serialize(
        encoder: Encoder,
        value: PluginDeepLinkArgumentsV1,
    ) {
        encoder.encodeStructure(descriptor) {
            names.forEachIndexed { index, name -> encodeStringElement(descriptor, index, value.values.getValue(name)) }
        }
    }
}

private fun DetectorResultV1.requireValid() {
    require(canonicalOrigin.length <= 8_192) { "Detector origin is too long" }
    PluginUrlPolicy.requireOrigin(canonicalOrigin)
    require(software == null || software.length <= 1_024) { "Detector software name is too long" }
    instance?.let {
        require(it.domain.isNotBlank() && it.domain.length <= 253) { "Invalid detected instance domain" }
        require(it.title.length <= 4_096 && (it.description?.length ?: 0) <= 64 * 1_024) { "Detected instance text is too long" }
        listOf(it.iconUrl, it.bannerUrl).filterNotNull().forEach { url ->
            PluginUrlPolicy.requireRequestUrl(url, setOf(PluginUrlPolicy.requireOrigin(canonicalOrigin)))
        }
    }
}

private fun LoginInteractionV1.toLoginMethodType(): LoginMethodType =
    when (this) {
        LoginInteractionV1.OAuth -> LoginMethodType.OAuth
        LoginInteractionV1.Password -> LoginMethodType.Password
        LoginInteractionV1.CredentialImport -> LoginMethodType.CredentialImport
        LoginInteractionV1.WebCookie -> LoginMethodType.WebCookie
        LoginInteractionV1.Form -> LoginMethodType.Form
    }

private fun LoginFieldTypeV1.toLoginFieldType(): LoginFieldType =
    when (this) {
        LoginFieldTypeV1.Text,
        LoginFieldTypeV1.Username,
        -> LoginFieldType.TextInput

        LoginFieldTypeV1.Password,
        LoginFieldTypeV1.Secret,
        -> LoginFieldType.PasswordInput

        LoginFieldTypeV1.Otp -> LoginFieldType.OtpInput
    }

private val PluginTextV1.fallback: String
    get() =
        when (this) {
            is PluginTextV1.Literal -> value
            is PluginTextV1.Localized -> fallback
        }

private fun String.toHttpsOrigin(): String =
    PluginUrlPolicy.requireOrigin(
        trim()
            .let { if (it.startsWith("https://", ignoreCase = true)) it else "https://$it" },
    )

private fun String.render(values: Map<String, String>): String =
    DEEP_LINK_CAPTURE.replace(this) { match -> values.getValue(match.groupValues[1]) }

private val DEEP_LINK_CAPTURE = Regex("\\{([A-Za-z][A-Za-z0-9_.-]{0,127})\\}")
private const val LOGIN_ACTION = "login"
private const val MAX_DEEP_LINK_CAPTURE_LENGTH = 4_096
